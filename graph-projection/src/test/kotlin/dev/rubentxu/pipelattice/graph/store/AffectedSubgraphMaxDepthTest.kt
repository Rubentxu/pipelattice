package dev.rubentxu.pipelattice.graph.store

import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphChangeSet
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies maxDepth bound is respected in BFS traversal.
 */
class AffectedSubgraphMaxDepthTest {

    private fun store(vararg edges: Edge): InMemoryGraphProjectionStore {
        val s = InMemoryGraphProjectionStore()
        s.apply(GraphChangeSet(addedEdges = edges.toList(), removedEdges = emptyList()))
        return s
    }

    @Test
    fun `traverse depth 0 returns empty set`() {
        val a = GraphNode.PipelineProfile(ResourceRef("a"))
        val b = GraphNode.Project(ResourceRef("b"))
        val ab = Edge(a, b, EdgeKind.SELECTS)

        val subject = AffectedSubgraph(store(ab))

        val result = subject.traverse(a, Direction.OUTGOING, EdgeKind.all(), maxDepth = 0)

        assertEquals(emptySet(), result)
    }

    @Test
    fun `traverse depth 1 returns direct neighbors only`() {
        // A --SELECTS--> B --SELECTS--> C
        val a = GraphNode.PipelineProfile(ResourceRef("a"))
        val b = GraphNode.Project(ResourceRef("b"))
        val c = GraphNode.Project(ResourceRef("c"))
        val ab = Edge(a, b, EdgeKind.SELECTS)
        val bc = Edge(b, c, EdgeKind.SELECTS)

        val subject = AffectedSubgraph(store(ab, bc))

        val result = subject.traverse(a, Direction.OUTGOING, EdgeKind.all(), maxDepth = 1)

        assertEquals(setOf(b), result)
    }

    @Test
    fun `traverse depth 64 is the default upper bound`() {
        // A --> B --> C --> ... (chain of 70 nodes)
        val nodes = (0..69).map { GraphNode.Project(ResourceRef("node_$it")) }
        val edges = nodes.zipWithNext().map { (from, to) ->
            Edge(from, to, EdgeKind.SELECTS)
        }

        val subject = AffectedSubgraph(store(*edges.toTypedArray()))

        // With default maxDepth=64, should not reach node_69 (depth 69 > 64)
        val result = subject.traverse(nodes[0], Direction.OUTGOING, EdgeKind.all())

        // nodes[1] through nodes[64] = 64 nodes reachable at depths 1-64
        val expectedCount = 64
        assertEquals(expectedCount, result.size)
    }
}
