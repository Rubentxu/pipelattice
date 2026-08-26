package dev.rubentxu.pipelattice.compose.compose

import dev.rubentxu.pipelattice.compose.diagnostics.CompositionDiagnosticCodes
import dev.rubentxu.pipelattice.compose.ports.CatalogSource
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
import dev.rubentxu.pipelattice.resource.ParseResult
import dev.rubentxu.pipelattice.resource.ParsedResource
import dev.rubentxu.pipelattice.resource.PipelineProfileResource
import dev.rubentxu.pipelattice.resource.ResourceParser

/**
 * Internal resolver for import chains with cycle detection and depth limiting.
 *
 * Resolves a starting profile reference to a flat list of all reachable profiles
 * by following import references in depth-first order.
 *
 * @param maxDepth Maximum import chain depth before triggering IMPORT-001 (default: 8).
 *                The chain includes the starting profile, so depth=8 means 8 profiles in the chain.
 * @param parser The resource parser for converting source documents to parsed resources.
 */
internal class ImportResolver(
    private val maxDepth: Int = 8,
    private val parser: ResourceParser,
) {

    /**
     * Result of resolving an import chain, containing the collected resources and cycle info.
     */
    internal data class ResolveResult(
        val resources: List<ParsedResource>,
        val cycleChain: List<String> = emptyList(),
        val hitMaxDepth: Boolean = false,
    )

    /**
     * Resolves a starting profile reference to a list of all reachable profiles.
     *
     * Uses DFS with visited tracking to detect cycles. If a cycle is detected,
     * reports COMPOSE-IMPORT-001 with the cycle chain. If maxDepth is exceeded,
     * also reports COMPOSE-IMPORT-001.
     *
     * @param startRef The starting profile reference.
     * @param catalog The catalog source for resolving references.
     * @param sink Diagnostic sink for reporting import errors.
     * @return List of parsed resources in the import chain (root to leaf order).
     */
    fun resolve(
        startRef: ResourceRef,
        catalog: CatalogSource,
        sink: DiagnosticSink,
    ): List<ParsedResource> {
        val visited = mutableSetOf<String>()
        val result = mutableListOf<ParsedResource>()
        val cycleChain = mutableListOf<String>()

        resolveRecursive(startRef, catalog, sink, visited, result, cycleChain, depth = 0)

        return result
    }

    private fun resolveRecursive(
        ref: ResourceRef,
        catalog: CatalogSource,
        sink: DiagnosticSink,
        visited: MutableSet<String>,
        result: MutableList<ParsedResource>,
        cycleChain: MutableList<String>,
        depth: Int,
    ) {
        val path = ref.path

        // Check depth limit - maxDepth=8 allows depths 0-8, triggers at depth 9
        if (depth >= maxDepth + 1) {
            sink.report(
                Diagnostic(
                    code = CompositionDiagnosticCodes.IMPORT_CYCLE,
                    severity = DiagnosticSeverity.ERROR,
                    message = "Import chain exceeds maximum depth of $maxDepth at '$path'. " +
                            "Chain: ${(cycleChain + path).joinToString(" -> ")}",
                    location = SourceLocation(path = path)
                )
            )
            return
        }

        // Check for cycle (already visited)
        if (path in visited) {
            val chain = cycleChain + path
            sink.report(
                Diagnostic(
                    code = CompositionDiagnosticCodes.IMPORT_CYCLE,
                    severity = DiagnosticSeverity.ERROR,
                    message = "Import cycle detected: ${chain.joinToString(" -> ")}",
                    location = SourceLocation(path = path)
                )
            )
            return
        }

        // Mark as visited and add to chain
        visited.add(path)
        cycleChain.add(path)

        // Resolve the document from catalog
        val sourceDoc = catalog.resolve(ref, sink) ?: run {
            // Catalog already reported the diagnostic, just stop processing this branch
            cycleChain.removeLast()
            visited.remove(path)
            return
        }

        // Parse the document
        val parseResult = parser.parse(sourceDoc)

        if (parseResult.hasErrors) {
            parseResult.diagnostics.forEach { sink.report(it) }
            cycleChain.removeLast()
            visited.remove(path)
            return
        }

        // Add all parsed resources to result
        // Typically this will be 1 resource, but handle multiple for robustness
        for (resource in parseResult.resources) {
            result.add(resource)

            // If this is a PipelineProfileResource, recursively resolve its imports
            if (resource is PipelineProfileResource) {
                for (importRef in resource.spec.imports) {
                    resolveRecursive(importRef, catalog, sink, visited, result, cycleChain, depth + 1)
                }
            }
        }

        // Backtrack
        cycleChain.removeLast()
        visited.remove(path)
    }
}
