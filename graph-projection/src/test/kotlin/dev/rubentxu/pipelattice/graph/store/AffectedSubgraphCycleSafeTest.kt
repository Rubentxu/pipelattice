package dev.rubentxu.pipelattice.graph.store

import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphChangeSet
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies AffectedSubgraph traversal is cycle-safe (no infinite loops).
 */
class AffectedSubgraphCycleSafeTest {

    private fun store(vararg edges: Edge): InMemoryGraphProjectionStore {
        val s = InMemoryGraphProjectionStore()
        s.apply(GraphChangeSet(addedEdges = edges.toList(), removedEdges = emptyList()))
        return s
    }

    @Test
    fun `traverse does not infinite loop on cycle`() {
        // A --> B
        // ^     |
        // +-----+  (cycle: A -> B -> A)
        val a = GraphNode.PipelineProfile(ResourceRef("a"))
        val b = GraphNode.Project(ResourceRef("b"))
        val ab = Edge(a, b, EdgeKind.SELECTS)
        val ba = Edge(b, a, EdgeKind.SELECTS)

        val subject = AffectedSubgraph(store(ab, ba))

        // Should complete without hanging and return both nodes
        val result = subject.traverse(a, Direction.OUTGOING, EdgeKind.all())

        assertEquals(setOf(b), result)
    }

    @Test
    fun `traverse handles self-loop`() {
        // A --> A (self-loop)
        val a = GraphNode.PipelineProfile(ResourceRef("a"))
        val aa = Edge(a, a, EdgeKind.SELECTS)

        val subject = AffectedSubgraph(store(aa))

        val result = subject.traverse(a, Direction.OUTGOING, EdgeKind.all())

        // Self-loop: only the source node is visited (no new nodes)
        assertEquals(emptySet(), result)
    }
}
