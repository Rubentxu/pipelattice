package dev.rubentxu.pipelattice.build.facade

import dev.rubentxu.pipelattice.build.domain.Command
import dev.rubentxu.pipelattice.build.domain.CommandResult
import dev.rubentxu.pipelattice.build.ports.ProcessRunner
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

/**
 * Default [ProcessRunner] implementation backed by [ProcessBuilder].
 *
 * This is the production implementation of the [ProcessRunner] port.
 * All subprocess execution is routed through this class, keeping the
 * [ProcessBuilder] API isolated behind the port interface per FARCH-013.
 *
 * **Thread safety:** instances must not be shared across threads.
 *
 * @see ProcessRunner
 * @see dev.rubentxu.pipelattice.build.fake.FakeProcessRunner
 */
public class DefaultProcessRunner : ProcessRunner {

    override suspend fun run(command: Command): CommandResult {
        return runBlocking {
            val processBuilder = ProcessBuilder(
                command.executable.value,
                *command.arguments.map { it.value }.toTypedArray(),
            )
            processBuilder.directory(command.workingDirectory.toFile())
            command.environment.forEach { (key, value) ->
                processBuilder.environment()[key.name] = value
            }
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()

            val stdout = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                CommandResult.Success(stdout = stdout, stderr = "")
            } else {
                CommandResult.Failed(exitCode = exitCode, stdout = stdout, stderr = "")
            }
        }
    }
}
