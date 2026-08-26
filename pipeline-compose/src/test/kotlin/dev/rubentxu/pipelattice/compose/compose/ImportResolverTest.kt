package dev.rubentxu.pipelattice.compose.compose

import dev.rubentxu.pipelattice.compose.diagnostics.CompositionDiagnosticCodes
import dev.rubentxu.pipelattice.compose.ports.CatalogSource
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
import dev.rubentxu.pipelattice.resource.ApiVersion
import dev.rubentxu.pipelattice.resource.Metadata
import dev.rubentxu.pipelattice.resource.ParseResult
import dev.rubentxu.pipelattice.resource.ParsedResource
import dev.rubentxu.pipelattice.resource.PipelineProfileResource
import dev.rubentxu.pipelattice.resource.PipelineProfileSpec
import dev.rubentxu.pipelattice.resource.ResourceParser
import dev.rubentxu.pipelattice.resource.SourceDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [ImportResolver.resolve].
 *
 * Test cases:
 * 1. Happy path: single profile (no imports)
 * 2. Happy path: two profiles in chain (A imports B)
 * 3. Cycle: A imports B imports A
 * 4. Self-reference cycle: A imports A
 * 5. Max depth: depth 9 triggers IMPORT-001 (maxDepth=8)
 * 6. Missing reference: unresolved ref skipped with diagnostic
 */
class ImportResolverTest {

    private val API = ApiVersion.KNOWN

    private fun profile(name: String, vararg imports: ResourceRef): PipelineProfileResource {
        return PipelineProfileResource(
            apiVersion = API,
            metadata = Metadata(name = name),
            spec = PipelineProfileSpec(
                imports = imports.toList(),
                parameters = emptyMap(),
            )
        )
    }

    private fun collectingSink(): Pair<MutableList<Diagnostic>, DiagnosticSink> {
        val reports = mutableListOf<Diagnostic>()
        val sink = DiagnosticSink { reports.add(it) }
        return reports to sink
    }

    /**
     * Mock ResourceParser that returns a parsed resource based on the document path.
     */
    private class MockParser(private val profiles: Map<String, PipelineProfileResource>) : ResourceParser {
        override fun parse(document: SourceDocument): ParseResult {
            val profile = profiles[document.path]
            return if (profile != null) {
                ParseResult(resources = listOf(profile))
            } else {
                ParseResult.failed(
                    listOf(
                        Diagnostic(
                            code = CompositionDiagnosticCodes.IMPORT_UNRESOLVED,
                            severity = dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity.ERROR,
                            message = "Unresolved import: ${document.path}",
                            location = SourceLocation(path = "spec.imports")
                        )
                    )
                )
            }
        }
    }

    /**
     * Mock CatalogSource that returns SourceDocument for known paths.
     * Reports IMPORT_UNRESOLVED when a path is not found.
     */
    private class MockCatalogSource(private val paths: Set<String>) : CatalogSource {
        override fun resolve(ref: ResourceRef, sink: DiagnosticSink): SourceDocument? {
            return if (ref.path in paths) {
                SourceDocument(ref.path, "")  // Use path, not canonicalForm
            } else {
                sink.report(
                    Diagnostic(
                        code = CompositionDiagnosticCodes.IMPORT_UNRESOLVED,
                        severity = dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity.ERROR,
                        message = "Unresolved import reference: ${ref.canonicalForm}",
                        location = SourceLocation(path = "spec.imports")
                    )
                )
                null
            }
        }
    }

    // --- Case 1: Happy single profile (no imports) ---

    @Test
    fun `resolve returns single profile with no imports`() {
        val profileA = profile("a")
        val pathsA = setOf("profiles/a")
        val catalog = MockCatalogSource(pathsA)
        val parser = MockParser(mapOf("profiles/a" to profileA))
        val resolver = ImportResolver(parser = parser)
        val (reports, sink) = collectingSink()

        val startRef = ResourceRef.parse("catalog://profiles/a")
        val result = resolver.resolve(startRef, catalog, sink)

        assertEquals(1, result.size)
        assertEquals("a", result[0].metadata.name)
        assertTrue(reports.isEmpty())
    }

    // --- Case 2: Happy 2-chain transitive (A imports B) ---

    @Test
    fun `resolve returns two profiles in chain A imports B`() {
        val profileA = profile("a", ResourceRef.parse("catalog://profiles/b"))
        val profileB = profile("b")
        val pathsAB = setOf("profiles/a", "profiles/b")
        val catalog = MockCatalogSource(pathsAB)
        val parser = MockParser(mapOf(
            "profiles/a" to profileA,
            "profiles/b" to profileB
        ))
        val resolver = ImportResolver(parser = parser)
        val (reports, sink) = collectingSink()

        val startRef = ResourceRef.parse("catalog://profiles/a")
        val result = resolver.resolve(startRef, catalog, sink)

        assertEquals(2, result.size)
        assertEquals("a", result[0].metadata.name)
        assertEquals("b", result[1].metadata.name)
        assertTrue(reports.isEmpty())
    }

