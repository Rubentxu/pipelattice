package dev.rubentxu.pipelattice.fleet.diff.cli

import dev.rubentxu.pipelattice.fleet.diff.domain.FleetCandidateDiff
import dev.rubentxu.pipelattice.fleet.diff.json.FleetDiffJsonEncoder
import dev.rubentxu.pipelattice.fleet.diff.repository.InMemorySnapshotRepository
import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
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
 */
public object Main {

    public fun main(args: Array<String>) {
        val baselineRef = args.extract("--baseline").required()
        val candidateRef = args.extract("--candidate").required()
        val outputPath = args.extract("--output")

        val repo = InMemorySnapshotRepository()
        val store = InMemoryGraphProjectionStore()

        // Load snapshots into the repository
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
        // Baseline snapshot
        val baselineSnap = GraphSnapshot(
            nodes = emptySet(),
            edges = emptySet(),
            fingerprint = PlanFingerprint("0".repeat(64)),
        )
        repo.store("baseline", baselineSnap)

        // Candidate snapshot
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

    internal fun String?.required(): String {
        return this ?: throw IllegalArgumentException("Required argument missing")
    }
}

public fun main(args: Array<String>) {
    Main.main(args)
}
