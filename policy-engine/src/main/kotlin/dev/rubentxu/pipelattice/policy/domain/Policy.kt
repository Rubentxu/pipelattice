package dev.rubentxu.pipelattice.policy.domain

/**
 * A named, versioned policy that groups a list of rules to evaluate against a resource scope.
 *
 * @param id Unique identifier for this policy.
 * @param version Version string for this policy definition.
 * @param scope Resource scope selector (e.g. catalog path) to which this policy applies.
 * @param rules Ordered list of rules to evaluate.
 */
public data class Policy(
    public val id: String,
    public val version: String,
    public val scope: String,
    public val rules: List<Rule>,
)
