package dev.rubentxu.pipelattice.resource

/**
 * Kinds known to this compiler release. Grows one resource at a time (bootstrap PR #2 adds
 * exactly two). Unknown kinds on wire are reported as diagnostics, never guessed.
 */
public enum class ResourceKind(public val wireName: String) {
    PIPELINE_DEFINITION("PipelineDefinition"),
    PIPELINE_PROFILE("PipelineProfile"),
    ;

    public companion object {
        private val byWireName: Map<String, ResourceKind> =
            entries.associateBy(ResourceKind::wireName)

        /** Returns null for unknown wire names; callers must report a diagnostic. */
        public fun fromWire(name: String): ResourceKind? = byWireName[name]
    }
}
