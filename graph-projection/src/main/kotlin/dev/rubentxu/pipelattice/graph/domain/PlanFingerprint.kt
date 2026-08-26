package dev.rubentxu.pipelattice.graph.domain

/**
 * Deterministic fingerprint of a graph snapshot.
 *
 * Two snapshots with the same fingerprint are guaranteed to have the same
 * canonical serialization of nodes + edges. Computed via SHA-256 over the
 * sorted canonical form.
 */
@JvmInline
public value class PlanFingerprint(public val value: String) {
    init {
        require(value.isNotBlank()) { "PlanFingerprint must not be blank" }
        require(value.length == 64) { "PlanFingerprint must be a 64-char SHA-256 hex digest" }
        require(value.matches(Regex("[0-9a-f]{64}"))) {
            "PlanFingerprint must be a valid 64-char SHA-256 hex digest"
        }
    }
}
