package dev.rubentxu.pipelattice.compiler.parse

import dev.rubentxu.pipelattice.resource.PipelineDefinitionResource
import dev.rubentxu.pipelattice.resource.PipelineProfileResource
import dev.rubentxu.pipelattice.resource.SourceDocument
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for YamlResourceParser — covers all M1 REQ scenarios.
 * Each scenario maps to a specific requirement from the spec.
 */
class YamlResourceParserTest {

    private val parser = YamlResourceParser()

    // -------------------------------------------------------------------------
    // REQ: Resource-Envelope-Per-Kind — happy paths
    // -------------------------------------------------------------------------

    @Test
    fun `PipelineDefinition happy path`() {
        val yaml = """
            apiVersion: pipelattice.dev/v1alpha1
            kind: PipelineDefinition
            metadata:
              name: payments-api
            spec:
              profile:
                ref: catalog://profiles/java-maven-container@stable
              parameters:
                javaVersion: 21
        """.trimIndent()

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.diagnostics.isEmpty(), "expected no diagnostics but got: ${result.diagnostics}")
        assertEquals(1, result.resources.size)

        val resource = result.resources[0] as PipelineDefinitionResource
        assertEquals("payments-api", resource.metadata.name)
        assertEquals("pipelattice.dev/v1alpha1", resource.apiVersion.value)
        assertNotNull(resource.spec.profile)
        assertEquals("catalog://profiles/java-maven-container@stable", resource.spec.profile!!.canonicalForm)
        assertEquals(21L, (resource.spec.parameters["javaVersion"] as dev.rubentxu.pipelattice.resource.ParameterValue.IntValue).value)
    }

    @Test
    fun `PipelineProfile happy path`() {
        val yaml = """
            apiVersion: pipelattice.dev/v1alpha1
            kind: PipelineProfile
            metadata:
              name: java-maven-container
              version: 4.3.0
            spec:
              imports:
                - ref: catalog://company/base@3
              parameters:
                javaVersion:
                  type: integer
                  default: 21
              workflow:
                ref: catalog://workflows/build-container@3
        """.trimIndent()

        val result = parser.parse(SourceDocument("profile.yaml", yaml))

        assertTrue(result.diagnostics.isEmpty(), "expected no diagnostics but got: ${result.diagnostics}")
        assertEquals(1, result.resources.size)

        val resource = result.resources[0] as PipelineProfileResource
        assertEquals("java-maven-container", resource.metadata.name)
        assertEquals("4.3.0", resource.metadata.version)
        assertEquals(1, resource.spec.imports.size)
        assertEquals("catalog://company/base@3", resource.spec.imports[0].canonicalForm)
    }

    // -------------------------------------------------------------------------
    // REQ: Canonical-ResourceRef — malformed ref rejection
    // -------------------------------------------------------------------------

    @Test
    fun `https ref rejected with REF-INVALID-001`() {
        val yaml = """
            apiVersion: pipelattice.dev/v1alpha1
            kind: PipelineDefinition
            metadata:
              name: test
            spec:
              profile:
                ref: https://example.com/foo
        """.trimIndent()

        val result = parser.parse(SourceDocument("doc.yaml", yaml))

        assertTrue(result.hasErrors)
        assertTrue(result.resources.isEmpty())

        val error = result.diagnostics.firstOrNull { it.code.value == "REF-INVALID-001" }
        assertNotNull(error, "expected REF-INVALID-001 but got: ${result.diagnostics.map { it.code.value }}")
        assertContains(error.message, "catalog://")
        val loc = error.location
        assertNotNull(loc)
        assertEquals("doc.yaml", loc.path)
    }

    // -------------------------------------------------------------------------
    // REQ: Typed-Parameter-Values — scalars accepted
    // -------------------------------------------------------------------------

    @Test
    fun `typed scalars int bool string resolved correctly`() {
        val yaml = """
            apiVersion: pipelattice.dev/v1alpha1
            kind: PipelineDefinition
            metadata:
              name: test
            spec:
              profile:
                ref: catalog://profiles/test@stable
              parameters:
                intParam: 42
                boolParam: true
                strParam: hello-world
        """.trimIndent()

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.diagnostics.isEmpty(), "expected no diagnostics: ${result.diagnostics}")
        val params = (result.resources[0] as PipelineDefinitionResource).spec.parameters
        assertEquals(dev.rubentxu.pipelattice.resource.ParameterValue.IntValue(42L), params["intParam"])
        assertEquals(dev.rubentxu.pipelattice.resource.ParameterValue.BoolValue(true), params["boolParam"])
        assertEquals(dev.rubentxu.pipelattice.resource.ParameterValue.StringValue("hello-world"), params["strParam"])
    }

    @Test
    fun `null parameter value rejected with RESOURCE-SCHEMA-002`() {
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
        val error = result.diagnostics.firstOrNull { it.code.value == "RESOURCE-SCHEMA-002" }
        assertNotNull(error, "expected RESOURCE-SCHEMA-002 but got: ${result.diagnostics.map { it.code.value }}")
        assertContains(error.message, "must not be null")
    }

    @Test
    fun `mapping parameter value rejected with RESOURCE-SCHEMA-002`() {
        val yaml = """
            apiVersion: pipelattice.dev/v1alpha1
            kind: PipelineDefinition
            metadata:
              name: test
            spec:
              profile:
                ref: catalog://profiles/test@stable
              parameters:
                image:
                  repo: ghcr.io/x
        """.trimIndent()

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.hasErrors)
        assertTrue(result.resources.isEmpty())
        val error = result.diagnostics.firstOrNull { it.code.value == "RESOURCE-SCHEMA-002" }
        assertNotNull(error, "expected RESOURCE-SCHEMA-002 but got: ${result.diagnostics.map { it.code.value }}")
        assertContains(error.message, "must be a scalar")
    }

    // -------------------------------------------------------------------------
    // REQ: Governance-Modes-And-Constraints — guardrail + mandatory
    // -------------------------------------------------------------------------

    @Test
    fun `guardrail with constraints accepted`() {
        val yaml = """
            apiVersion: pipelattice.dev/v1alpha1
            kind: PipelineProfile
            metadata:
              name: test-profile
            spec:
              parameters:
                javaVersion:
                  type: integer
                  default: 21
                  governance:
                    mode: guardrail
                    constraints:
                      min: 17
                      max: 26
        """.trimIndent()

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.diagnostics.isEmpty(), "expected no diagnostics but got: ${result.diagnostics}")
        val params = (result.resources[0] as PipelineProfileResource).spec.parameters
        val javaVersion = params["javaVersion"]!!
        assertEquals(dev.rubentxu.pipelattice.resource.GovernanceMode.GUARDRAIL, javaVersion.governance.mode)
        assertEquals(17L, javaVersion.governance.constraints!!.min)
        assertEquals(26L, javaVersion.governance.constraints!!.max)
    }

    @Test
    fun `mandatory with constraints rejected with RESOURCE-SCHEMA-002`() {
        val yaml = """
            apiVersion: pipelattice.dev/v1alpha1
            kind: PipelineProfile
            metadata:
              name: test-profile
            spec:
              parameters:
                javaVersion:
                  type: integer
                  default: 21
                  governance:
                    mode: mandatory
                    constraints:
                      min: 17
        """.trimIndent()

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.hasErrors)
        assertTrue(result.resources.isEmpty())
        val error = result.diagnostics.firstOrNull { it.code.value == "RESOURCE-SCHEMA-002" }
        assertNotNull(error, "expected RESOURCE-SCHEMA-002 but got: ${result.diagnostics.map { it.code.value }}")
        assertContains(error.message, "constraints is only valid with mode 'guardrail'")
    }

    // -------------------------------------------------------------------------
    // REQ: Unknown-Field-Rejection — metadata typo
    // -------------------------------------------------------------------------

    @Test
    fun `metadata namme typo rejected with RESOURCE-FIELD-001`() {
        val yaml = """
            apiVersion: pipelattice.dev/v1alpha1
            kind: PipelineDefinition
            metadata:
              name: test
              namme: typo-value
            spec:
              profile:
                ref: catalog://profiles/test@stable
        """.trimIndent()

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.hasErrors)
        assertTrue(result.resources.isEmpty())
        val error = result.diagnostics.firstOrNull { it.code.value == "RESOURCE-FIELD-001" }
        assertNotNull(error, "expected RESOURCE-FIELD-001 but got: ${result.diagnostics.map { it.code.value }}")
        assertContains(error.message, "namme")
        assertContains(error.message, "metadata")
        val hint = error.remediationHint
        assertNotNull(hint)
        assertContains(hint, "name")
        assertContains(hint, "version")
        assertContains(hint, "labels")
    }

    // -------------------------------------------------------------------------
    // REQ: Stable-Diagnostic-Codes — apiVersion rejection
    // -------------------------------------------------------------------------

    @Test
    fun `unknown apiVersion rejected with RESOURCE-APIVERSION-001`() {
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
        val error = result.diagnostics.firstOrNull { it.code.value == "RESOURCE-APIVERSION-001" }
        assertNotNull(error, "expected RESOURCE-APIVERSION-001 but got: ${result.diagnostics.map { it.code.value }}")
        assertContains(error.message, "v9")
        assertContains(error.message, "unsupported")
    }

    // -------------------------------------------------------------------------
    // REQ: Syntax-Errors-With-Location — YAML syntax errors
    // -------------------------------------------------------------------------

    @Test
    fun `tab indent produces RESOURCE-YAML-001 with line 6`() {
        // Genuinely invalid YAML: tab character used for indentation (YAML 1.1 §4.3 forbids tabs).
        // SnakeYAML Engine v2 throws MarkedYamlEngineException; problemMark.line+1 = 6 (1-based).
        val yaml = """
            apiVersion: pipelattice.dev/v1alpha1
            kind: PipelineDefinition
            metadata:
              name: bad-indent-example
            spec:
        \t\tprofile:
                ref: catalog://profiles/test@stable
        """.trimIndent()

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.hasErrors, "expected parse errors but got none: ${result.diagnostics}")
        assertTrue(result.resources.isEmpty())
        val error = result.diagnostics.firstOrNull { it.code.value == "RESOURCE-YAML-001" }
        assertNotNull(error, "expected RESOURCE-YAML-001 but got: ${result.diagnostics.map { it.code.value }}")
        val loc = error.location
        assertNotNull(loc, "expected location to be present")
        // Tab on line 7 causes SnakeYAML to report the error at the enclosing
        // block context Mark: line 6 (0-based=5 → 1-based=6).
        assertEquals(6, loc.line)
    }

    @Test
    fun `empty document produces RESOURCE-YAML-001 with null location`() {
        val result = parser.parse(SourceDocument("empty.yaml", ""))

        assertTrue(result.hasErrors)
        assertTrue(result.resources.isEmpty())
        val error = result.diagnostics.firstOrNull { it.code.value == "RESOURCE-YAML-001" }
        assertNotNull(error, "expected RESOURCE-YAML-001 but got: ${result.diagnostics.map { it.code.value }}")
        assertNull(error.location)
        assertNotNull(error.message)
    }

    // -------------------------------------------------------------------------
    // REQ: Resource-Envelope-Per-Kind — unknown kind
    // -------------------------------------------------------------------------

    @Test
    fun `unknown kind produces RESOURCE-KIND-001`() {
        val yaml = """
            apiVersion: pipelattice.dev/v1alpha1
            kind: UnknownResourceKind
            metadata:
              name: test
            spec:
              profile:
                ref: catalog://profiles/test@stable
        """.trimIndent()

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.hasErrors)
        assertTrue(result.resources.isEmpty())
        val error = result.diagnostics.firstOrNull { it.code.value == "RESOURCE-KIND-001" }
        assertNotNull(error, "expected RESOURCE-KIND-001 but got: ${result.diagnostics.map { it.code.value }}")
        assertContains(error.message, "UnknownResourceKind")
    }
}
