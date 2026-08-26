package dev.rubentxu.pipelattice.compose.domain

import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticCode
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
import dev.rubentxu.pipelattice.resource.ApiVersion
import dev.rubentxu.pipelattice.resource.Metadata
import dev.rubentxu.pipelattice.resource.ParameterValue
import dev.rubentxu.pipelattice.resource.PipelineDefinitionResource
import dev.rubentxu.pipelattice.resource.PipelineDefinitionSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Invariants for [CompositionRequest], [CompositionResult], and [ExplainResult].
 *
 * Phase 2 — domain/CompositionRequest, CompositionResult, ExplainResult
 */
class CompositionResultInvariantsTest {

    // -------------------------------------------------------------------------
    // CompositionRequest
    // -------------------------------------------------------------------------

    @Test
    fun `CompositionRequest accepts definition and empty overrides`() {
        val definition = makePipelineDefinition("pipeline-1")
        val request = CompositionRequest(definition = definition)
        assertEquals(definition, request.definition)
        assertTrue(request.parametersOverride.isEmpty())
    }

    @Test
    fun `CompositionRequest accepts definition with parameter overrides`() {
        val definition = makePipelineDefinition("pipeline-2")
        val overrides = mapOf("timeout" to ParameterValue.StringValue("30m"))
        val request = CompositionRequest(definition = definition, parametersOverride = overrides)
        assertEquals(overrides, request.parametersOverride)
        assertEquals(1, request.parametersOverride.size)
    }

    // -------------------------------------------------------------------------
    // CompositionResult — equality and hashCode contract
    // -------------------------------------------------------------------------

