package dev.rubentxu.pipelattice.compose.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompositionDiagnosticCodesTest {

    private val codePattern = Regex("""[A-Z]+(-[A-Z0-9]+)*-[0-9]+""")

    @Test
    fun `IMPORT_CYCLE code matches CODE_PATTERN`() {
        assertTrue(
            codePattern.matches(CompositionDiagnosticCodes.IMPORT_CYCLE.value),
            "COMPOSE-IMPORT-001 must match <AREA>-<CONCERN>-<NNN>"
        )
    }

    @Test
    fun `IMPORT_CYCLE literal is COMPOSE-IMPORT-001`() {
        assertEquals("COMPOSE-IMPORT-001", CompositionDiagnosticCodes.IMPORT_CYCLE.value)
    }

    @Test
    fun `UNKNOWN_KIND code matches CODE_PATTERN`() {
        assertTrue(
            codePattern.matches(CompositionDiagnosticCodes.UNKNOWN_KIND.value),
            "COMPOSE-IMPORT-002 must match <AREA>-<CONCERN>-<NNN>"
        )
    }

    @Test
    fun `UNKNOWN_KIND literal is COMPOSE-IMPORT-002`() {
        assertEquals("COMPOSE-IMPORT-002", CompositionDiagnosticCodes.UNKNOWN_KIND.value)
    }

    @Test
    fun `MERGE_CONFLICT code matches CODE_PATTERN`() {
        assertTrue(
            codePattern.matches(CompositionDiagnosticCodes.MERGE_CONFLICT.value),
            "COMPOSE-MERGE-001 must match <AREA>-<CONCERN>-<NNN>"
        )
    }

    @Test
    fun `MERGE_CONFLICT literal is COMPOSE-MERGE-001`() {
        assertEquals("COMPOSE-MERGE-001", CompositionDiagnosticCodes.MERGE_CONFLICT.value)
    }
}
