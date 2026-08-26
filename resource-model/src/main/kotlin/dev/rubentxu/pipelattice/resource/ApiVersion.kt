package dev.rubentxu.pipelattice.resource

/**
 * Group/version identifier of the resource schema, e.g. `pipelattice.dev/v1alpha1`.
 */
@JvmInline
public value class ApiVersion(public val value: String) {
    init {
        require(value.isNotBlank()) { "apiVersion must not be blank" }
        require(' ' !in value && '/' in value) {
            "apiVersion '$value' must have the form '<group>/<version>'"
        }
    }

    public val isKnown: Boolean
        get() = this == KNOWN

    override fun toString(): String = value

    public companion object {
        /** Only schema version implemented by this compiler release. */
        public val KNOWN: ApiVersion = ApiVersion("pipelattice.dev/v1alpha1")
    }
}
