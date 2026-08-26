package dev.rubentxu.pipelattice.compose.domain

/**
 * Composition precedence layers, ordered from lowest to highest priority.
 *
 * Higher precedence wins when merging values from different layers:
 * - [PROFILE_IMPORT] (0) — imported profiles, lowest priority
 * - [PROFILE] (1) — direct profile values
 * - [LOCAL] (2) — local overrides, highest priority
 */
public enum class Layer(public val precedence: Int) {
    PROFILE_IMPORT(0),
    PROFILE(1),
    LOCAL(2);

    public companion object {
        /** All layers sorted by ascending precedence. */
        public val all: List<Layer> = entries.sortedBy { it.precedence }
    }
}
