package dev.rubentxu.pipelattice.graph.store

import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphChangeSet
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies Direction enum (OUTGOING, INCOMING, BOTH) works correctly.
 */
class AffectedSubgraphDirectionTest {

    private fun store(vararg edges: Edge): InMemoryGraphProjectionStore {
        val s = InMemoryGraphProjectionStore()
        s.apply(GraphChangeSet(addedEdges = edges.toList(), removedEdges = emptyList()))
        return s
    }

    @Test
    fun `traverse OUTGOING follows source to target`() {
        // A --SELECTS--> B
        val a = GraphNode.PipelineProfile(ResourceRef("a"))
        val b = GraphNode.Project(ResourceRef("b"))
        val ab = Edge(a, b, EdgeKind.SELECTS)

        val subject = AffectedSubgraph(store(ab))

        val result = subject.traverse(a, Direction.OUTGOING, EdgeKind.all())

        assertEquals(setOf(b), result)
    }

    @Test
    fun `traverse INCOMING follows target to source`() {
        // A --SELECTS--> B
        val a = GraphNode.PipelineProfile(ResourceRef("a"))
        val b = GraphNode.Project(ResourceRef("b"))
        val ab = Edge(a, b, EdgeKind.SELECTS)

        val subject = AffectedSubgraph(store(ab))

        val result = subject.traverse(b, Direction.INCOMING, EdgeKind.all())

        assertEquals(setOf(a), result)
    }

    @Test
    fun `traverse BOTH follows both directions`() {
        // A --SELECTS--> B --EXTENDS--> C
        val a = GraphNode.PipelineProfile(ResourceRef("a"))
        val b = GraphNode.Project(ResourceRef("b"))
        val c = GraphNode.Project(ResourceRef("c"))
        val ab = Edge(a, b, EdgeKind.SELECTS)
        val bc = Edge(b, c, EdgeKind.EXTENDS)

        val subject = AffectedSubgraph(store(ab, bc))

        // From B going BOTH should reach A and C
        val result = subject.traverse(b, Direction.BOTH, EdgeKind.all())

        assertEquals(setOf(a, c), result)
    }
}
