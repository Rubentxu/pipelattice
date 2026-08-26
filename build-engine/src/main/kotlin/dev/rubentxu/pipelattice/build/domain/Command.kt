package dev.rubentxu.pipelattice.build.domain

import java.nio.file.Path

/**
 * A runnable command produced by the build system.
 *
 * [Command] captures the full specification of a subprocess invocation:
 * the program, its arguments, the working directory, and the environment map.
 *
 * ## Example
 * ```kotlin
 * val cmd = Command(
 *     executable = Executable("mvn"),
 *     arguments = listOf(Argument("verify"), Argument("-DskipTests")),
 *     workingDirectory = Path.of("/repo"),
 *     environment = mapOf(EnvironmentKey("HOME") to "/root")
 * )
 * ```
 *
 * @property executable The program to run.
 * @property arguments The command-line arguments (positional and flags).
 * @property workingDirectory The directory from which the command executes.
 * @property environment Additional environment variables to set for the subprocess.
 */
public data class Command(
    public val executable: Executable,
    public val arguments: List<Argument>,
    public val workingDirectory: Path,
    public val environment: Map<EnvironmentKey, String>,
)
