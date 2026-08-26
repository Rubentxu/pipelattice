package dev.rubentxu.pipelattice.compose.domain

import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
import dev.rubentxu.pipelattice.resource.ParameterValue

/**
 * Physical origin of a value within a resolved pipeline definition.
 *
 * @param resource The catalog resource from which this value originated.
 * @param location The source-location metadata (may be null for synthetic or generated values).
 * @param humanForm An optional human-readable alias for [resource]; used in error messages and diagnostics.
 */
public data class ProvenanceSource(
    public val resource: ResourceRef,
    public val location: SourceLocation,
    public val humanForm: ResourceRef? = null,
)

/**
 * A single transformation step applied to a configuration value during composition.
 *
 * @param kind The transformation kind (must be non-blank).
 * @param detail Human-readable description of what this transformation did.
 */
public data class Transformation(
    public val kind: String,
    public val detail: String,
) {
    init {
        require(kind.isNotBlank()) { "Transformation.kind must not be blank" }
    }

    public companion object {
        public const val REQUESTED_AS: String = "REQUESTED_AS"
        public const val IMPORTED_BY: String = "IMPORTED_BY"
        public const val SELECTED_BY: String = "SELECTED_BY"
        public const val OVERRIDDEN_BY: String = "OVERRIDDEN_BY"
        public const val PROVIDED_BY: String = "PROVIDED_BY"
    }
}

/**
 * The complete provenance chain for a single resolved configuration key.
 *
 * @param key The configuration key this provenance entry describes.
 * @param layer The composition layer at which this value was resolved.
 * @param source The physical origin of the value.
 * @param transformations The ordered list of transformations applied to derive this value (must be non-empty).
 * @param effectiveValue The resolved value, or null for intermediate/non-leaf entries.
 */
public data class Provenance(
    public val key: String,
    public val layer: Layer,
    public val source: ProvenanceSource,
    public val transformations: List<Transformation>,
    public val effectiveValue: ParameterValue? = null,
) {
    init {
        require(transformations.isNotEmpty()) { "Provenance.transformations must not be empty" }
    }
}
