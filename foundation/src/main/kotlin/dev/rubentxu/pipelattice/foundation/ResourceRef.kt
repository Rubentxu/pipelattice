package dev.rubentxu.pipelattice.foundation

/**
 * Typed reference to a versioned configuration resource.
 *
 * Canonical form (see pipelattice-spec/docs/02_CONFIGURATION_SPEC.md):
 *
 * ```text
 * catalog://<path>[@<version>]
 * ```
 *
 * Examples: `catalog://profiles/java-maven-container@stable`, `catalog://company/base@3`.
 *
 * Note: the resource-kind taxonomy is intentionally NOT modeled here yet. It becomes a sealed
 * hierarchy when the schema registry lands in M1; until then this type only guarantees a
 * well-formed, unambiguous reference.
 */
public data class ResourceRef(
    public val path: String,
    public val version: String? = null,
) {
    init {
        require(path.isNotBlank()) { "ResourceRef.path must not be blank" }
        require('@' !in path) {
            "ResourceRef.path must not contain '@'; move the version into ResourceRef.version"
        }
        require(version == null || version.isNotBlank()) {
            "ResourceRef.version must not be blank when present"
        }
    }

    /** Fully qualified canonical representation, safe to persist and to hash. */
    public val canonicalForm: String
        get() = if (version == null) "$SCHEME://$path" else "$SCHEME://$path@$version"

    override fun toString(): String = canonicalForm

    public companion object {
        public const val SCHEME: String = "catalog"

        /**
         * Parses a canonical reference such as `catalog://build/maven@4`.
         *
         * @throws IllegalArgumentException if the input is not in canonical form.
         */
        public fun parse(raw: String): ResourceRef {
            val prefix = "$SCHEME://"
            require(raw.startsWith(prefix)) {
                "Unsupported resource ref '$raw'; expected '$prefix<path>[@<version>]'"
            }
            val rest = raw.removePrefix(prefix)
            require(rest.isNotBlank()) {
                "Unsupported resource ref '$raw'; path segment is empty"
            }
            val separator = rest.lastIndexOf('@')
            return if (separator >= 0) {
                ResourceRef(path = rest.substring(0, separator), version = rest.substring(separator + 1))
            } else {
                ResourceRef(path = rest)
            }
        }
    }
}
