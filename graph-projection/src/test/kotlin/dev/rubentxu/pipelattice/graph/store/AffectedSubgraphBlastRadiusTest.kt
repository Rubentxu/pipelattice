package dev.rubentxu.pipelattice.graph.store

import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphChangeSet
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies blastRadius convenience method works correctly.
 */
class AffectedSubgraphBlastRadiusTest {

    private fun store(vararg edges: Edge): InMemoryGraphProjectionStore {
        val s = InMemoryGraphProjectionStore()
        s.apply(GraphChangeSet(addedEdges = edges.toList(), removedEdges = emptyList()))
        return s
    }

    @Test
    fun `blastRadius uses SELECTS IMPORTS REQUIRES by default`() {
        val profile = GraphNode.PipelineProfile(ResourceRef("profile"))
        val project = GraphNode.Project(ResourceRef("project"))
        val resource = GraphNode.Project(ResourceRef("resource"))

        // profile --SELECTS--> project --IMPORTS--> resource
        val ps = Edge(profile, project, EdgeKind.SELECTS)
        val pi = Edge(project, resource, EdgeKind.IMPORTS)

        val subject = AffectedSubgraph(store(ps, pi))

        val blast = subject.blastRadius(profile)

        // SELECTS+IMPORTS by default should reach both project and resource
        assertEquals(setOf(project, resource), blast)
    }

    @Test
    fun `blastRadius respects custom kinds`() {
        val profile = GraphNode.PipelineProfile(ResourceRef("profile"))
        val project = GraphNode.Project(ResourceRef("project"))
        val resource = GraphNode.Project(ResourceRef("resource"))

        val ps = Edge(profile, project, EdgeKind.SELECTS)
        val pi = Edge(project, resource, EdgeKind.IMPORTS)

        val subject = AffectedSubgraph(store(ps, pi))

        // Only SELECTS should NOT reach resource
        val blast = subject.blastRadius(profile, setOf(EdgeKind.SELECTS))

        assertEquals(setOf(project), blast)
    }
}
