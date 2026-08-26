package dev.rubentxu.pipelattice.foundation.diagnostics

/**
 * Port through which compilation phases report findings.
 *
 * Deliberately synchronous in M0: pure compiler phases are synchronous by design
 * (pipelattice-spec/docs/05_KOTLIN_2_4_ENGINEERING.md §11). Async sinks can adapt on top later.
 */
public fun interface DiagnosticSink {
    public fun report(diagnostic: Diagnostic)
}
