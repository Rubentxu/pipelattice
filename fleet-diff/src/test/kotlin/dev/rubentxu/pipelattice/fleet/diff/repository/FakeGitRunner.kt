package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.build.domain.Command
import dev.rubentxu.pipelattice.build.domain.CommandResult
import dev.rubentxu.pipelattice.build.ports.ProcessRunner

/**
 * A fake [ProcessRunner] for unit-testing [GitSnapshotRepository] without a real git binary.
 *
 * This fixture is testkit-local to `:fleet-diff` and mirrors the shape of
 * [dev.rubentxu.pipelattice.build.fake.FakeProcessRunner] from `:build-engine`
 * (the canonical process-runner test fixture, per FARCH-014 + FARCH-016 process-isolation precedent).
 *
 * ## FIFO queue semantics
 * Each call to [run] records the [Command] in [invocations] and returns the next
 * scripted [CommandResult] from the queue. If the queue is exhausted, the call throws
 * [IllegalStateException].
 *
 * ## Thread safety
 * A-min assumes a single-threaded caller. The fixture is **not** safe for concurrent use.
 *
 * ## Usage
 * ```kotlin
 * val runner = FakeGitRunner()
 * runner.enqueue(CommandResult.Success("abc123def456...\n", ""))
 * runner.enqueue(CommandResult.Failed(128, "", "fatal: bad revision 'nonexistent'"))
 *
 * val result1 = runner.run(someCommand)  // returns Success("abc123def456...", "")
 * val result2 = runner.run(someCommand)  // returns Failed(128, "", "fatal: bad revision 'nonexistent'")
 *
 * assertEquals(2, runner.invocationCount())
 * assertEquals(listOf(someCommand, someCommand), runner.invocations())
 * ```
 *
 * @see GitSnapshotRepository
 * @see FakeProcessRunner
 */
public class FakeGitRunner : ProcessRunner {

    private val scripts: MutableList<CommandResult> = mutableListOf()

    private val _invocations: MutableList<Command> = mutableListOf()

    /**
     * Enqueues a [CommandResult] response to be returned by the next [run] call.
     *
     * Responses are returned in FIFO order.
     *
     * @param result The scripted result to return on the next invocation.
     */
    public fun enqueue(result: CommandResult) {
        scripts.add(result)
    }

    /**
     * Returns a snapshot of all [Command]s that have been passed to [run].
     *
     * The commands are recorded in the order they were received,
     * even if the queue was empty and an exception was thrown.
     */
    public fun invocations(): List<Command> = _invocations.toList()

    /**
     * Returns the number of times [run] has been called.
     */
    public fun invocationCount(): Int = _invocations.size

    /**
     * Resets the fixture, clearing both the scripted-response queue
     * and the invocation history.
     */
    public fun reset() {
        scripts.clear()
        _invocations.clear()
    }

    override suspend fun run(command: Command): CommandResult {
        _invocations.add(command)
        check(scripts.isNotEmpty()) {
            "FakeGitRunner: no scripted response was enqueued for command: $command"
        }
        return scripts.removeAt(0)
    }
}
