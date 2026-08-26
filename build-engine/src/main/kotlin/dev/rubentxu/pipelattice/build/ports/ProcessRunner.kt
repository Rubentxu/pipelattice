package dev.rubentxu.pipelattice.build.ports

import dev.rubentxu.pipelattice.build.domain.Command
import dev.rubentxu.pipelattice.build.domain.CommandResult

/**
 * Port for running external processes.
 *
 * Implementations of this port delegate to the underlying process-run primitive
 * (e.g., `java.lang.ProcessBuilder`) while keeping it isolated behind this
 * interface. This allows the domain to remain free of direct process APIs and
 * enables testability via [dev.rubentxu.pipelattice.build.fake.FakeProcessRunner].
 *
 * All operations are `suspend` to integrate cleanly with Kotlin coroutines and
 * to allow non-blocking subprocess execution in the build pipeline.
 *
 * ## Usage
 * ```kotlin
 * class DefaultProcessRunner : ProcessRunner {
 *     override suspend fun run(command: Command): CommandResult {
 *         val process = ProcessBuilder(command.executable.value)
 *             .directory(command.workingDirectory.toFile())
 *             .redirectErrorStream(true)
 *             .start()
 *         return process.waitFor().let { exitCode ->
 *             CommandResult.fromExitCode(exitCode)
 *         }
 *     }
 * }
 * ```
 *
 * ## Thread safety
 * Implementations must be thread-safe if used concurrently.
 * The [dev.rubentxu.pipelattice.build.fake.FakeProcessRunner] fixture
 * assumes a single-threaded caller in A-min.
 */
public interface ProcessRunner {
    /**
     * Executes the given [command] and returns its result.
     *
     * @param command The command specification to execute.
     * @return [CommandResult.Success] if the process exits with code 0,
     *         or [CommandResult.Failed] otherwise.
     */
    public suspend fun run(command: Command): CommandResult
}
