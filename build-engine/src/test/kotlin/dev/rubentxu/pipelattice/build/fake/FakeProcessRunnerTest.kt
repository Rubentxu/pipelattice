package dev.rubentxu.pipelattice.build.fake

import dev.rubentxu.pipelattice.build.domain.Argument
import dev.rubentxu.pipelattice.build.domain.Command
import dev.rubentxu.pipelattice.build.domain.CommandResult
import dev.rubentxu.pipelattice.build.domain.Executable
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FakeProcessRunnerTest {

    private fun dummyCommand() = Command(
        executable = Executable("mvn"),
        arguments = listOf(Argument("verify")),
        workingDirectory = Path.of("/p"),
        environment = emptyMap(),
    )

    @Test
    fun `enqueue adds scripted response to queue`() {
        val runner = FakeProcessRunner()
        runner.enqueue(CommandResult.Success("out", ""))
        assertEquals(0, runner.invocationCount())
        assertTrue(runner.invocations().isEmpty())
    }

    @Test
    fun `run returns scripted response in FIFO order`() {
        val runner = FakeProcessRunner()
        val cmd = dummyCommand()
        runner.enqueue(CommandResult.Success("out", "err"))
        runner.enqueue(CommandResult.Failed(1, "stdout", "stderr"))

        val result1 = runBlocking { runner.run(cmd) }
        val result2 = runBlocking { runner.run(cmd) }

        assertIs<CommandResult.Success>(result1)
        assertEquals("out", result1.stdout)
        assertEquals("err", result1.stderr)

        assertIs<CommandResult.Failed>(result2)
        assertEquals(1, result2.exitCode)
        assertEquals("stdout", result2.stdout)
        assertEquals("stderr", result2.stderr)
    }

    @Test
    fun `empty queue raises IllegalStateException`() {
        val runner = FakeProcessRunner()
        val cmd = dummyCommand()

        val error = assertFailsWith<IllegalStateException> { runBlocking { runner.run(cmd) } }
        assertTrue(error.message!!.contains("no scripted response"))
    }

    @Test
    fun `empty queue still records invocation`() {
        val runner = FakeProcessRunner()
        val cmd = dummyCommand()

        assertFailsWith<IllegalStateException> { runBlocking { runner.run(cmd) } }

        assertEquals(1, runner.invocationCount())
        assertEquals(listOf(cmd), runner.invocations())
    }

    @Test
    fun `invocations records all calls in order`() {
        val runner = FakeProcessRunner()
        val cmd1 = dummyCommand()
        val cmd2 = Command(
            executable = Executable("gradle"),
            arguments = listOf(Argument("build")),
            workingDirectory = Path.of("/p2"),
            environment = emptyMap(),
        )
        runner.enqueue(CommandResult.Success("", ""))
        runner.enqueue(CommandResult.Success("", ""))

        runBlocking { runner.run(cmd1) }
        runBlocking { runner.run(cmd2) }

        assertEquals(listOf(cmd1, cmd2), runner.invocations())
    }

    @Test
    fun `reset clears queue and invocations`() {
        val runner = FakeProcessRunner()
        val cmd = dummyCommand()
        runner.enqueue(CommandResult.Success("", ""))
        runBlocking { runner.run(cmd) }

        runner.reset()

        assertEquals(0, runner.invocationCount())
        assertTrue(runner.invocations().isEmpty())
        assertFailsWith<IllegalStateException> { runBlocking { runner.run(cmd) } }
    }

    @Test
    fun `invocationCount increments on each run call`() {
        val runner = FakeProcessRunner()
        val cmd = dummyCommand()
        runner.enqueue(CommandResult.Success("", ""))
        runner.enqueue(CommandResult.Success("", ""))

        runBlocking { runner.run(cmd) }
        assertEquals(1, runner.invocations().size)

        runBlocking { runner.run(cmd) }
        assertEquals(2, runner.invocations().size)
    }
}
