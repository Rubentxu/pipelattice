package dev.rubentxu.pipelattice.fleet.diff.repository

/**
 * Thrown when the git repository cannot be accessed.
 *
 * This exception is a subtype of [IllegalStateException] so that the CLI entry point's
 * generic `catch (e: Exception)` handler maps it to exit code 10 (EXIT_INTERNAL) as
 * specified in `pipelattice-spec/docs/17_CLI_CONTROL_PLANE.md §4`.
 *
 * Scenarios that throw this exception:
 * - [dev.rubentxu.pipelattice.fleet.diff.repository.GitSnapshotRepository] is constructed
 *   with a path that is not a git working tree.
 * - The `git` binary is not present on the system PATH.
 *
 * FARCH-016 guards the production surface: [GitSnapshotRepository] must not import
 * `java.lang.ProcessBuilder`, `java.lang.Runtime`, `java.lang.Process`, `kotlin.system.exitProcess`,
 * or `org.apache.tools.ant.taskdefs.Execute`.
 *
 * @see GitSnapshotRepository
 * @see dev.rubentxu.pipelattice.fleet.diff.ports.gitRevParse
 */
public class GitRepositoryUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
