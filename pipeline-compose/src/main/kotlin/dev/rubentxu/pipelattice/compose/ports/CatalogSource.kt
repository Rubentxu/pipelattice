package dev.rubentxu.pipelattice.compose.ports

import dev.rubentxu.pipelattice.compose.diagnostics.CompositionDiagnosticCodes
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.resource.SourceDocument

/**
 * Outbound port for resolving catalog resource references to source documents.
 *
 * Contract:
 * - Returns the [SourceDocument] if the [ref] is found in the catalog.
 * - Returns `null` and reports [CompositionDiagnosticCodes.UNKNOWN_KIND] (IMPORT-002) if the
 *   reference cannot be resolved.
 * - Alias resolution: when a versioned reference is not found, the sink attempts resolution with
 *   `ref.copy(version = null)` as a fallback before reporting missing.
 *
 * This is a public API port (not internal) as it forms part of the composition pipeline's
 * external contract.
 */
public fun interface CatalogSource {

    /**
     * Resolves a catalog resource reference to a source document.
     *
     * @param ref The resource reference to resolve.
     * @param sink Diagnostic sink for reporting resolution failures.
     * @return The [SourceDocument] if found, or `null` if not found (in which case a diagnostic
     *         is reported to [sink]).
     */
    public fun resolve(ref: ResourceRef, sink: DiagnosticSink): SourceDocument?
}
