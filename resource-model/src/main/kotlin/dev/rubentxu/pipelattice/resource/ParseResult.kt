package dev.rubentxu.pipelattice.resource

import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity

/**
 * Outcome of parsing one [SourceDocument]: either zero or more resources, or diagnostics —
 * never a half-typed guess. When any ERROR diagnostic is present, `resources` is empty.
 */
public data class ParseResult(
    public val resources: List<ParsedResource>,
    public val diagnostics: List<Diagnostic> = emptyList(),
) {
    public val hasErrors: Boolean
        get() = diagnostics.any { it.severity == DiagnosticSeverity.ERROR }

    public companion object {
        public fun failed(diagnostics: List<Diagnostic>): ParseResult =
            ParseResult(resources = emptyList(), diagnostics = diagnostics)
    }
}
