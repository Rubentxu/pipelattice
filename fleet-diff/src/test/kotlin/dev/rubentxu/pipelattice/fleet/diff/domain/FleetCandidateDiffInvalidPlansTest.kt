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
import kotlin.test.assertTrue

class FleetCandidateDiffInvalidPlansTest {

    @Test
    fun `removed project with plan marks plan invalid`() {
        val repo = InMemorySnapshotRepository()
        val store = InMemoryGraphProjectionStore()

        val project = GraphNode.Project(ResourceRef("projects/example"))
        val profile = GraphNode.PipelineProfile(ResourceRef("profiles/java"))
        val plan = GraphNode.ResolvedPipelinePlan(ResourceRef("projects/example"), "digest123")

        // Edge from profile to project in baseline
        val baselineEdge = Edge(profile, project, EdgeKind.SELECTS)

        val baseline = GraphSnapshot(
            nodes = setOf(project, profile, plan),
            edges = setOf(baselineEdge),
            fingerprint = PlanFingerprint("0".repeat(64)),
        )

        // No edges in candidate (project was removed from selection)
        val candidate = GraphSnapshot(
            nodes = setOf(project, profile, plan),
            edges = emptySet(),
            fingerprint = PlanFingerprint("1".repeat(64)),
        )

        repo.store("baseline", baseline)
        repo.store("candidate", candidate)

        val report = FleetCandidateDiff(repo, store).diff("baseline", "candidate")

        // Plan for the removed project should be in invalidPlans
        assertTrue(report.invalidPlans.any {
            it.projectId == ResourceRef("projects/example")
        })
    }
}
