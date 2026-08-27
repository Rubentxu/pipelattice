package dev.rubentxu.pipelattice.fleet.diff.cli

import dev.rubentxu.pipelattice.fleet.diff.domain.FleetCandidateDiff
import dev.rubentxu.pipelattice.fleet.diff.json.FleetDiffJsonEncoder
import dev.rubentxu.pipelattice.fleet.diff.repository.GitSnapshotRepository
import dev.rubentxu.pipelattice.fleet.diff.repository.InMemorySnapshotRepository
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import dev.rubentxu.pipelattice.graph.store.InMemoryGraphProjectionStore
import java.nio.file.Path

/**
 * CLI entry point for the fleet-diff tool.
 *
 * Usage:
 *   fleet-diff --baseline <ref> --candidate <ref> [--output <path>]
 *   fleet-diff --base <ref> --candidate <ref> [--repo <path>]
 *
 * Arguments:
 *   --baseline   Required (legacy). Reference to the baseline snapshot.
 *   --base       Required (alias for --baseline). Reference to the baseline snapshot.
 *   --candidate  Required. Reference to the candidate snapshot.
 *   --output     Optional. Output file path. Defaults to stdout.
 *   --repo       Optional. Path to a git repository. Defaults to "." (identity path).
 *                When "." or absent, uses an in-memory identity repository.
 *                When a valid git repo path, uses GitSnapshotRepository to resolve
 *                refs via the git CLI.
 *
 * Exit codes (per pipelattice-spec/docs/17_CLI_CONTROL_PLANE.md §4 + BSD sysexits.h):
 *   0  success
 *   2  validation failure (e.g. snapshot ref not found in repository)
 *   10 internal error (unexpected exception, e.g. GitRepositoryUnavailableException)
 *   64 command line usage error (missing required flag)
 *
 * ## Example
 *   pipelattice diff --base main --candidate pr/123
 *   pipelattice diff --repo /path/to/repo --base HEAD~1 --candidate HEAD
 */
public object Main {

    /** Successful execution. */
    public const val EXIT_SUCCESS: Int = 0

    /** Validation failure — input did not pass business validation. */
    public const val EXIT_VALIDATION: Int = 2

    /** Internal error — unexpected exception during execution. */
    public const val EXIT_INTERNAL: Int = 10

    /** Command line usage error — required flag missing or malformed (EX_USAGE). */
    public const val EXIT_USAGE: Int = 64

    public fun main(args: Array<String>) {
        kotlin.system.exitProcess(run(args))
    }

    /**
     * Executes the CLI workflow and returns the process exit code.
     *
     * Exposed separately from [main] so tests can assert the exit code without
     * spawning a subprocess. Never calls [System.exit]; returns the code instead.
     */
    public fun run(args: Array<String>): Int = try {
        execute(args)
        EXIT_SUCCESS
    } catch (e: Exception) {
        val result = mapExceptionToExit(e)
        System.err.println(result.message)
        result.code
    }

    private data class ExitResult(val code: Int, val message: String)

    private fun mapExceptionToExit(e: Exception): ExitResult = when (e) {
        is MissingArgumentException -> ExitResult(
            EXIT_USAGE,
            "usage error: ${e.message}\nUsage: fleet-diff --baseline <ref> --candidate <ref> [--output <path>]"
        )
        is IllegalArgumentException -> ExitResult(
            EXIT_VALIDATION,
            "validation error: ${e.message}"
        )
        else -> ExitResult(
            EXIT_INTERNAL,
            "internal error: ${e.message}"
        )
    }

    /**
     * Runs the diff workflow, throwing on any failure.
     *
     * Separated from [run] so the exit-code mapping and message formatting stay
     * in the public entry point.
     */
    private fun execute(args: Array<String>) {
        // --base is first-wins alias for --baseline
        val baselineRef = (extract(args, "--base") ?: extract(args, "--baseline")).required("--baseline or --base")
        val candidateRef = extract(args, "--candidate").required("--candidate")
        val outputPath = extract(args, "--output")
        val repoPath = extract(args, "--repo") ?: "."

        val repo: InMemorySnapshotRepository
        if (repoPath == ".") {
            // Identity path: store synthetic snapshots at traditional "baseline"/"candidate" keys
            // User-provided refs are used for LOADING, not storing
            repo = InMemorySnapshotRepository()
            storeSynthetic(repo)
        } else {
            // Git path: use GitSnapshotRepository to resolve refs via git CLI
            val gitRepo = GitSnapshotRepository(Path.of(repoPath))
            repo = InMemorySnapshotRepository()
            // Load baseline and candidate from git and store in the in-memory repo
            val baselineSnap = gitRepo.load(baselineRef)
                ?: throw IllegalArgumentException("Baseline snapshot not found: $baselineRef")
            val candidateSnap = gitRepo.load(candidateRef)
                ?: throw IllegalArgumentException("Candidate snapshot not found: $candidateRef")
            repo.store(baselineRef, baselineSnap)
            repo.store(candidateRef, candidateSnap)
        }

        val store = InMemoryGraphProjectionStore()

        val report = FleetCandidateDiff(repo, store).diff(baselineRef, candidateRef)
        val json = FleetDiffJsonEncoder.encode(report)

        if (outputPath != null) {
            java.nio.file.Files.writeString(java.nio.file.Path.of(outputPath), json)
        } else {
            println(json)
        }
    }

    /**
     * Stores synthetic baseline and candidate snapshots for the identity path.
     *
     * These are placeholder snapshots with deterministic fingerprints used when
     * no `--repo` is provided (legacy M9 behavior preserved for CLI hermetic testing).
     *
     * The identity path ALWAYS stores at "baseline"/"candidate" keys, regardless of
     * user-provided ref names. User-provided refs are used only for loading.
     */
    private fun storeSynthetic(repo: InMemorySnapshotRepository) {
        val baselineSnap = GraphSnapshot(
            nodes = emptySet(),
            edges = emptySet(),
            fingerprint = PlanFingerprint("0".repeat(64)),
        )
        repo.store("baseline", baselineSnap)

        val candidateSnap = GraphSnapshot(
            nodes = emptySet(),
            edges = emptySet(),
            fingerprint = PlanFingerprint("1".repeat(64)),
        )
        repo.store("candidate", candidateSnap)
    }

    /**
     * Extracts the value of a flag from command-line arguments.
     *
     * Exposed as a public method on [Main] so tests can verify flag parsing.
     *
     * @param args The command-line arguments.
     * @param flag The flag to look for (e.g., "--baseline", "--base").
     * @return The value following the flag, or null if the flag is absent.
     */
    public fun extract(args: Array<String>, flag: String): String? {
        val index = args.indexOf(flag)
        return if (index >= 0 && index + 1 < args.size) {
            args[index + 1]
        } else {
            null
        }
    }

    internal fun String?.required(name: String): String {
        return this ?: throw MissingArgumentException("Required argument missing: $name")
    }
}

/**
 * Thrown when a required CLI argument is missing or its flag is absent.
 *
 * Extends [IllegalArgumentException] for backwards compatibility with callers
 * that already catch the parent type. The CLI entry point catches this
 * subclass specifically and maps it to exit code 64 (EX_USAGE).
 */
public class MissingArgumentException(message: String) : IllegalArgumentException(message)

public fun main(args: Array<String>) {
    Main.main(args)
}
