package dev.rubentxu.pipelattice.resource

/** Governance level of a parameter declaration (pipelattice-spec/docs/02 §10). */
public enum class GovernanceMode(public val wireName: String) {
    DEFAULT("default"),
    GUARDRAIL("guardrail"),
    MANDATORY("mandatory"),
    ;

    public companion object {
        private val byWireName: Map<String, GovernanceMode> =
            entries.associateBy(GovernanceMode::wireName)

        public fun fromWire(name: String): GovernanceMode? = byWireName[name]
    }
}

/** Numeric bounds; both ends optional. */
public data class Constraints(
    public val min: Long? = null,
    public val max: Long? = null,
) {
    init {
        require(min == null || max == null || min <= max) {
            "constraints.min ($min) must be <= constraints.max ($max)"
        }
    }
}

/**
 * Flat governance block matching the wire shape:
 * `mode: default|guardrail|mandatory` with optional `constraints` for guardrail.
 *
 * The sealed `GovernanceMode` hierarchy from spec doc 05 is deferred to the policy engine
 * milestone (M3); behavior dispatch is not needed while this is pure data.
 */
public data class Governance(
    public val mode: GovernanceMode = GovernanceMode.DEFAULT,
    public val constraints: Constraints? = null,
) {
    init {
        require(mode != GovernanceMode.GUARDRAIL || constraints != null) {
            "governance mode 'guardrail' requires constraints"
        }
        require(constraints == null || mode == GovernanceMode.GUARDRAIL) {
            "governance.constraints is only valid with mode 'guardrail'"
        }
    }
}
