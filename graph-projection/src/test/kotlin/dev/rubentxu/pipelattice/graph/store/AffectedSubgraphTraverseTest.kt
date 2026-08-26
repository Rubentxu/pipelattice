package dev.rubentxu.pipelattice.graph.store

import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphChangeSet
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AffectedSubgraphTraverseTest {

    private fun store(vararg edges: Edge): InMemoryGraphProjectionStore {
        val store = InMemoryGraphProjectionStore()
        store.apply(GraphChangeSet(addedEdges = edges.toList(), removedEdges = emptyList()))
        return store
    }

    @Test
    fun `traverse returns direct neighbors outgoing`() {
        // A --SELECTS--> B --SELECTS--> C
        val a = GraphNode.PipelineProfile(ResourceRef("a"))
        val b = GraphNode.Project(ResourceRef("b"))
        val c = GraphNode.Project(ResourceRef("c"))
        val ab = Edge(a, b, EdgeKind.SELECTS)
        val bc = Edge(b, c, EdgeKind.SELECTS)

        val subject = AffectedSubgraph(store(ab, bc))

        val result = subject.traverse(a, Direction.OUTGOING, setOf(EdgeKind.SELECTS))

        assertEquals(setOf(b, c), result)
    }

    @Test
    fun `traverse returns direct neighbors incoming`() {
        // A --SELECTS--> B --SELECTS--> C
        val a = GraphNode.PipelineProfile(ResourceRef("a"))
        val b = GraphNode.Project(ResourceRef("b"))
        val c = GraphNode.Project(ResourceRef("c"))
        val ab = Edge(a, b, EdgeKind.SELECTS)
        val bc = Edge(b, c, EdgeKind.SELECTS)

        val subject = AffectedSubgraph(store(ab, bc))

        val result = subject.traverse(c, Direction.INCOMING, setOf(EdgeKind.SELECTS))

        assertEquals(setOf(b, a), result)
    }

    @Test
    fun `traverse respects maxDepth`() {
        // A --> B --> C --> D
        val a = GraphNode.PipelineProfile(ResourceRef("a"))
        val b = GraphNode.Project(ResourceRef("b"))
        val c = GraphNode.Project(ResourceRef("c"))
        val d = GraphNode.Project(ResourceRef("d"))
        val ab = Edge(a, b, EdgeKind.SELECTS)
        val bc = Edge(b, c, EdgeKind.SELECTS)
        val cd = Edge(c, d, EdgeKind.SELECTS)

        val subject = AffectedSubgraph(store(ab, bc, cd))

        val depth2 = subject.traverse(a, Direction.OUTGOING, EdgeKind.all(), maxDepth = 2)
        assertEquals(setOf(b, c), depth2)

        val depth1 = subject.traverse(a, Direction.OUTGOING, EdgeKind.all(), maxDepth = 1)
        assertEquals(setOf(b), depth1)
    }

    @Test
    fun `traverse filters by EdgeKind`() {
        val a = GraphNode.PipelineProfile(ResourceRef("a"))
        val b = GraphNode.Project(ResourceRef("b"))
        val abSelects = Edge(a, b, EdgeKind.SELECTS)
        val abImports = Edge(a, b, EdgeKind.IMPORTS)

        val subject = AffectedSubgraph(store(abSelects, abImports))

        val selectsOnly = subject.traverse(a, Direction.OUTGOING, setOf(EdgeKind.SELECTS))
        val importsOnly = subject.traverse(a, Direction.OUTGOING, setOf(EdgeKind.IMPORTS))

        assertEquals(setOf(b), selectsOnly)
        assertEquals(setOf(b), importsOnly)
    }
}
