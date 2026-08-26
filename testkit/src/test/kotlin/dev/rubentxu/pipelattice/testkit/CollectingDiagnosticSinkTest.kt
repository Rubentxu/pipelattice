package dev.rubentxu.pipelattice.testkit

import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticCode
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectingDiagnosticSinkTest {

    @Test
    fun `collects diagnostics in report order and is defensive at the boundary`() {
        val sink = CollectingDiagnosticSink()
        val first = error("REF-INVALID-001")
        val second = error("CONFIG-CONFLICT-023")

        sink.report(first)
        sink.report(second)
        second.copy(message = "mutated after reporting")

        assertEquals(listOf(first, second), sink.diagnostics)
        assertTrue(sink.hasErrors())
        assertEquals(setOf("REF-INVALID-001", "CONFIG-CONFLICT-023"), sink.codes())
    }

    @Test
    fun `empty sink reports no errors`() {
        val sink = CollectingDiagnosticSink()
        assertTrue(sink.diagnostics.isEmpty())
        assertTrue(!sink.hasErrors())
    }

    private fun error(code: String) = Diagnostic(
        code = DiagnosticCode(code),
        severity = DiagnosticSeverity.ERROR,
        message = "boom",
    )
}
