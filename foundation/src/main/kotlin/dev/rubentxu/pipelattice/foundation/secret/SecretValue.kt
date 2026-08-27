package dev.rubentxu.pipelattice.foundation.secret

/**
 * Opaque carrier for a secret material that enforces a non-rendering invariant.
 *
 * The [marker] is an opaque, non-secret identifier used for equality
 * ([equals]/[hashCode]). It is NOT the secret material.
 *
 * The non-rendering invariant guarantees that:
 * 1. [toString] never exposes the underlying material.
 * 2. [equals] compares on [marker] only (material is ignored).
 * 3. [hashCode] derives from [marker] only.
 * 4. [material] is the only path to the underlying secret string.
 *
 * Construction MUST go through [SecretValue.of]; the canonical constructor is private.
 * This forces the API surface to always redact via [toString]/[hashCode]/[equals].
 *
 * ## Usage
 * ```kotlin
 * val value = SecretValue.of("env-var", "synthetic-payload-X")
 * value.material()  // returns "synthetic-payload-X"
 * value.toString()  // returns "<redacted:SecretValue>" (NEVER the material)
 * value == SecretValue.of("env-var", "different-material")  // true (same marker)
 * ```
 */
public class SecretValue private constructor(
    private val marker: String,
    private val material: String,
) {

    init {
        require(marker.isNotBlank()) { "SecretValue.marker must not be blank" }
        require(material.isNotBlank()) { "SecretValue.material must not be blank" }
    }

    /**
     * Returns the underlying secret material.
     *
     * This is the ONLY public accessor for the secret value.
     * All other members ([toString], [equals], [hashCode]) redact it.
     */
    public fun material(): String = material

    /**
     * Returns the redaction marker.
     *
     * NEVER exposes [material].
     */
    override fun toString(): String = REDACTION_MARKER

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SecretValue) return false
        return marker == other.marker
    }

    override fun hashCode(): Int = marker.hashCode()

    public companion object {
        private const val REDACTION_MARKER = "<redacted:SecretValue>"

        /**
         * Factory for constructing a [SecretValue].
         *
         * @param marker An opaque identifier for identity purposes (not the secret).
         * @param material The underlying secret string.
         * @return A [SecretValue] with the non-rendering invariant enforced.
         */
        public fun of(marker: String, material: String): SecretValue {
            return SecretValue(marker, material)
        }
    }
}