    @Test
    fun `CompositionResult equal instances have same hashCode`() {
        val params = mapOf("key" to ParameterValue.StringValue("val"))
        val provenance = emptyMap<String, List<Provenance>>()
        val diagnostics = emptyList<Diagnostic>()
        val a = CompositionResult(
            pipelineId = "pipe-1",
            parameters = params,
            provenance = provenance,
            fingerprint = "abc123",
            diagnostics = diagnostics
        )
        val b = CompositionResult(
            pipelineId = "pipe-1",
            parameters = params,
            provenance = provenance,
            fingerprint = "abc123",
            diagnostics = diagnostics
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `CompositionResult different pipelineIds are not equal`() {
        val params = mapOf("key" to ParameterValue.StringValue("val"))
        val provenance = emptyMap<String, List<Provenance>>()
        val a = CompositionResult(
            pipelineId = "pipe-1",
            parameters = params,
            provenance = provenance,
            fingerprint = "abc123"
        )
        val b = CompositionResult(
            pipelineId = "pipe-2",
            parameters = params,
            provenance = provenance,
            fingerprint = "abc123"
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `CompositionResult different parameters are not equal`() {
        val provenance = emptyMap<String, List<Provenance>>()
        val a = CompositionResult(
            pipelineId = "pipe-1",
            parameters = mapOf("key" to ParameterValue.StringValue("a")),
            provenance = provenance,
            fingerprint = "abc123"
        )
        val b = CompositionResult(
            pipelineId = "pipe-1",
            parameters = mapOf("key" to ParameterValue.StringValue("b")),
            provenance = provenance,
            fingerprint = "abc123"
        )
        assertNotEquals(a, b)
    }

    @Test
    fun `CompositionResult round-trip — all fields preserved through equality`() {
        val params = mapOf("timeout" to ParameterValue.IntValue(60L))
        val provenance = emptyMap<String, List<Provenance>>()
        val diagnostics = listOf(
            Diagnostic(
                code = DiagnosticCode("COMPOSE-MERGE-001"),
                severity = DiagnosticSeverity.WARNING,
                message = "overlapping key",
                location = SourceLocation(path = "p.yaml", line = 1, column = 1)
            )
        )
        val original = CompositionResult(
            pipelineId = "pipe-1",
            parameters = params,
            provenance = provenance,
            fingerprint = "fingerprint-xyz",
            diagnostics = diagnostics
        )
        // Equal copy
        val copy = CompositionResult(
            pipelineId = original.pipelineId,
            parameters = original.parameters,
            provenance = original.provenance,
            fingerprint = original.fingerprint,
            diagnostics = original.diagnostics
        )
        assertEquals(original, copy)
        assertEquals(original.hashCode(), copy.hashCode())
    }

    // -------------------------------------------------------------------------
    // CompositionResult.hasErrors
    // -------------------------------------------------------------------------

    @Test
    fun `hasErrors is false when diagnostics contain only WARNING`() {
        val diagnostics = listOf(
            Diagnostic(
                code = DiagnosticCode("COMPOSE-MERGE-001"),
                severity = DiagnosticSeverity.WARNING,
                message = "info message"
            )
        )
        val result = CompositionResult(
            pipelineId = "pipe-1",
            parameters = emptyMap(),
            provenance = emptyMap(),
            fingerprint = "abc",
            diagnostics = diagnostics
        )
        assertFalse(result.hasErrors)
    }

    @Test
    fun `hasErrors is true when diagnostics contain ERROR`() {
        val diagnostics = listOf(
            Diagnostic(
                code = DiagnosticCode("COMPOSE-IMPORT-001"),
                severity = DiagnosticSeverity.ERROR,
                message = "import cycle detected"
            )
        )
        val result = CompositionResult(
            pipelineId = "pipe-1",
            parameters = emptyMap(),
            provenance = emptyMap(),
            fingerprint = "abc",
            diagnostics = diagnostics
        )
        assertTrue(result.hasErrors)
    }

    @Test
    fun `hasErrors is false when diagnostics are empty`() {
        val result = CompositionResult(
            pipelineId = "pipe-1",
            parameters = emptyMap(),
            provenance = emptyMap(),
            fingerprint = "abc"
        )
        assertFalse(result.hasErrors)
    }

    @Test
    fun `hasErrors is true when ERROR and WARNING are both present`() {
        val diagnostics = listOf(
            Diagnostic(
                code = DiagnosticCode("COMPOSE-MERGE-001"),
                severity = DiagnosticSeverity.WARNING,
                message = "warning"
            ),
            Diagnostic(
                code = DiagnosticCode("COMPOSE-IMPORT-001"),
                severity = DiagnosticSeverity.ERROR,
                message = "error"
            )
        )
        val result = CompositionResult(
            pipelineId = "pipe-1",
            parameters = emptyMap(),
            provenance = emptyMap(),
            fingerprint = "abc",
            diagnostics = diagnostics
        )
        assertTrue(result.hasErrors)
    }

    // -------------------------------------------------------------------------
    // ExplainResult
    // -------------------------------------------------------------------------

    @Test
    fun `ExplainResult Hit carries non-empty provenance chain`() {
        val provenance = listOf(
            Provenance(
                key = "timeout",
                layer = Layer.PROFILE,
                source = ProvenanceSource(
                    resource = dev.rubentxu.pipelattice.foundation.ResourceRef.parse("catalog://profiles/base"),
                    location = SourceLocation(path = "p.yaml", line = 1, column = 1),
                    humanForm = null
                ),
                transformations = listOf(
                    Transformation(kind = Transformation.REQUESTED_AS, detail = "from profile")
                )
            )
        )
        val hit = ExplainResult.Hit(chain = provenance)
        assertEquals(1, hit.chain.size)
        assertEquals("timeout", hit.chain[0].key)
    }

    @Test
    fun `ExplainResult Miss is a singleton object`() {
        val a = ExplainResult.Miss
        val b = ExplainResult.Miss
        assertTrue(a === b) // referential equality (singleton)
        assertEquals(a, b)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makePipelineDefinition(id: String): PipelineDefinitionResource {
        return PipelineDefinitionResource(
            apiVersion = ApiVersion("pipelattice.dev/v1alpha1"),
            metadata = Metadata(name = id, version = "1.0"),
            spec = PipelineDefinitionSpec(profile = null, parameters = emptyMap())
        )
    }
}
