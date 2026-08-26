package dev.rubentxu.pipelattice.graph.store

import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import dev.rubentxu.pipelattice.graph.ports.GraphProjectionStore

/**
 * Lazy adjacency index for O(1) node+kind+direction lookups.
 *
 * Built lazily on first [traverse] or [blastRadius] call.
 * Invalidated automatically when the parent store's version increments.
 *
 * The index maps: node -> (direction -> (kind -> set of neighbor nodes))
 */
internal class AdjacencyIndex(
    private val store: GraphProjectionStore,
) {
    private var snapshotVersion: Long = -1L
    private var cachedEdges: List<Edge> = emptyList()

    private val outgoing: MutableMap<GraphNode, MutableMap<EdgeKind, MutableSet<GraphNode>>> = mutableMapOf()
    private val incoming: MutableMap<GraphNode, MutableMap<EdgeKind, MutableSet<GraphNode>>> = mutableMapOf()

    private fun isStoreModified(): Boolean {
        val storeVersion = (store as? InMemoryGraphProjectionStore)?.applyVersion ?: 0L
        return storeVersion != snapshotVersion
    }

    private fun build() {
        if (!isStoreModified()) return

        cachedEdges = store.snapshot().edges.toList()
        snapshotVersion = (store as? InMemoryGraphProjectionStore)?.applyVersion ?: -1L
        outgoing.clear()
        incoming.clear()

        for (edge in cachedEdges) {
            outgoing
                .getOrPut(edge.source) { mutableMapOf() }
                .getOrPut(edge.kind) { mutableSetOf() }
                .add(edge.target)

            incoming
                .getOrPut(edge.target) { mutableMapOf() }
                .getOrPut(edge.kind) { mutableSetOf() }
                .add(edge.source)
        }
    }

    fun neighbors(
        node: GraphNode,
        direction: Direction,
        kinds: Set<EdgeKind>,
    ): Set<GraphNode> {
        build()
        val result = mutableSetOf<GraphNode>()

        if (direction == Direction.OUTGOING || direction == Direction.BOTH) {
            for (kind in kinds) {
                outgoing[node]?.get(kind)?.let { result.addAll(it) }
            }
        }
        if (direction == Direction.INCOMING || direction == Direction.BOTH) {
            for (kind in kinds) {
                incoming[node]?.get(kind)?.let { result.addAll(it) }
            }
        }
        return result
    }
}
