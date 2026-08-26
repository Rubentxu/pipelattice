package dev.rubentxu.pipelattice.graph.store

import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import dev.rubentxu.pipelattice.graph.ports.GraphProjectionStore

/**
 * Provides graph traversal utilities over a [GraphProjectionStore].
 *
 * Supports BFS traversal with cycle safety and max-depth bound.
 */
public class AffectedSubgraph(
    private val store: GraphProjectionStore,
) {
    private val adjacencyIndex = AdjacencyIndex(store)

    /**
     * Returns all nodes reachable from [source] following edges matching [kinds]
     * in the given [direction], bounded by [maxDepth].
     *
     * Uses BFS internally with a visited set to prevent infinite loops.
     *
     * @param source The starting node for traversal.
     * @param direction Which direction to traverse (OUTGOING, INCOMING, BOTH).
     * @param kinds Set of [EdgeKind] to follow during traversal.
     * @param maxDepth Maximum depth to traverse (default 64). Depth 0 returns empty set.
     * @return Set of [GraphNode]s reachable within [maxDepth] steps.
     */
    public fun traverse(
        source: GraphNode,
        direction: Direction = Direction.OUTGOING,
        kinds: Set<EdgeKind> = EdgeKind.all(),
        maxDepth: Int = 64,
    ): Set<GraphNode> {
        if (maxDepth <= 0) return emptySet()

        val visited = mutableSetOf<GraphNode>()
        val queue = ArrayDeque<Pair<GraphNode, Int>>()
        queue.add(source to 0)
        visited.add(source)

        while (queue.isNotEmpty()) {
            val (current, depth) = queue.removeFirst()
            if (depth >= maxDepth) continue

            val neighbors = adjacencyIndex.neighbors(current, direction, kinds)
            for (neighbor in neighbors) {
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(neighbor to depth + 1)
                }
            }
        }

        visited.remove(source)
        return visited
    }

    /**
     * Convenience method for blast-radius analysis.
     *
     * Uses SELECTS, IMPORTS, and REQUIRES as the default edge kinds,
     * which covers the most common change-propagation scenarios.
     *
     * @param source The starting node for blast-radius computation.
     * @param kinds Set of [EdgeKind] to consider during traversal.
     * @return Set of [GraphNode]s in the blast radius.
     */
    public fun blastRadius(
        source: GraphNode,
        kinds: Set<EdgeKind> = setOf(EdgeKind.SELECTS, EdgeKind.IMPORTS, EdgeKind.REQUIRES),
    ): Set<GraphNode> = traverse(source, Direction.OUTGOING, kinds)
}
