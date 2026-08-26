package dev.rubentxu.pipelattice.foundation

/**
 * Stable identifier of a project within the fleet.
 *
 * Identity types exist to prevent connascence of meaning: a [ProjectId] must never be
 * interchangeable with any other `String`-shaped concept (see ADR-0016 on public API typing).
 */
@JvmInline
public value class ProjectId(public val value: String) {
    init {
        require(value.isNotBlank()) { "ProjectId must not be blank" }
    }

    override fun toString(): String = value
}
