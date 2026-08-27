package dev.rubentxu.pipelattice.fleet.diff.cli

import dev.rubentxu.pipelattice.fleet.diff.domain.FleetCandidateDiff
import dev.rubentxu.pipelattice.fleet.diff.json.FleetDiffJsonEncoder
import dev.rubentxu.pipelattice.fleet.diff.repository.InMemorySnapshotRepository
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import dev.rubentxu.pipelattice.graph.store.InMemoryGraphProjectionStore

/**
 * CLI entry point for the fleet-diff tool.
 *
 * Usage:
 *   fleet-diff --baseline <ref> --candidate <ref> [--output <path>]
 *
 * Arguments:
 *   --baseline   Required. Reference to the baseline snapshot.
 *   --candidate  Required. Reference to the candidate snapshot.
 *   --output     Optional. Output file path. Defaults to stdout.
 *
 * Exit codes (per pipelattice-spec/docs/17_CLI_CONTROL_PLANE.md §4 + BSD sysexits.h):
 *   0  success
 *   2  validation failure (e.g. snapshot ref not found in repository)
 *   10 internal error (unexpected exception)
 *   64 command line usage error (missing required flag)
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
    } catch (e: MissingArgumentException) {
        System.err.println("usage error: ${e.message}")
        System.err.println("Usage: fleet-diff --baseline <ref> --candidate <ref> [--output <path>]")
        EXIT_USAGE
    } catch (e: IllegalArgumentException) {
        System.err.println("validation error: ${e.message}")
        EXIT_VALIDATION
    } catch (e: Exception) {
        System.err.println("internal error: ${e.message}")
        EXIT_INTERNAL
    }

    /**
     * Runs the diff workflow, throwing on any failure.
     *
     * Separated from [run] so the exit-code mapping and message formatting stay
     * in the public entry point.
     */
    private fun execute(args: Array<String>) {
        val baselineRef = args.extract("--baseline").required("--baseline")
        val candidateRef = args.extract("--candidate").required("--candidate")
        val outputPath = args.extract("--output")

        val repo = InMemorySnapshotRepository()
        val store = InMemoryGraphProjectionStore()

        loadSnapshots(repo)

        val report = FleetCandidateDiff(repo, store).diff(baselineRef, candidateRef)
        val json = FleetDiffJsonEncoder.encode(report)

        if (outputPath != null) {
            java.nio.file.Files.writeString(java.nio.file.Path.of(outputPath), json)
        } else {
            println(json)
        }
    }

    /**
     * Loads sample snapshots for demonstration.
     *
     * In a real implementation, this would load from a catalog or build artifact.
     */
    private fun loadSnapshots(repo: InMemorySnapshotRepository) {
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

    internal fun Array<String>.extract(flag: String): String? {
        val index = indexOf(flag)
        return if (index >= 0 && index + 1 < size) {
            this[index + 1]
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
