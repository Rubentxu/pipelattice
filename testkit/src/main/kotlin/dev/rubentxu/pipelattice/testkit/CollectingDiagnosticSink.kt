package dev.rubentxu.pipelattice.testkit

import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink

/**
 * Collecting [DiagnosticSink] for tests and CLI tooling: records every reported diagnostic
 * and exposes convenience queries over them.
 */
public class CollectingDiagnosticSink : DiagnosticSink {

    private val reported = mutableListOf<Diagnostic>()

    public val diagnostics: List<Diagnostic>
        get() = reported.toList()

    public val errors: List<Diagnostic>
        get() = reported.filter { it.severity == DiagnosticSeverity.ERROR }

    public fun hasErrors(): Boolean = errors.isNotEmpty()

    public fun codes(): Set<String> = diagnostics.map { it.code.value }.toSet()

    public override fun report(diagnostic: Diagnostic) {
        reported += diagnostic
    }
}
