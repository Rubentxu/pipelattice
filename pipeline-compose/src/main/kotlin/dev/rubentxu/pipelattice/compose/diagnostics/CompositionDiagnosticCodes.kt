package dev.rubentxu.pipelattice.compose.diagnostics

import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticCode

/**
 * Stable, machine-readable diagnostic codes for the composition pipeline.
 *
 * Convention: every public failure carries a stable code shaped `COMPOSE-<AREA>-<NNN>`.
 * Codes are part of the public contract and must never be renamed or reused for
 * different semantics.
 */
public object CompositionDiagnosticCodes {

    /** Import cycle detected or unresolved import reference. */
    public val IMPORT_CYCLE: DiagnosticCode = DiagnosticCode("COMPOSE-IMPORT-001")

    /** Unknown kind or unrecognized catalog reference. */
    public val UNKNOWN_KIND: DiagnosticCode = DiagnosticCode("COMPOSE-IMPORT-002")

    /** Merge conflict between two composition units or type mismatch during merge. */
    public val MERGE_CONFLICT: DiagnosticCode = DiagnosticCode("COMPOSE-MERGE-001")
}
