package dev.rubentxu.pipelattice.build.domain

/**
 * Typed wrapper for an environment-variable key.
 *
 * Environment keys identify variables in the subprocess environment map
 * (e.g., `HOME`, `PATH`, `JAVA_HOME`). The blank-string guard prevents
 * construction of invalid empty keys.
 */
@JvmInline
public value class EnvironmentKey(public val name: String) {
    init {
        require(name.isNotBlank()) { "EnvironmentKey must not be blank" }
    }

    override fun toString(): String = name
}
