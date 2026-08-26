package dev.rubentxu.pipelattice.build.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies CommandResult.Failed preserves signal and durationMs metadata.
 */
class CommandResultFailedMetadataTest {

    @Test
    fun `Failed preserves exitCode stdout stderr`() {
        val failed = CommandResult.Failed(
            exitCode = 127,
            stdout = "command not found",
            stderr = "",
        )

        assertEquals(127, failed.exitCode)
        assertEquals("command not found", failed.stdout)
        assertEquals("", failed.stderr)
    }

    @Test
    fun `Failed defaults signal to null and durationMs to zero`() {
        val failed = CommandResult.Failed(
            exitCode = 1,
            stdout = "",
            stderr = "error",
        )

        assertEquals(null, failed.signal)
        assertEquals(0L, failed.durationMs)
    }

    @Test
    fun `Failed captures POSIX signal when terminated by signal`() {
        // SIGTERM = 15, duration 1234ms
        val failed = CommandResult.Failed(
            exitCode = 143, // 128 + 15 (standard convention for signal exit)
            stdout = "",
            stderr = "",
            signal = 15,
            durationMs = 1234L,
        )

        assertEquals(15, failed.signal)
        assertEquals(1234L, failed.durationMs)
        assertEquals(143, failed.exitCode)
    }

    @Test
    fun `Failed captures SIGKILL`() {
        val failed = CommandResult.Failed(
            exitCode = 137,
            stdout = "",
            stderr = "",
            signal = 9,
            durationMs = 500L,
        )

        assertEquals(9, failed.signal)
        assertEquals(500L, failed.durationMs)
    }

    @Test
    fun `Failed captures SIGSEGV`() {
        val failed = CommandResult.Failed(
            exitCode = 139,
            stdout = "",
            stderr = "Segmentation fault",
            signal = 11,
            durationMs = 200L,
        )

        assertEquals(11, failed.signal)
        assertEquals(200L, failed.durationMs)
    }
}
