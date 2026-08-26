package dev.rubentxu.pipelattice.foundation.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DiagnosticCodeTest {

    @Test
    fun `accepts codes following the area-concern-number convention`() {
        assertEquals(
            "CONFIG-CONFLICT-023",
            DiagnosticCode("CONFIG-CONFLICT-023").value,
        )
    }

    @Test
    fun `rejects malformed codes`() {
        for (malformed in listOf("lower-case-001", "NO-NUMBERS", "", "TRAILING-", "SPACES IN-CODE-01")) {
            assertFailsWith<IllegalArgumentException>("should reject '$malformed'") {
                DiagnosticCode(malformed)
            }
        }
    }
}

class DiagnosticTest {

    @Test
    fun `carries code severity message location and remediation`() {
        val diagnostic = Diagnostic(
            code = DiagnosticCode("CONFIG-GOVERNANCE-004"),
            severity = DiagnosticSeverity.ERROR,
            message = "timeout 120m violates guardrail max 60m",
            location = SourceLocation(path = "pipeline.yaml", line = 12, column = 5),
            remediationHint = "reduce timeout or request an exception via platform review",
        )

        assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity)
        assertEquals(12, diagnostic.location?.line)
        assertTrue(diagnostic.remediationHint != null)
    }
}
