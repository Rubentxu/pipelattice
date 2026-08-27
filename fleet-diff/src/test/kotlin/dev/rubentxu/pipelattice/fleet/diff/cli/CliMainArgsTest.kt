package dev.rubentxu.pipelattice.fleet.diff.cli

import dev.rubentxu.pipelattice.fleet.diff.domain.FleetCandidateDiff
import dev.rubentxu.pipelattice.fleet.diff.repository.InMemorySnapshotRepository
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import dev.rubentxu.pipelattice.graph.store.InMemoryGraphProjectionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CliMainArgsTest {

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
    fun `extract returns value after flag`() {
        val args = arrayOf("--baseline", "ref1", "--candidate", "ref2")

        val baseline = requiredArg(extractArg(args, "--baseline"))
        val candidate = requiredArg(extractArg(args, "--candidate"))

        assertEquals("ref1", baseline)
        assertEquals("ref2", candidate)
    }

    @Test
    fun `extract returns null for missing flag`() {
        val args = arrayOf("--baseline", "ref1")

        val output = extractArg(args, "--output")

        assertEquals(null, output)
    }

    @Test
    fun `required throws on null`() {
        val nullValue: String? = null

        val exception = assertFailsWith<IllegalArgumentException> {
            requiredArg(nullValue)
        }

        assertTrue(exception.message!!.contains("Required argument missing"))
    }

    @Test
    fun `diff uses baseline and candidate refs correctly`() {
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

        repo.store("baseline-ref", baseline)
        repo.store("candidate-ref", candidate)

        val store = InMemoryGraphProjectionStore()
        val report = FleetCandidateDiff(repo, store).diff("baseline-ref", "candidate-ref")

        assertTrue(report.affectedProjects.isEmpty())
    }

    @Test
    fun `--base alias returns same value as --baseline`() {
        // --base is first-wins alias for --baseline
        // When --base B is provided, --baseline is not used
        val argsWithBase = arrayOf("--base", "baseline", "--candidate", "candidate")
        assertEquals("baseline", Main.extract(argsWithBase, "--base"))
        assertEquals(null, Main.extract(argsWithBase, "--baseline"))

        // --base baseline --candidate candidate with identity path exits 0
        // (same as --baseline baseline --candidate candidate)
        val code = Main.run(argsWithBase)
        assertEquals(Main.EXIT_SUCCESS, code)
    }
}
