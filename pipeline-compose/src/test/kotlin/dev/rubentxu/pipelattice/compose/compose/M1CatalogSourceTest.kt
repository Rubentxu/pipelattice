package dev.rubentxu.pipelattice.compose.compose

import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticCode
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.resource.ParseResult
import dev.rubentxu.pipelattice.resource.ResourceParser
import dev.rubentxu.pipelattice.resource.SourceDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class M1CatalogSourceTest {

    private val reports = mutableListOf<Diagnostic>()
    private val sink = DiagnosticSink { diagnostic -> reports.add(diagnostic) }

    @Test
    fun `resolve returns document when found in map regardless of parser result`() {
        // Given a parser that produces a successful parse result
        val parser = ResourceParser { doc ->
            ParseResult(resources = emptyList())
        }
        val doc = SourceDocument("catalog://test/profile", "yaml content")
        val ref = ResourceRef.parse("catalog://test/profile")
        val catalogSource = M1CatalogSource(parser, mapOf(ref to doc))

        // When resolving the reference
        val result = catalogSource.resolve(ref, sink)

        // Then result is the original document
        assertEquals(doc, result)
        assertEquals(0, reports.size)
    }

    @Test
    fun `resolve returns null and propagates diagnostics when parser has errors`() {
        // Given a parser that returns errors
        val errorDiagnostic = Diagnostic(
            code = DiagnosticCode("TEST-ERROR-001"),
            severity = DiagnosticSeverity.ERROR,
            message = "Parse error"
        )
        val parser = ResourceParser { _ ->
            ParseResult(
                resources = emptyList(),
                diagnostics = listOf(errorDiagnostic)
            )
        }
        val doc = SourceDocument("catalog://test/bad", "bad content")
        val ref = ResourceRef.parse("catalog://test/bad")
        val catalogSource = M1CatalogSource(parser, mapOf(ref to doc))

        // When resolving the reference
        val result = catalogSource.resolve(ref, sink)

        // Then result is null and diagnostics are propagated to sink
        assertNull(result)
        assertEquals(1, reports.size)
        assertEquals(errorDiagnostic, reports[0])
    }

    @Test
    fun `resolve returns null when reference not found in catalog`() {
        // Given a parser and an empty catalog
        val parser = ResourceParser { _ ->
            ParseResult(resources = emptyList())
        }
        val catalogSource = M1CatalogSource(parser, emptyMap())
        val ref = ResourceRef.parse("catalog://nonexistent/profile")

        // When resolving a non-existent reference
        val result = catalogSource.resolve(ref, sink)

        // Then result is null with no diagnostics propagated (nothing to parse)
        assertNull(result)
        assertEquals(0, reports.size)
    }
}
