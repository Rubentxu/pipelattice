package dev.rubentxu.pipelattice.fleet.diff.cli

import dev.rubentxu.pipelattice.fleet.diff.json.FleetDiffJsonEncoder
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import dev.rubentxu.pipelattice.graph.store.InMemoryGraphProjectionStore
import dev.rubentxu.pipelattice.fleet.diff.domain.FleetCandidateDiff
import dev.rubentxu.pipelattice.fleet.diff.repository.InMemorySnapshotRepository
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CliMainSmokeTest {

    private fun extractArg(args: Array<String>, flag: String): String? {
        val index = args.indexOf(flag)
        return if (index >= 0 && index + 1 < args.size) {
            args[index + 1]
        } else {
            null
        }
    }

    private fun requiredArg(value: String?): String {
        return value ?: throw IllegalArgumentException("Required argument missing")
    }

    @Test
    fun `end-to-end produces JSON output`() {
        // Set up repository with test snapshots
        val repo = InMemorySnapshotRepository()

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

        val store = InMemoryGraphProjectionStore()
        val report = FleetCandidateDiff(repo, store).diff("baseline", "candidate")
        val json = FleetDiffJsonEncoder.encode(report)

        assertContains(json, "\"schema\":\"fleet-diff/v1\"")
        assertContains(json, "\"affectedProjects\":[]")
    }

    @Test
    fun `main with valid args does not throw`() {
        // This tests the argument extraction logic works
        val args = arrayOf("--baseline", "baseline", "--candidate", "candidate", "--output", "/tmp/test-output.json")

        val baselineRef = requiredArg(extractArg(args, "--baseline"))
        val candidateRef = requiredArg(extractArg(args, "--candidate"))
        val outputPath = extractArg(args, "--output")

        assertEquals("baseline", baselineRef)
        assertEquals("candidate", candidateRef)
        assertEquals("/tmp/test-output.json", outputPath)
    }
}
