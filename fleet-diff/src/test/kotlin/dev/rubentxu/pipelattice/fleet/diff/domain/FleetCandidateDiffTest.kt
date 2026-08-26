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

class FleetCandidateDiffTest {

    @Test
    fun `diff produces report with all seven sections`() {
        val repo = InMemorySnapshotRepository()
        val store = InMemoryGraphProjectionStore()

        val baseline = GraphSnapshot(
            nodes = emptySet(),
            edges = emptySet(),
            fingerprint = PlanFingerprint("0".repeat(64)),
        )
        val candidate = GraphSnapshot(
            nodes = emptySet(),
            edges = emptySet(),
            fingerprint = PlanFingerprint("1".repeat(64)),
        )

        repo.store("baseline", baseline)
        repo.store("candidate", candidate)

        val report = FleetCandidateDiff(repo, store).diff("baseline", "candidate")

        // All seven sections must be present
        assertTrue(report.affectedProjects.isEmpty())
        assertTrue(report.effectiveChanges.isEmpty())
        assertTrue(report.invalidPlans.isEmpty())
        assertTrue(report.newPolicyViolations.isEmpty())
        assertTrue(report.resolvedPolicyViolations.isEmpty())
        assertTrue(report.providerChanges.isEmpty())
        assertTrue(report.localOverrides.isEmpty())
        assertEquals("fleet-diff/v1", report.schema)
    }

    @Test
    fun `diff throws when baseline not found`() {
        val repo = InMemorySnapshotRepository()
        val store = InMemoryGraphProjectionStore()

        repo.store("candidate", GraphSnapshot(emptySet(), emptySet(), PlanFingerprint("1".repeat(64))))

        val exception = kotlin.test.assertFailsWith<IllegalArgumentException> {
            FleetCandidateDiff(repo, store).diff("baseline", "candidate")
        }
        assertTrue(exception.message!!.contains("Baseline snapshot not found"))
    }

    @Test
    fun `diff throws when candidate not found`() {
        val repo = InMemorySnapshotRepository()
        val store = InMemoryGraphProjectionStore()

        repo.store("baseline", GraphSnapshot(emptySet(), emptySet(), PlanFingerprint("0".repeat(64))))

        val exception = kotlin.test.assertFailsWith<IllegalArgumentException> {
            FleetCandidateDiff(repo, store).diff("baseline", "candidate")
        }
        assertTrue(exception.message!!.contains("Candidate snapshot not found"))
    }
}
