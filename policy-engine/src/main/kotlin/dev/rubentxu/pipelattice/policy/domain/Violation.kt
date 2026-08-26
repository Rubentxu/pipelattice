package dev.rubentxu.pipelattice.policy.domain

import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticCode

/**
 * Represents a policy rule violation detected during evaluation.
 *
 * @param code Stable diagnostic code identifying the violated rule.
 * @param severity How severe the violation is.
 * @param message Human-readable description of the violation.
 * @param sourceLocation Optional source location where the violation was detected.
 */
public data class Violation(
    public val code: DiagnosticCode,
    public val severity: Severity,
    public val message: String,
    public val sourceLocation: String? = null,
)
