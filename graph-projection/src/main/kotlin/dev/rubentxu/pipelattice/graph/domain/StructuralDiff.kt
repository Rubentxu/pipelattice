package dev.rubentxu.pipelattice.graph.domain

/**
 * Output of [diff].
 *
 * A-min provides the minimum required by spec 04 §8 (affected nodes) plus
 * the symmetric delta on edges. The full §8 output (effectiveChanges,
 * invalidPlans, newPolicyViolations, resolvedPolicyViolations, providerChanges,
 * localOverrides) is deferred to A-lite when the compiler emits ChangeSets.
 */
public data class StructuralDiff(
    public val addedEdges: Set<Edge>,
    public val removedEdges: Set<Edge>,
    public val affectedNodes: Set<GraphNode>,
) {
    public companion object {
        /**
         * Computes the structural diff between [baseline] and [candidate].
         *
         * Returns a [StructuralDiff] containing:
         * - [StructuralDiff.addedEdges]: edges in candidate but not in baseline
         * - [StructuralDiff.removedEdges]: edges in baseline but not in candidate
         * - [StructuralDiff.affectedNodes]: union of endpoints of added + removed edges
         *
         * Symmetric set difference; deterministic regardless of input ordering.
         */
        public fun diff(baseline: GraphSnapshot, candidate: GraphSnapshot): StructuralDiff {
            val added = candidate.edges - baseline.edges
            val removed = baseline.edges - candidate.edges
            val affected = (added.asSequence() + removed.asSequence())
                .flatMap { sequenceOf(it.source, it.target) }
                .toSet()
            return StructuralDiff(
                addedEdges = added,
                removedEdges = removed,
                affectedNodes = affected,
            )
        }
    }
}