    // --- Case 3: Cycle A imports B imports A ---

    @Test
    fun `resolve detects cycle A imports B imports A and reports IMPORT-001`() {
        val profileA = profile("a", ResourceRef.parse("catalog://profiles/b"))
        val profileB = profile("b", ResourceRef.parse("catalog://profiles/a"))
        val pathsAB = setOf("profiles/a", "profiles/b")
        val catalog = MockCatalogSource(pathsAB)
        val parser = MockParser(mapOf(
            "profiles/a" to profileA,
            "profiles/b" to profileB
        ))
        val resolver = ImportResolver(parser = parser)
        val (reports, sink) = collectingSink()

        val startRef = ResourceRef.parse("catalog://profiles/a")
        val result = resolver.resolve(startRef, catalog, sink)

        // Should have A and B before detecting cycle
        assertTrue(result.any { it.metadata.name == "a" })
        assertTrue(result.any { it.metadata.name == "b" })
        assertTrue(reports.isNotEmpty())
        val import001 = reports.find { it.code == CompositionDiagnosticCodes.IMPORT_CYCLE }
        assertTrue(import001 != null, "Expected IMPORT-001 diagnostic")
        assertTrue(import001.message.contains("profiles/a") || import001.message.contains("profiles/b"),
            "Cycle chain should reference involved paths")
    }

    // --- Case 4: Self-reference cycle (A imports A) ---

    @Test
    fun `resolve detects self-reference cycle and reports IMPORT-001`() {
        val selfRef = ResourceRef.parse("catalog://profiles/a")
        val profileA = profile("a", selfRef)
        val pathsA = setOf("profiles/a")
        val catalog = MockCatalogSource(pathsA)
        val parser = MockParser(mapOf("profiles/a" to profileA))
        val resolver = ImportResolver(parser = parser)
        val (reports, sink) = collectingSink()

        val startRef = ResourceRef.parse("catalog://profiles/a")
        val result = resolver.resolve(startRef, catalog, sink)

        assertTrue(result.isNotEmpty())
        assertTrue(reports.any { it.code == CompositionDiagnosticCodes.IMPORT_CYCLE },
            "Expected IMPORT-001 for self-reference cycle")
    }

    // --- Case 5: Max depth exceeded at depth 9 ---

    @Test
    fun `resolve triggers IMPORT-001 when maxDepth exceeded at depth 9`() {
        // Create a chain A0 -> A1 -> A2 -> ... -> A8 -> A9 (10 profiles)
        // Default maxDepth is 8, so depth 9 (A9) should trigger IMPORT-001
        val profiles = mutableMapOf<String, PipelineProfileResource>()
        val paths = mutableSetOf<String>()

        // Create A0 through A9 where A9 exists but should not be processed due to maxDepth
        for (i in 0..9) {
            val nextRef = if (i < 9) ResourceRef.parse("catalog://profiles/a${i + 1}") else null
            val profile = PipelineProfileResource(
                apiVersion = API,
                metadata = Metadata(name = "a$i"),
                spec = PipelineProfileSpec(
                    imports = if (nextRef != null) listOf(nextRef) else emptyList(),
                    parameters = emptyMap(),
                )
            )
            profiles["profiles/a$i"] = profile
            paths += "profiles/a$i"
        }

        val catalog = MockCatalogSource(paths)
        val parser = MockParser(profiles)
        val resolver = ImportResolver(parser = parser) // maxDepth = 8 by default
        val (reports, sink) = collectingSink()

        val startRef = ResourceRef.parse("catalog://profiles/a0")
        val result = resolver.resolve(startRef, catalog, sink)

        // Should have A0 through A8 (9 profiles) because A9 at depth 9 triggers max depth
        assertEquals(9, result.size)
        assertTrue(reports.any { it.code == CompositionDiagnosticCodes.IMPORT_CYCLE },
            "Expected IMPORT-001 for max depth exceeded. Got codes: ${reports.map { it.code.value }}")
    }

    // --- Case 6: Missing reference reports diagnostic but continues ---

    @Test
    fun `resolve skips missing reference and reports IMPORT-003`() {
        val profileA = profile("a", ResourceRef.parse("catalog://profiles/b"))
        val pathsA = setOf("profiles/a") // B is missing
        val catalog = MockCatalogSource(pathsA)
        val parser = MockParser(mapOf("profiles/a" to profileA))
        val resolver = ImportResolver(parser = parser)
        val (reports, sink) = collectingSink()

        val startRef = ResourceRef.parse("catalog://profiles/a")
        val result = resolver.resolve(startRef, catalog, sink)

        assertEquals(1, result.size)
        assertTrue(reports.any { it.code == CompositionDiagnosticCodes.IMPORT_UNRESOLVED })
    }
}
