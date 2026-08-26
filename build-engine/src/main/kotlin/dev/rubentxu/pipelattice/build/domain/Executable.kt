package dev.rubentxu.pipelattice.build.domain

/**
 * Typed wrapper for an executable name or path.
 *
 * An executable identifies the program to be run, such as `mvn`, `gradle`, or `/usr/bin/python3`.
 * The blank-string guard prevents construction of invalid empty identifiers.
 */
@JvmInline
public value class Executable(public val value: String) {
    init {
        require(value.isNotBlank()) { "Executable must not be blank" }
    }

    override fun toString(): String = value
}
