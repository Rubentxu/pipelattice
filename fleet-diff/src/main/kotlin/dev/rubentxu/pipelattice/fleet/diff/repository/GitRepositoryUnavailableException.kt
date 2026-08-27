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
 * - The JGit object store is inaccessible (corrupted, permissions, etc.).
 *
 * FARCH-016 v1 guards the production surface: [GitSnapshotRepository] must not import
 * `java.lang.ProcessBuilder`, `java.lang.Runtime`, `java.lang.Process`, `kotlin.system.exitProcess`,
 * or `org.apache.tools.ant.taskdefs.Execute`. This exception is the upstream result of
 * JGit's [java.io.IOException] when the repository cannot be opened.
 *
 * @see GitSnapshotRepository
 */
public class GitRepositoryUnavailableException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
