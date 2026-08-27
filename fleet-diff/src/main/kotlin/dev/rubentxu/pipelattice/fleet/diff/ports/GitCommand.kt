package dev.rubentxu.pipelattice.fleet.diff.ports

import dev.rubentxu.pipelattice.build.domain.Argument
import dev.rubentxu.pipelattice.build.domain.Command
import dev.rubentxu.pipelattice.build.domain.Executable
import java.nio.file.Path

/**
 * Constructs a `git rev-parse --verify <ref>^{commit}` command for resolving a git ref to a SHA.
 *
 * Used by [dev.rubentxu.pipelattice.fleet.diff.repository.GitSnapshotRepository] to
 * bridge the [dev.rubentxu.pipelattice.build.ports.ProcessRunner] port with the git CLI.
 *
 * @param workingDir The git working tree directory.
 * @param ref The git ref to resolve (branch, tag, SHA, HEAD, HEAD~N, etc.).
 * @return A [Command] ready to be executed by a [dev.rubentxu.pipelattice.build.ports.ProcessRunner].
 */
public fun gitRevParse(workingDir: Path, ref: String): Command {
    return Command(
        executable = Executable("git"),
        arguments = listOf(
            Argument("-C"),
            Argument(workingDir.toString()),
            Argument("rev-parse"),
            Argument("--verify"),
            Argument("$ref^{commit}"),
        ),
        workingDirectory = workingDir,
        environment = emptyMap(),
    )
}
