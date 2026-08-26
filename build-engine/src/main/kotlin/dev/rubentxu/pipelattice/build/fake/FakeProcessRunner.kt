package dev.rubentxu.pipelattice.build.fake

import dev.rubentxu.pipelattice.build.domain.Command
import dev.rubentxu.pipelattice.build.domain.CommandResult
import dev.rubentxu.pipelattice.build.ports.ProcessRunner

/**
 * A fake [ProcessRunner] that scripts deterministic responses for unit testing.
 *
 * This fixture operates as a FIFO queue of scripted [CommandResult] responses.
 * Each call to [run] records the [Command] in [invocations] and returns the
 * next response from the queue. If the queue is exhausted, the call throws
 * [IllegalStateException].
 *
 * ## Usage
 * ```kotlin
 * val runner = FakeProcessRunner()
 * runner.enqueue(CommandResult.Success("BUILD SUCCESS", ""))
 * runner.enqueue(CommandResult.Failed(1, "", "BUILD FAILED"))
 *
 * val result1 = runner.run(someCommand)  // returns Success("BUILD SUCCESS", "")
 * val result2 = runner.run(someCommand)  // returns Failed(1, "", "BUILD FAILED")
 *
 * assertEquals(2, runner.invocationCount())
 * assertEquals(listOf(someCommand, someCommand), runner.invocations())
 * ```
 *
 * ## Thread safety
 * A-min assumes a single-threaded caller. The fixture is **not** safe for
 * concurrent use from multiple coroutines. Concurrency primitives (Mutex, Channel)
 * are deferred to A-lite.
 *
 * ## Note
 * This fixture lives in `:build-engine` (module-local) per decision Q2.
 * Promotion to `:testkit` is deferred to A-lite when the first real provider lands.
 */
public class FakeProcessRunner : ProcessRunner {

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
            "FakeProcessRunner: no scripted response was enqueued for command: $command"
        }
        return scripts.removeAt(0)
    }
}
