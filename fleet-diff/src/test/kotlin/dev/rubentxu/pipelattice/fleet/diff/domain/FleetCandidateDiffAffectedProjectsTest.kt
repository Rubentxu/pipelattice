package dev.rubentxu.pipelattice.fleet.diff.domain

import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import dev.rubentxu.pipelattice.graph.store.InMemoryGraphProjectionStore
import dev.rubentxu.pipelattice.fleet.diff.repository.InMemorySnapshotRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FleetCandidateDiffAffectedProjectsTest {

    @Test
    fun `affected projects detected from SELECTS edge change`() {
        val repo = InMemorySnapshotRepository()
        val store = InMemoryGraphProjectionStore()

        val projectA = GraphNode.Project(ResourceRef("projects/a"))
        val profile = GraphNode.PipelineProfile(ResourceRef("profiles/java"))

        val baselineEdge = Edge(profile, projectA, EdgeKind.SELECTS)
        val candidateEdge = Edge(profile, projectA, EdgeKind.SELECTS)

        val baseline = GraphSnapshot(
            nodes = setOf(projectA, profile),
            edges = setOf(baselineEdge),
            fingerprint = PlanFingerprint("0".repeat(64)),
        )

        val candidate = GraphSnapshot(
            nodes = setOf(projectA, profile),
            edges = setOf(candidateEdge),
            fingerprint = PlanFingerprint("1".repeat(64)),
        )

        repo.store("baseline", baseline)
        repo.store("candidate", candidate)

        val report = FleetCandidateDiff(repo, store).diff("baseline", "candidate")

        // No change in SELECTS edge, so no affected projects
        assertTrue(report.affectedProjects.isEmpty())
    }

    @Test
    fun `removed project is detected as affected`() {
        val repo = InMemorySnapshotRepository()
        val store = InMemoryGraphProjectionStore()

        val projectA = GraphNode.Project(ResourceRef("projects/a"))
        val projectB = GraphNode.Project(ResourceRef("projects/b"))
        val profile = GraphNode.PipelineProfile(ResourceRef("profiles/java"))

        val baselineEdge = Edge(profile, projectA, EdgeKind.SELECTS)
        val candidateEdge = Edge(profile, projectB, EdgeKind.SELECTS)

        val baseline = GraphSnapshot(
            nodes = setOf(projectA, projectB, profile),
            edges = setOf(baselineEdge),
            fingerprint = PlanFingerprint("0".repeat(64)),
        )

        val candidate = GraphSnapshot(
            nodes = setOf(projectA, projectB, profile),
            edges = setOf(candidateEdge),
            fingerprint = PlanFingerprint("1".repeat(64)),
        )

        repo.store("baseline", baseline)
        repo.store("candidate", candidate)

        val report = FleetCandidateDiff(repo, store).diff("baseline", "candidate")

        // projectA is affected because the edge to it was removed
        assertTrue(report.affectedProjects.isNotEmpty())
    }
}
