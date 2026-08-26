package dev.rubentxu.pipelattice.compose.compose

import dev.rubentxu.pipelattice.compose.domain.MergeResult
import dev.rubentxu.pipelattice.compose.domain.MergeRule
import dev.rubentxu.pipelattice.compose.domain.ParameterNode

/**
 * Internal merge engine that dispatches merge operations according to merge rules.
 *
 * Throws [MergeUnsupportedException] when identity-keyed merge is attempted with a ListNode,
 * as per ADR-0009 and spec §7.
 */
internal class MergeEngine {

    /**
     * Exception thrown when a merge operation is not supported.
     *
     * @param path The path at which the unsupported merge was attempted.
     * @param mergeKey The schema merge key that triggered the rejection.
     * @param reason Human-readable explanation citing ADR-0009 and spec §7.
     */
    class MergeUnsupportedException(
        public val path: String,
        public val mergeKey: String,
        reason: String,
    ) : IllegalStateException(
        "Unsupported merge at '$path' with mergeKey '$mergeKey': $reason"
    )

    /**
     * Dispatches a merge operation for the given path using the specified rule.
     *
     * If [schemaMergeKey] is non-null and either [lower] or [upper] is a [ParameterNode.ListNode],
     * throws [MergeUnsupportedException] as identity-keyed merge with lists is not supported.
     *
     * @param path The configuration path being merged.
     * @param schemaMergeKey The schema-defined merge key, or null if not identity-keyed.
     * @param rule The merge rule to apply.
     * @param lower The lower-precedence parameter node (may be null).
     * @param upper The higher-precedence parameter node (may be null).
     * @return The merged [MergeResult].
     * @throws MergeUnsupportedException if identity-keyed merge is attempted with a ListNode.
     */
    fun dispatch(
        path: String,
        schemaMergeKey: String?,
        rule: MergeRule,
        lower: ParameterNode?,
        upper: ParameterNode?,
    ): MergeResult {
        // Identity-keyed merge rejection: lists cannot be merged with identity semantics
        if (schemaMergeKey != null) {
            if (lower is ParameterNode.ListNode) {
                throw MergeUnsupportedException(
                    path = path,
                    mergeKey = schemaMergeKey,
                    reason = "ListNode merge is not supported with identity key. See ADR-0009 and spec §7."
                )
            }
            if (upper is ParameterNode.ListNode) {
                throw MergeUnsupportedException(
                    path = path,
                    mergeKey = schemaMergeKey,
                    reason = "ListNode merge is not supported with identity key. See ADR-0009 and spec §7."
                )
            }
        }
        return rule.apply(lower, upper)
    }
}
