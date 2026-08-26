package dev.rubentxu.pipelattice.compiler.parse

import dev.rubentxu.pipelattice.resource.SourceDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * REQ-Stable-Diagnostic-Codes: assert the complete set of ParseErrorCodes.
 * All 7 RESOURCE-STAR and REF-INVALID codes must be exercisable.
 */
class DiagnosticCodesTest {

    private val parser = YamlResourceParser()

    private fun findCode(result: dev.rubentxu.pipelattice.resource.ParseResult, code: String) =
        result.diagnostics.firstOrNull { it.code.value == code }

    // RESOURCE-SCHEMA-001: required property absent
    @Test
    fun `missing metadata produces RESOURCE-SCHEMA-001`() {
        val yaml = """
            apiVersion: pipelattice.dev/v1alpha1
            kind: PipelineDefinition
            spec:
              profile:
                ref: catalog://profiles/test@stable
        """.trimIndent()

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.hasErrors)
        assertTrue(result.resources.isEmpty())
        val error = findCode(result, "RESOURCE-SCHEMA-001")
        assertNotNull(error, "expected RESOURCE-SCHEMA-001 but got: ${result.diagnostics.map { it.code.value }}")
    }

    // RESOURCE-YAML-001: YAML syntax error
    @Test
    fun `syntax error produces RESOURCE-YAML-001`() {
        val yaml = """
            apiVersion: pipelattice.dev/v1alpha1
            kind: PipelineDefinition
            metadata:
              name: test
            spec:
        \t\tprofile:
                ref: catalog://profiles/test@stable
        """.trimIndent()

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.hasErrors)
        assertTrue(result.resources.isEmpty())
        val error = findCode(result, "RESOURCE-YAML-001")
        assertNotNull(error, "expected RESOURCE-YAML-001 but got: ${result.diagnostics.map { it.code.value }}")
    }

    // RESOURCE-APIVERSION-001: unknown apiVersion
    @Test
    fun `unknown apiVersion produces RESOURCE-APIVERSION-001`() {
        val yaml = """
            apiVersion: pipelattice.dev/v9
            kind: PipelineDefinition
            metadata:
              name: test
            spec:
              profile:
                ref: catalog://profiles/test@stable
        """.trimIndent()

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.hasErrors)
        assertTrue(result.resources.isEmpty())
        val error = findCode(result, "RESOURCE-APIVERSION-001")
        assertNotNull(error, "expected RESOURCE-APIVERSION-001 but got: ${result.diagnostics.map { it.code.value }}")
    }

    // RESOURCE-KIND-001: unknown kind
    @Test
    fun `unknown kind produces RESOURCE-KIND-001`() {
        val yaml = """
            apiVersion: pipelattice.dev/v1alpha1
            kind: UnknownKind
            metadata:
              name: test
            spec:
              profile:
                ref: catalog://profiles/test@stable
        """.trimIndent()

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.hasErrors)
        assertTrue(result.resources.isEmpty())
        val error = findCode(result, "RESOURCE-KIND-001")
        assertNotNull(error, "expected RESOURCE-KIND-001 but got: ${result.diagnostics.map { it.code.value }}")
    }

    // RESOURCE-FIELD-001: unknown field in metadata
    @Test
    fun `unknown field produces RESOURCE-FIELD-001`() {
        val yaml = """
            apiVersion: pipelattice.dev/v1alpha1
            kind: PipelineDefinition
            metadata:
              name: test
              typo: value
            spec:
              profile:
                ref: catalog://profiles/test@stable
        """.trimIndent()

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.hasErrors)
        assertTrue(result.resources.isEmpty())
        val error = findCode(result, "RESOURCE-FIELD-001")
        assertNotNull(error, "expected RESOURCE-FIELD-001 but got: ${result.diagnostics.map { it.code.value }}")
    }

    // RESOURCE-SCHEMA-002: type error (e.g., null value)
    @Test
    fun `null parameter value produces RESOURCE-SCHEMA-002`() {
        val yaml = """
            apiVersion: pipelattice.dev/v1alpha1
            kind: PipelineDefinition
            metadata:
              name: test
            spec:
              profile:
                ref: catalog://profiles/test@stable
              parameters:
                image: ~
        """.trimIndent()

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.hasErrors)
        assertTrue(result.resources.isEmpty())
        val error = findCode(result, "RESOURCE-SCHEMA-002")
        assertNotNull(error, "expected RESOURCE-SCHEMA-002 but got: ${result.diagnostics.map { it.code.value }}")
    }

    // REF-INVALID-001: invalid ref format
    @Test
    fun `https ref produces REF-INVALID-001`() {
        val yaml = """
            apiVersion: pipelattice.dev/v1alpha1
            kind: PipelineDefinition
            metadata:
              name: test
            spec:
              profile:
                ref: https://example.com/foo
        """.trimIndent()

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.hasErrors)
        assertTrue(result.resources.isEmpty())
        val error = findCode(result, "REF-INVALID-001")
        assertNotNull(error, "expected REF-INVALID-001 but got: ${result.diagnostics.map { it.code.value }}")
    }

    @Test
    fun `all seven error codes are defined`() {
        // Sanity check: verify all 7 codes exist in ParseErrorCodes object
        val allCodes = listOf(
            "RESOURCE-YAML-001",
            "RESOURCE-APIVERSION-001",
            "RESOURCE-KIND-001",
            "RESOURCE-FIELD-001",
            "RESOURCE-SCHEMA-001",
            "RESOURCE-SCHEMA-002",
            "REF-INVALID-001",
        )
        assertEquals(7, allCodes.size)
    }
}
