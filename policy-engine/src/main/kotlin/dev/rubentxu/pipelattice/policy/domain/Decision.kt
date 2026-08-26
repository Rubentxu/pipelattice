package dev.rubentxu.pipelattice.policy.domain

/**
 * Result of evaluating a policy rule against a resource.
 */
public enum class Decision {
    ALLOW,
    DENY,
    WARN,
}
