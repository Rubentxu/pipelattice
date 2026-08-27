package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.fleet.diff.ports.GitRefResolution
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import java.security.MessageDigest

/**
 * Factory for creating [GraphSnapshot] placeholders from resolved git refs.
 *
 * **V1 plumbing-only placeholder** — content emission (full graph nodes/edges from a compiler
 * run at a given git ref) is deferred to M12+ per orchestrator Q1 ruling. The returned
 * snapshots have `nodes = emptySet()` and `edges = emptySet()`.
 *
 * Fingerprint scheme: `PlanFingerprint(SHA-256("git-ref-only/v1:" + resolvedSha))`.
 * Two calls with the same resolved SHA produce identical fingerprints, enabling structural
 * diffs between two non-trivial refs even when content is empty.
 *
 * @see GitSnapshotRepository
 * @see GitRefResolution.Resolved
 */
public class GitSnapshotFactory {

    /**
     * Creates a placeholder [GraphSnapshot] from a resolved git SHA.
     *
     * @param resolution The resolved ref, guaranteed to be a valid 40-hex SHA.
     * @return A [GraphSnapshot] with empty nodes/edges and a domain-tagged fingerprint.
     */
    public fun create(resolution: GitRefResolution.Resolved): GraphSnapshot {
        val fingerprintValue = sha256("git-ref-only/v1:${resolution.sha}")
        return GraphSnapshot(
            nodes = emptySet(),
            edges = emptySet(),
            fingerprint = PlanFingerprint(fingerprintValue),
        )
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
