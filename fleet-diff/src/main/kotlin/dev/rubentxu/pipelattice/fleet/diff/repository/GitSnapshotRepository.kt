package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.build.domain.CommandResult
import dev.rubentxu.pipelattice.build.ports.ProcessRunner
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.fleet.diff.domain.SnapshotRepository
import dev.rubentxu.pipelattice.fleet.diff.ports.GitRefResolution
import dev.rubentxu.pipelattice.fleet.diff.ports.gitRevParse
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

/**
 * Git-backed [SnapshotRepository] implementation that resolves refs via the git CLI.
 *
 * **V1 plumbing-only per orchestrator Q1 ruling.** Full `GraphSnapshot` content emission
 * (compiler integration at a git ref) is deferred to M12+.
 *
 * This implementation delegates ALL external process invocation to the injected [ProcessRunner]
 * port and MUST NOT use `ProcessBuilder`, `Runtime.exec`, `java.lang.Process`,
 * `kotlin.system.exitProcess`, or `org.apache.tools.ant.taskdefs.Execute` directly.
 * This invariant is enforced by FARCH-016 (ArchUnit bytecode check) and the defensive scan
 * registered in `architecture-tests/build.gradle.kts`.
 *
 * ## Exit code mapping
 * - Exit 0 from `git rev-parse` → [GitRefResolution.Resolved] → snapshot returned.
 * - Exit 128 + "bad revision" in stderr → [GitRefResolution.NotFound] → `null` returned
 *   (consumed by [FleetCandidateDiff.diff] → `IllegalArgumentException` → CLI exit 2).
 * - Any other failure (not a git repo, missing binary) → [GitRepositoryUnavailableException]
 *   (consumed by CLI generic `Exception` handler → CLI exit 10).
 *
 * @param workingDir The git working tree directory. Must be a valid git repository.
 * @param processRunner The [ProcessRunner] port for executing the git subprocess.
 * @param snapshotFactory Factory for creating [GraphSnapshot] placeholders. Defaults to [GitSnapshotFactory].
 * @throws GitRepositoryUnavailableException when the working directory is not a git repository
 *         or the git binary is unavailable.
 * @see GitSnapshotFactory
 * @see gitRevParse
 */
public class GitSnapshotRepository(
    private val workingDir: Path,
    private val processRunner: ProcessRunner,
    private val snapshotFactory: GitSnapshotFactory = GitSnapshotFactory(),
) : SnapshotRepository {

    /**
     * Loads a [GraphSnapshot] by resolving the given git ref.
     *
     * @param ref A git ref (branch, tag, SHA, HEAD, HEAD~N, etc.).
     * @return A placeholder [GraphSnapshot] if the ref resolves successfully,
     *         or `null` if the ref does not exist in the repository.
     * @throws GitRepositoryUnavailableException if the working directory is not a git repository
     *         or the git binary is unavailable.
     */
    override fun load(ref: String): GraphSnapshot? {
        val command = gitRevParse(workingDir, ref)
        val result = runBlocking { processRunner.run(command) }
        return when (result) {
            is CommandResult.Success -> {
                val sha = result.stdout.trim()
                require(sha.isNotBlank()) { "git rev-parse returned blank SHA for ref: $ref" }
                val resolution = GitRefResolution.Resolved(sha)
                snapshotFactory.create(resolution)
            }
            is CommandResult.Failed -> {
                when {
                    result.exitCode == 128 && "bad revision" in result.stderr -> null
                    else -> throw GitRepositoryUnavailableException(
                        "git unavailable at '$workingDir': ${result.stderr.ifBlank { "exit ${result.exitCode}" }}",
                        null,
                    )
                }
            }
        }
    }
}
