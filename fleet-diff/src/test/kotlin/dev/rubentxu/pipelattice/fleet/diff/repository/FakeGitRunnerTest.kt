package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.build.domain.Command
import dev.rubentxu.pipelattice.build.domain.CommandResult
import dev.rubentxu.pipelattice.build.domain.Executable
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for [FakeGitRunner] FIFO queue behavior and invocation history.
 *
 * These tests are hermetic (no git binary required) and run in the default `:fleet-diff:test` task.
 *
 * Covers spec scenario: *FakeGitRunner scriptable queue behaviour + invocation history*
 * (lives implicitly via scenario-testing pattern; the test file IS the executor contract).
 */
class FakeGitRunnerTest {

    private val testCommand = Command(
        executable = Executable("git"),
        arguments = emptyList(),
        workingDirectory = Path.of("/tmp"),
        environment = emptyMap(),
    )

    @Test
    fun `enqueued results returned in FIFO order`() {
        val runner = FakeGitRunner()
        runner.enqueue(CommandResult.Success("first", ""))
        runner.enqueue(CommandResult.Success("second", ""))

        val result1 = runBlocking { runner.run(testCommand) }
        val result2 = runBlocking { runner.run(testCommand) }

        assertTrue(result1 is CommandResult.Success)
        assertEquals("first", result1.stdout)
        assertTrue(result2 is CommandResult.Success)
        assertEquals("second", result2.stdout)
    }

    @Test
    fun `queue exhaustion throws`() {
        val runner = FakeGitRunner()
        runner.enqueue(CommandResult.Success("only", ""))

        runBlocking { runner.run(testCommand) } // succeeds

        val exception = assertFailsWith<IllegalStateException> {
            runBlocking { runner.run(testCommand) } // queue is empty
        }
        assertTrue(exception.message!!.contains("no scripted response was enqueued"))
    }

    @Test
    fun `invocations snapshot matches received commands`() {
        val runner = FakeGitRunner()
        runner.enqueue(CommandResult.Success("first", ""))
        runner.enqueue(CommandResult.Success("second", ""))

        runBlocking { runner.run(testCommand) }
        runBlocking { runner.run(testCommand) }

        val invocations = runner.invocations()
        assertEquals(2, invocations.size)
        assertEquals(testCommand, invocations[0])
        assertEquals(testCommand, invocations[1])
    }

    @Test
    fun `reset clears queue and invocations`() {
        val runner = FakeGitRunner()
        runner.enqueue(CommandResult.Success("first", ""))

        runBlocking { runner.run(testCommand) }
        assertEquals(1, runner.invocationCount())

        runner.reset()

        assertEquals(0, runner.invocationCount())
        assertTrue(runner.invocations().isEmpty())

        // After reset, running should fail because queue is empty
        assertFailsWith<IllegalStateException> {
            runBlocking { runner.run(testCommand) }
        }
    }
}
