package dev.rubentxu.pipelattice.fleet.diff.domain

/**
 * Source of policy violations from a snapshot.
 *
 * This is a seam for spec M3 (scoped GraphView UAT) policy evaluation.
 * In m16 (spec M7 completion), this returns [emptySet].
 *
 * @see <vault>/specs/fleet-diff/REQ-PolicyViolationSource-M3.md (D4)
 * @see <vault>/cycles/.../m16-.../exploration-report.md §3 D4
 *
 * This seam is intentionally empty in m16. Real policy evaluation belongs to
 * spec M3 (scoped GraphView UAT). The seam is type-stable so spec-M3 work
 * can wire [dev.rubentxu.pipelattice.policy] without breaking the public API.
 */
public class PolicyViolationSource(
    private val snapshotRepo: SnapshotRepository,
) : () -> Set<PolicyViolation> {

    /**
     * Returns policy violations for the snapshot.
     *
     * In m16, this always returns [emptySet]. Future versions will
     * evaluate policy rules against the snapshot.
     */
    override fun invoke(): Set<PolicyViolation> = emptySet()
}
