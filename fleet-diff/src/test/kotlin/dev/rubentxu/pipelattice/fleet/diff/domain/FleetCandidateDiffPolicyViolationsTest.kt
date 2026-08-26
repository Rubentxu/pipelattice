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

class FleetCandidateDiffPolicyViolationsTest {

    @Test
    fun `policy violations are empty in a-min`() {
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

        // A-min: policy violations are not yet implemented
        assertTrue(report.newPolicyViolations.isEmpty())
        assertTrue(report.resolvedPolicyViolations.isEmpty())
    }
}
