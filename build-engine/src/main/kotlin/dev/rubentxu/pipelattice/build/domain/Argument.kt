package dev.rubentxu.pipelattice.build.domain

/**
 * Typed wrapper for a command-line argument.
 *
 * Arguments are the positional or named parameters passed to an [Executable].
 * The blank-string guard prevents construction of invalid empty arguments.
 */
@JvmInline
public value class Argument(public val value: String) {
    init {
        require(value.isNotBlank()) { "Argument must not be blank" }
    }

    override fun toString(): String = value
}
