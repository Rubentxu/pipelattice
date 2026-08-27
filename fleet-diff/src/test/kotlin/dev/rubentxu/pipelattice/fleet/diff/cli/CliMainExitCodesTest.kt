package dev.rubentxu.pipelattice.fleet.diff.cli

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies the CLI exit code contract defined in
 * pipelattice-spec/docs/17_CLI_CONTROL_PLANE.md §4 + BSD sysexits.h.
 *
 * The test calls [Main.run] directly (which returns the exit code instead of
 * calling [System.exit]) so the suite does not spawn a subprocess.
 */
class CliMainExitCodesTest {

    @Test
    fun `run returns EXIT_SUCCESS 0 when both refs are present in repo`() {
        val code = Main.run(
            arrayOf("--baseline", "baseline", "--candidate", "candidate"),
        )
        assertEquals(Main.EXIT_SUCCESS, code)
    }

    @Test
    fun `run returns EXIT_USAGE 64 when --baseline is missing`() {
        val code = Main.run(arrayOf("--candidate", "candidate"))
        assertEquals(Main.EXIT_USAGE, code)
    }

    @Test
    fun `run returns EXIT_USAGE 64 when --candidate is missing`() {
        val code = Main.run(arrayOf("--baseline", "baseline"))
        assertEquals(Main.EXIT_USAGE, code)
    }

    @Test
    fun `run returns EXIT_USAGE 64 when both required flags are missing`() {
        val code = Main.run(arrayOf())
        assertEquals(Main.EXIT_USAGE, code)
    }

    @Test
    fun `run returns EXIT_VALIDATION 2 when baseline ref is unknown to repo`() {
        val code = Main.run(
            arrayOf("--baseline", "unknown-ref", "--candidate", "candidate"),
        )
        assertEquals(Main.EXIT_VALIDATION, code)
    }

    @Test
    fun `run returns EXIT_VALIDATION 2 when candidate ref is unknown to repo`() {
        val code = Main.run(
            arrayOf("--baseline", "baseline", "--candidate", "unknown-ref"),
        )
        assertEquals(Main.EXIT_VALIDATION, code)
    }
}
