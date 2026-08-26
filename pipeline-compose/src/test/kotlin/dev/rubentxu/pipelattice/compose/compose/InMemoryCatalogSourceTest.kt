package dev.rubentxu.pipelattice.compose.compose

import dev.rubentxu.pipelattice.compose.diagnostics.CompositionDiagnosticCodes
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
import dev.rubentxu.pipelattice.resource.SourceDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryCatalogSourceTest {

    private val reports = mutableListOf<Diagnostic>()
    private val sink = DiagnosticSink { diagnostic -> reports.add(diagnostic) }

    @Test
    fun `resolve returns document on exact match`() {
        // Given a catalog with a document
        val doc = SourceDocument("catalog://test/profile", "content: yaml")
        val ref = ResourceRef.parse("catalog://test/profile")
        val catalogSource = InMemoryCatalogSource(mapOf(ref to doc))

        // When resolving the exact reference
        val result = catalogSource.resolve(ref, sink)

        // Then result is the document with no diagnostics
        assertEquals(doc, result)
        assertEquals(0, reports.size)
    }

    @Test
    fun `resolve returns document on version alias hit`() {
        // Given a catalog with a versioned document
        val doc = SourceDocument("catalog://test/profile@stable", "content: yaml")
        val versionedRef = ResourceRef.parse("catalog://test/profile@stable")
        val catalogSource = InMemoryCatalogSource(mapOf(versionedRef to doc))

        // When resolving without version (alias semantic: ref.copy(version=null))
        val versionLessRef = ResourceRef.parse("catalog://test/profile")
        val result = catalogSource.resolve(versionLessRef, sink)

        // Then result is the document (version alias hit) with no diagnostics
        assertEquals(doc, result)
        assertEquals(0, reports.size)
    }

    @Test
    fun `resolve returns null and reports diagnostic on miss`() {
        // Given an empty catalog source
        val catalogSource = InMemoryCatalogSource(emptyMap())
        val ref = ResourceRef.parse("catalog://nonexistent/profile")

        // When resolving a non-existent reference
        val result = catalogSource.resolve(ref, sink)

        // Then result is null and diagnostic is reported
        assertNull(result)
        assertEquals(1, reports.size)
        val diagnostic = reports[0]
        assertEquals(CompositionDiagnosticCodes.IMPORT_UNRESOLVED, diagnostic.code)
        assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity)
        assertEquals(SourceLocation(path = "spec.imports"), diagnostic.location)
    }

    @Test
    fun `resolve tries exact match before alias`() {
        // Given a catalog with both versioned and unversioned refs
        val versionedDoc = SourceDocument("catalog://test/profile@stable", "versioned content")
        val unversionedDoc = SourceDocument("catalog://test/profile", "unversioned content")
        val versionedRef = ResourceRef.parse("catalog://test/profile@stable")
        val unversionedRef = ResourceRef.parse("catalog://test/profile")
        val catalogSource = InMemoryCatalogSource(mapOf(
            versionedRef to versionedDoc,
            unversionedRef to unversionedDoc
        ))

        // When resolving the versioned ref (should get versioned doc, not alias to unversioned)
        val result = catalogSource.resolve(versionedRef, sink)

        // Then result is the versioned document (exact match wins)
        assertEquals(versionedDoc, result)
        assertEquals(0, reports.size)
    }
}
