package dev.rubentxu.pipelattice.graph.domain

import dev.rubentxu.pipelattice.foundation.ResourceRef
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GraphNodeTest {

    @Test
    fun `GraphNode sealed interface has 5 data class variants`() {
        val nodes = listOf(
            GraphNode.Project(ResourceRef("projects/example")),
            GraphNode.Component(ResourceRef("components/foo"), ResourceRef("projects/owner")),
            GraphNode.PipelineProfile(ResourceRef("profiles/java-maven")),
            GraphNode.ConfigSource(Path.of("/etc/config.yaml"), "abc123"),
            GraphNode.ResolvedPipelinePlan(ResourceRef("projects/example"), "digest-xyz"),
        )
        assertEquals(5, nodes.size)
        nodes.forEach { assertIs<GraphNode>(it) }
    }

    @Test
    fun `GraphNode variants have structural equality`() {
        val p1 = GraphNode.Project(ResourceRef("projects/example"))
        val p2 = GraphNode.Project(ResourceRef("projects/example"))
        assertEquals(p1, p2)

        val c1 = GraphNode.Component(ResourceRef("components/foo"), ResourceRef("projects/owner"))
        val c2 = GraphNode.Component(ResourceRef("components/foo"), ResourceRef("projects/owner"))
        assertEquals(c1, c2)

        val pp1 = GraphNode.PipelineProfile(ResourceRef("profiles/java-maven"))
        val pp2 = GraphNode.PipelineProfile(ResourceRef("profiles/java-maven"))
        assertEquals(pp1, pp2)

        val cs1 = GraphNode.ConfigSource(Path.of("/etc/config.yaml"), "abc123")
        val cs2 = GraphNode.ConfigSource(Path.of("/etc/config.yaml"), "abc123")
        assertEquals(cs1, cs2)

        val rp1 = GraphNode.ResolvedPipelinePlan(ResourceRef("projects/example"), "digest-xyz")
        val rp2 = GraphNode.ResolvedPipelinePlan(ResourceRef("projects/example"), "digest-xyz")
        assertEquals(rp1, rp2)
    }

    @Test
    fun `GraphNode variants are exhaustive in when expression`() {
        val node: GraphNode = GraphNode.Project(ResourceRef("projects/test"))
        val result = when (node) {
            is GraphNode.Project -> "project"
            is GraphNode.Component -> "component"
            is GraphNode.PipelineProfile -> "profile"
            is GraphNode.ConfigSource -> "config"
            is GraphNode.ResolvedPipelinePlan -> "plan"
        }
        assertEquals("project", result)
    }

    @Test
    fun `GraphNode Project and PipelineProfile can coexist in a Set`() {
        val project = GraphNode.Project(ResourceRef("projects/example"))
        val profile = GraphNode.PipelineProfile(ResourceRef("profiles/java-maven"))
        val set = setOf(project, profile)
        assertEquals(2, set.size)
        assertTrue(project in set)
        assertTrue(profile in set)
    }
}
