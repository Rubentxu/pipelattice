package dev.rubentxu.pipelattice.compose.compose

import dev.rubentxu.pipelattice.compose.ports.CatalogSource
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.resource.ResourceParser
import dev.rubentxu.pipelattice.resource.SourceDocument

/**
 * M1-backed implementation of [CatalogSource] that delegates parsing to [ResourceParser].
 *
 * Resolution:
 * 1. Looks up the [SourceDocument] in the internal map by [ResourceRef].
 * 2. If found, delegates to [parser.parse] to validate/proocees the document.
 * 3. If parse has errors (i.e., [dev.rubentxu.pipelattice.resource.ParseResult.hasErrors]),
 *    propagates diagnostics verbatim to [sink] and returns null.
 * 4. If not found in map, returns null with no diagnostics.
 *
 * This adapter respects FARCH-011: no direct imports of format libraries (SnakeYAML, kaml, etc.).
 * All parsing is mediated through the [ResourceParser] port.
 *
 * @param parser The resource parser adapter for processing source documents.
 * @param documents The in-memory catalog of source documents indexed by [ResourceRef].
 */
internal class M1CatalogSource(
    private val parser: ResourceParser,
    private val documents: Map<ResourceRef, SourceDocument>
) : CatalogSource {

    override fun resolve(ref: ResourceRef, sink: DiagnosticSink): SourceDocument? {
        // Step 1: look up document in catalog
        val doc = documents[ref] ?: return null

        // Step 2: delegate to parser for validation/processing
        val parseResult = parser.parse(doc)

        // Step 3: if parse has errors, propagate diagnostics and return null
        if (parseResult.hasErrors) {
            parseResult.diagnostics.forEach { diagnostic -> sink.report(diagnostic) }
            return null
        }

        // Step 4: return the document if parse succeeded
        return doc
    }
}
