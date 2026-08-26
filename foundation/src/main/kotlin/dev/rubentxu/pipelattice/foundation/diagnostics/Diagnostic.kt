package dev.rubentxu.pipelattice.foundation.diagnostics

/** Severity of a [Diagnostic]; ordering is meaningful for build-failure decisions. */
public enum class DiagnosticSeverity {
    INFO,
    WARNING,
    ERROR,
}

/** Physical origin of a configuration element, as precise as the source format allows. */
public data class SourceLocation(
    public val path: String,
    public val line: Int? = null,
    public val column: Int? = null,
)

/**
 * A single compilation finding. Every new user-visible failure must produce a [Diagnostic]
 * with a stable [DiagnosticCode], a human message, an optional machine-readable location and
 * — when one exists — a remediation hint.
 */
public data class Diagnostic(
    public val code: DiagnosticCode,
    public val severity: DiagnosticSeverity,
    public val message: String,
    public val location: SourceLocation? = null,
    public val remediationHint: String? = null,
)
