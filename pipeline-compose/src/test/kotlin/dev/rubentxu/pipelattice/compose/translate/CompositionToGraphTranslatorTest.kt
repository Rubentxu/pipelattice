package dev.rubentxu.pipelattice.compose.translate

import dev.rubentxu.pipelattice.compose.domain.CompositionRequest
import dev.rubentxu.pipelattice.compose.domain.CompositionResult
import dev.rubentxu.pipelattice.compose.domain.Layer
import dev.rubentxu.pipelattice.compose.domain.Provenance
import dev.rubentxu.pipelattice.compose.domain.ProvenanceSource
import dev.rubentxu.pipelattice.compose.domain.Transformation
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import dev.rubentxu.pipelattice.resource.ParameterValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [CompositionToGraphTranslator].
 *
 * Tests the Q8 mapping table:
 * - PROFILE_IMPORT layer → IMPORTS + EXTENDS edges
 * - PROFILE layer with selects → SELECTS edge
 * - LOCAL layer → OVERRIDES edge
 */
class CompositionToGraphTranslatorTest {

    private val translator = CompositionToGraphTranslator()

    @Test
    fun `profile imports produce IMPORTS and EXTENDS edges`() {
        // Given: a provenance entry with PROFILE_IMPORT layer
        val importedProfileRef = ResourceRef.parse("catalog://profiles/base-profile@1.0")
        val pipelineId = "test-pipeline"

        val provenance = Provenance(
            key = "javaVersion",
            layer = Layer.PROFILE_IMPORT,
            source = ProvenanceSource(
                resource = importedProfileRef,
                location = SourceLocation(path = importedProfileRef.canonicalForm)
            ),
            transformations = listOf(
                Transformation(kind = Transformation.IMPORTED_BY, detail = "imported")
            ),
            effectiveValue = ParameterValue.IntValue(21)
        )

        val result = CompositionResult(
            pipelineId = pipelineId,
            parameters = mapOf("javaVersion" to ParameterValue.IntValue(21)),
            provenance = mapOf("javaVersion" to listOf(provenance)),
            fingerprint = "abc123def456abc123def456abc123def456abc123def456abc123def456abc1"
        )

        // When: translating the result
        val changeSet = translator.translate(result)

        // Then: two edges are added (IMPORTS + EXTENDS)
        assertEquals(2, changeSet.addedEdges.size)

        val importsEdge = changeSet.addedEdges.find { it.kind == EdgeKind.IMPORTS }
        assertEquals(GraphNode.PipelineProfile(importedProfileRef), importsEdge?.source)
        assertEquals(
            GraphNode.Project(ResourceRef.parse("catalog://pipelines/$pipelineId")),
            importsEdge?.target
        )

        val extendsEdge = changeSet.addedEdges.find { it.kind == EdgeKind.EXTENDS }
        assertEquals(GraphNode.PipelineProfile(importedProfileRef), extendsEdge?.source)
        assertEquals(
            GraphNode.Project(ResourceRef.parse("catalog://pipelines/$pipelineId")),
            extendsEdge?.target
        )

        assertTrue(changeSet.removedEdges.isEmpty())
    }

    @Test
    fun `profile with selects produces SELECTS edge`() {
        // Given: a provenance entry with PROFILE layer and a workflow reference
        val profileRef = ResourceRef.parse("catalog://profiles/java-profile@2.0")
        val workflowRef = ResourceRef.parse("catalog://workflows/java-build@1.0")
        val pipelineId = "my-pipeline"

        val provenance = Provenance(
            key = "workflow",
            layer = Layer.PROFILE,
            source = ProvenanceSource(
                resource = profileRef,
                location = SourceLocation(path = profileRef.canonicalForm)
            ),
            transformations = listOf(
                Transformation(kind = Transformation.SELECTED_BY, detail = "selected workflow")
            ),
            effectiveValue = ParameterValue.StringValue("catalog://workflows/java-build@1.0")
        )

        val result = CompositionResult(
            pipelineId = pipelineId,
            parameters = emptyMap(),
            provenance = mapOf("workflow" to listOf(provenance)),
            fingerprint = "abc123def456abc123def456abc123def456abc123def456abc123def456abc1"
        )

        // When: translating the result
        val changeSet = translator.translate(result)

        // Then: one SELECTS edge is added
        assertEquals(1, changeSet.addedEdges.size)

        val selectsEdge = changeSet.addedEdges.find { it.kind == EdgeKind.SELECTS }
        assertEquals(GraphNode.PipelineProfile(profileRef), selectsEdge?.source)
        assertEquals(GraphNode.Project(workflowRef), selectsEdge?.target)

        assertTrue(changeSet.removedEdges.isEmpty())
    }

    @Test
    fun `local override produces OVERRIDES edge`() {
        // Given: a provenance entry with LOCAL layer
        val pipelineRef = ResourceRef.parse("catalog://pipelines/my-pipeline")
        val pipelineId = "my-pipeline"

        val provenance = Provenance(
            key = "javaVersion",
            layer = Layer.LOCAL,
            source = ProvenanceSource(
                resource = pipelineRef,
                location = SourceLocation(path = pipelineId)
            ),
            transformations = listOf(
                Transformation(kind = Transformation.OVERRIDDEN_BY, detail = "local override")
            ),
            effectiveValue = ParameterValue.IntValue(25)
        )

        val result = CompositionResult(
            pipelineId = pipelineId,
            parameters = mapOf("javaVersion" to ParameterValue.IntValue(25)),
            provenance = mapOf("javaVersion" to listOf(provenance)),
            fingerprint = "abc123def456abc123def456abc123def456abc123def456abc123def456abc1"
        )

        // When: translating the result
        val changeSet = translator.translate(result)

        // Then: one OVERRIDES edge is added
        assertEquals(1, changeSet.addedEdges.size)

        val overridesEdge = changeSet.addedEdges.find { it.kind == EdgeKind.OVERRIDES }
        assertEquals(GraphNode.PipelineProfile(pipelineRef), overridesEdge?.source)
        assertEquals(
            GraphNode.Project(ResourceRef.parse("catalog://pipelines/$pipelineId")),
            overridesEdge?.target
        )

        assertTrue(changeSet.removedEdges.isEmpty())
    }

    @Test
    fun `empty CompositionResult produces empty GraphChangeSet`() {
        // Given: an empty composition result
        val result = CompositionResult(
            pipelineId = "empty-pipeline",
            parameters = emptyMap(),
            provenance = emptyMap(),
            fingerprint = "abc123def456abc123def456abc123def456abc123def456abc123def456abc1"
        )

        // When: translating the result
        val changeSet = translator.translate(result)

        // Then: no edges are added or removed
        assertTrue(changeSet.addedEdges.isEmpty())
        assertTrue(changeSet.removedEdges.isEmpty())
    }
}
