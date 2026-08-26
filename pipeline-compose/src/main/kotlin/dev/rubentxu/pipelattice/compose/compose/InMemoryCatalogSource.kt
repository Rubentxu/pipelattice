package dev.rubentxu.pipelattice.compose.compose

import dev.rubentxu.pipelattice.compose.diagnostics.CompositionDiagnosticCodes
import dev.rubentxu.pipelattice.compose.ports.CatalogSource
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
import dev.rubentxu.pipelattice.resource.SourceDocument

/**
 * In-memory implementation of [CatalogSource] for testing and simple use cases.
 *
 * Resolution order:
 * 1. Exact match on [ResourceRef]
 * 2. Alias match: `ref.copy(version = null)` — version-less fallback
 * 3. If neither found, reports [CompositionDiagnosticCodes.IMPORT_UNRESOLVED] and returns null
 */
internal class InMemoryCatalogSource(
    private val documents: Map<ResourceRef, SourceDocument>
) : CatalogSource {

    override fun resolve(ref: ResourceRef, sink: DiagnosticSink): SourceDocument? {
        // Step 1: exact match
        documents[ref]?.let { return it }

        // Step 2: alias match - find any document with matching path (ignoring version)
        // When ref has no version, find the first document whose path matches
        documents.entries.find { it.key.path == ref.path }?.let { return it.value }

        // Step 3: report unresolved
        sink.report(
            Diagnostic(
                code = CompositionDiagnosticCodes.IMPORT_UNRESOLVED,
                severity = DiagnosticSeverity.ERROR,
                message = "Unresolved import reference: ${ref.canonicalForm}",
                location = SourceLocation(path = "spec.imports")
            )
        )
        return null
    }
}
