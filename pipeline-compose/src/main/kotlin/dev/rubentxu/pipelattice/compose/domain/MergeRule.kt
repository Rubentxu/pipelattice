package dev.rubentxu.pipelattice.compose.domain

import dev.rubentxu.pipelattice.resource.ParameterValue

/**
 * Sealed hierarchy of parameter tree nodes produced during composition.
 */
public sealed interface ParameterNode {
    /**
     * A scalar leaf node carrying a single [ParameterValue].
     */
    public data class ScalarNode(public val value: ParameterValue) : ParameterNode

    /**
     * A map branch node carrying child entries keyed by string.
     */
    public data class MapNode(public val entries: Map<String, ParameterNode>) : ParameterNode

    /**
     * A list branch node carrying an ordered list of child nodes.
     */
    public data class ListNode(public val items: List<ParameterNode>) : ParameterNode
}

/**
 * Result of applying a [MergeRule] to a pair of parameter nodes.
 *
 * @param value The merged parameter node, or null if both inputs are null.
 * @param winner The layer of the winning node, or null if the result is null.
 */
public data class MergeResult(
    public val value: ParameterNode?,
    public val winner: Layer?,
)

/**
 * Sealed interface for merge strategies applied during profile composition.
 */
public sealed interface MergeRule {
    /**
     * Applies this merge rule to the [lower] and [upper] parameter nodes.
     *
     * @param lower The lower-precedence node (may be null).
     * @param upper The higher-precedence node (may be null).
     * @return The merged [MergeResult].
     */
    public fun apply(lower: ParameterNode?, upper: ParameterNode?): MergeResult

    /**
     * Scalar-replacement strategy: upper always replaces lower.
     * If upper is null, lower is returned (even though the winner is null).
     */
    public data object ScalarReplace : MergeRule {
        override fun apply(lower: ParameterNode?, upper: ParameterNode?): MergeResult {
            return MergeResult(value = upper ?: lower, winner = null)
        }
    }

    /**
     * Structural map merge: recursively merges maps entry-by-entry using [keyMerger].
     * If both are null, returns null. If only one is non-null, returns it.
     * If both are non-null [MapNode]s, merges entries; otherwise the non-null node wins.
     *
     * @param keyMerger Function called for each key present in either map.
     *   Receives (key, lowerValue, upperValue) and returns the merged [ParameterNode].
     */
    public data class MapStructural(
        public val keyMerger: (key: String, lowerValue: ParameterNode?, upperValue: ParameterNode?) -> ParameterNode?,
    ) : MergeRule {
        override fun apply(lower: ParameterNode?, upper: ParameterNode?): MergeResult {
            return MergeResult(
                value = mergeNodes(lower, upper),
                winner = null
            )
        }

        private fun mergeNodes(lower: ParameterNode?, upper: ParameterNode?): ParameterNode? {
            return when {
                lower == null && upper == null -> null
                lower == null -> upper
                upper == null -> lower
                lower is ParameterNode.MapNode && upper is ParameterNode.MapNode -> {
                    val allKeys = lower.entries.keys + upper.entries.keys
                    val mergedEntries = allKeys.associateWith { key ->
                        val lowerValue = lower.entries[key]
                        val upperValue = upper.entries[key]
                        keyMerger(key, lowerValue, upperValue) ?: lowerValue ?: upperValue!!
                    }
                    ParameterNode.MapNode(mergedEntries)
                }
                else -> upper
            }
        }
    }

    /**
     * List-replacement strategy: upper always replaces lower entirely.
     */
    public data object ListReplace : MergeRule {
        override fun apply(lower: ParameterNode?, upper: ParameterNode?): MergeResult {
            return MergeResult(value = upper ?: lower, winner = null)
        }
    }
}
