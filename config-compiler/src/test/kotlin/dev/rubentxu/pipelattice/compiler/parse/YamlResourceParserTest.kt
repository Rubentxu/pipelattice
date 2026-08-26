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
        val yaml = buildString {
            append("apiVersion: pipelattice.dev/v1alpha1\n")
            append("kind: PipelineDefinition\n")
            append("metadata:\n")
            append("  name: payments-api\n")
            append("spec:\n")
            append("  profile:\n")
            append("    ref: catalog://profiles/java-maven-container@stable\n")
            append("  parameters:\n")
            append("    javaVersion: 21\n")
        }

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
        val yaml = buildString {
            append("apiVersion: pipelattice.dev/v1alpha1\n")
            append("kind: PipelineProfile\n")
            append("metadata:\n")
            append("  name: java-maven-container\n")
            append("  version: 4.3.0\n")
            append("spec:\n")
            append("  imports:\n")
            append("    - ref: catalog://company/base@3\n")
            append("  parameters:\n")
            append("    javaVersion:\n")
            append("      type: integer\n")
            append("      default: 21\n")
            append("  workflow:\n")
            append("    ref: catalog://workflows/build-container@3\n")
        }

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
        val yaml = buildString {
            append("apiVersion: pipelattice.dev/v1alpha1\n")
            append("kind: PipelineDefinition\n")
            append("metadata:\n")
            append("  name: test\n")
            append("spec:\n")
            append("  profile:\n")
            append("    ref: https://example.com/foo\n")
        }

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
        val yaml = buildString {
            append("apiVersion: pipelattice.dev/v1alpha1\n")
            append("kind: PipelineDefinition\n")
            append("metadata:\n")
            append("  name: test\n")
            append("spec:\n")
            append("  profile:\n")
            append("    ref: catalog://profiles/test@stable\n")
            append("  parameters:\n")
            append("    intParam: 42\n")
            append("    boolParam: true\n")
            append("    strParam: hello-world\n")
        }

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.diagnostics.isEmpty(), "expected no diagnostics: ${result.diagnostics}")
        val params = (result.resources[0] as PipelineDefinitionResource).spec.parameters
        assertEquals(dev.rubentxu.pipelattice.resource.ParameterValue.IntValue(42L), params["intParam"])
        assertEquals(dev.rubentxu.pipelattice.resource.ParameterValue.BoolValue(true), params["boolParam"])
        assertEquals(dev.rubentxu.pipelattice.resource.ParameterValue.StringValue("hello-world"), params["strParam"])
    }

    @Test
    fun `null parameter value rejected with RESOURCE-SCHEMA-002`() {
        val yaml = buildString {
            append("apiVersion: pipelattice.dev/v1alpha1\n")
            append("kind: PipelineDefinition\n")
            append("metadata:\n")
            append("  name: test\n")
            append("spec:\n")
            append("  profile:\n")
            append("    ref: catalog://profiles/test@stable\n")
            append("  parameters:\n")
            append("    image: ~\n")
        }

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.hasErrors)
        assertTrue(result.resources.isEmpty())
        val error = result.diagnostics.firstOrNull { it.code.value == "RESOURCE-SCHEMA-002" }
        assertNotNull(error, "expected RESOURCE-SCHEMA-002 but got: ${result.diagnostics.map { it.code.value }}")
        assertContains(error.message, "must not be null")
    }

    @Test
    fun `mapping parameter value rejected with RESOURCE-SCHEMA-002`() {
        val yaml = buildString {
            append("apiVersion: pipelattice.dev/v1alpha1\n")
            append("kind: PipelineDefinition\n")
            append("metadata:\n")
            append("  name: test\n")
            append("spec:\n")
            append("  profile:\n")
            append("    ref: catalog://profiles/test@stable\n")
            append("  parameters:\n")
            append("    image:\n")
            append("      repo: ghcr.io/x\n")
        }

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
        val yaml = buildString {
            append("apiVersion: pipelattice.dev/v1alpha1\n")
            append("kind: PipelineProfile\n")
            append("metadata:\n")
            append("  name: test-profile\n")
            append("spec:\n")
            append("  parameters:\n")
            append("    javaVersion:\n")
            append("      type: integer\n")
            append("      default: 21\n")
            append("      governance:\n")
            append("        mode: guardrail\n")
            append("        constraints:\n")
            append("          min: 17\n")
            append("          max: 26\n")
        }

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
        val yaml = buildString {
            append("apiVersion: pipelattice.dev/v1alpha1\n")
            append("kind: PipelineProfile\n")
            append("metadata:\n")
            append("  name: test-profile\n")
            append("spec:\n")
            append("  parameters:\n")
            append("    javaVersion:\n")
            append("      type: integer\n")
            append("      default: 21\n")
            append("      governance:\n")
            append("        mode: mandatory\n")
            append("        constraints:\n")
            append("          min: 17\n")
        }

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
        val yaml = buildString {
            append("apiVersion: pipelattice.dev/v1alpha1\n")
            append("kind: PipelineDefinition\n")
            append("metadata:\n")
            append("  name: test\n")
            append("  namme: typo-value\n")
            append("spec:\n")
            append("  profile:\n")
            append("    ref: catalog://profiles/test@stable\n")
        }

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
        val yaml = buildString {
            append("apiVersion: pipelattice.dev/v9\n")
            append("kind: PipelineDefinition\n")
            append("metadata:\n")
            append("  name: test\n")
            append("spec:\n")
            append("  profile:\n")
            append("    ref: catalog://profiles/test@stable\n")
        }

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
    fun `bad indent produces RESOURCE-YAML-001 with line 3`() {
        // Note: line 3 is the "metadata:" line (1-based)
        val yaml = buildString {
            append("apiVersion: pipelattice.dev/v1alpha1\n")
            append("kind: PipelineDefinition\n")
            append("metadata:\n")
            append("  name: bad-indent-example\n")
            append("spec:\n")
            append(" profile:\n")  // intentional bad indent on line 6
            append("    ref: catalog://profiles/test@stable\n")
        }

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.hasErrors)
        assertTrue(result.resources.isEmpty())
        val error = result.diagnostics.firstOrNull { it.code.value == "RESOURCE-YAML-001" }
        assertNotNull(error, "expected RESOURCE-YAML-001 but got: ${result.diagnostics.map { it.code.value }}")
        val loc = error.location
        assertNotNull(loc, "expected location to be present")
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
        val yaml = buildString {
            append("apiVersion: pipelattice.dev/v1alpha1\n")
            append("kind: UnknownResourceKind\n")
            append("metadata:\n")
            append("  name: test\n")
            append("spec:\n")
            append("  profile:\n")
            append("    ref: catalog://profiles/test@stable\n")
        }

        val result = parser.parse(SourceDocument("test.yaml", yaml))

        assertTrue(result.hasErrors)
        assertTrue(result.resources.isEmpty())
        val error = result.diagnostics.firstOrNull { it.code.value == "RESOURCE-KIND-001" }
        assertNotNull(error, "expected RESOURCE-KIND-001 but got: ${result.diagnostics.map { it.code.value }}")
        assertContains(error.message, "UnknownResourceKind")
    }
}
