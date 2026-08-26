package dev.rubentxu.pipelattice.compose.translate

import dev.rubentxu.pipelattice.compose.CompositionEngine
import dev.rubentxu.pipelattice.compose.compose.DefaultCompositionEngine
import dev.rubentxu.pipelattice.compose.compose.FingerprintComputer
import dev.rubentxu.pipelattice.compose.compose.ImportResolver
import dev.rubentxu.pipelattice.compose.compose.M1CatalogSource
import dev.rubentxu.pipelattice.compose.compose.MergeEngine
import dev.rubentxu.pipelattice.compose.compose.ParameterBinder
import dev.rubentxu.pipelattice.compose.domain.CompositionRequest
import dev.rubentxu.pipelattice.compose.domain.CompositionResult
import dev.rubentxu.pipelattice.compose.domain.Layer
import dev.rubentxu.pipelattice.compose.domain.Provenance
import dev.rubentxu.pipelattice.compose.domain.ProvenanceSource
import dev.rubentxu.pipelattice.compose.domain.Transformation
import dev.rubentxu.pipelattice.compose.ports.CatalogSource
import dev.rubentxu.pipelattice.compose.ports.ProvenanceSink
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphChangeSet
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import dev.rubentxu.pipelattice.graph.ports.GraphProjectionStore
import dev.rubentxu.pipelattice.resource.ApiVersion
import dev.rubentxu.pipelattice.resource.Metadata
import dev.rubentxu.pipelattice.resource.ParameterDeclaration
import dev.rubentxu.pipelattice.resource.ParameterType
import dev.rubentxu.pipelattice.resource.ParameterValue
import dev.rubentxu.pipelattice.resource.ParseResult
import dev.rubentxu.pipelattice.resource.PipelineDefinitionResource
import dev.rubentxu.pipelattice.resource.PipelineDefinitionSpec
import dev.rubentxu.pipelattice.resource.PipelineProfileResource
import dev.rubentxu.pipelattice.resource.PipelineProfileSpec
import dev.rubentxu.pipelattice.resource.ResourceParser
import dev.rubentxu.pipelattice.resource.SourceDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Integration tests for [GraphEmittingCompositionEngine].
 *
 * Verifies that:
 * - The decorator delegates compose() to the wrapped engine
 * - The translator is called to convert the result
 * - The store.apply() is called with the translated change set
 */
class GraphEmittingCompositionEngineTest {

    private val API = ApiVersion.KNOWN

    /**
     * A mock store that records all applied change sets.
     */
    private class RecordingGraphProjectionStore : GraphProjectionStore {
        val appliedChangeSets = mutableListOf<GraphChangeSet>()

        override fun apply(changeSet: GraphChangeSet) {
            appliedChangeSets.add(changeSet)
        }

        override fun snapshot(): dev.rubentxu.pipelattice.graph.domain.GraphSnapshot {
            return dev.rubentxu.pipelattice.graph.domain.GraphSnapshot(
                nodes = emptySet(),
                edges = emptySet(),
                fingerprint = dev.rubentxu.pipelattice.graph.domain.PlanFingerprint("0".repeat(64))
            )
        }
    }

    /**
     * A mock engine that returns a predefined result.
     */
    private class MockCompositionEngine(private val result: CompositionResult) : CompositionEngine {
        var composeCallCount = 0
            private set

        override fun compose(
            request: CompositionRequest,
            catalog: CatalogSource,
            provenance: ProvenanceSink,
        ): CompositionResult {
            composeCallCount++
            return result
        }

        override fun explain(
            result: CompositionResult,
            path: String,
        ): dev.rubentxu.pipelattice.compose.domain.ExplainResult {
            return dev.rubentxu.pipelattice.compose.domain.ExplainResult.Miss
        }
    }

    /**
     * Parser that simulates parsing the golden YAML files.
     */
    private val yamlParser = ResourceParser { doc ->
        when {
            doc.path.contains("java-maven-container") -> {
                val profile = PipelineProfileResource(
                    apiVersion = API,
                    metadata = Metadata(name = "java-maven-container", version = "4.3.0"),
                    spec = PipelineProfileSpec(
                        imports = listOf(
                            ResourceRef.parse("catalog://company/base@3"),
                        ),
                        parameters = mapOf(
                            "javaVersion" to ParameterDeclaration(
                                type = ParameterType.INTEGER,
                                default = ParameterValue.IntValue(21)
                            )
                        )
                    )
                )
                ParseResult(resources = listOf(profile))
            }
            doc.path.contains("payments-api") -> {
                val pipeline = PipelineDefinitionResource(
                    apiVersion = API,
                    metadata = Metadata(name = "payments-api"),
                    spec = PipelineDefinitionSpec(
                        profile = ResourceRef.parse("catalog://profiles/java-maven-container@4.3.0"),
                        parameters = mapOf(
                            "javaVersion" to ParameterValue.IntValue(25)
                        )
                    )
                )
                ParseResult(resources = listOf(pipeline))
            }
            else -> ParseResult.failed(emptyList())
        }
    }

    @Test
    fun `decorator delegates to underlying engine and calls store apply`() {
        // Given: a mock engine with a known result
        val importedProfileRef = ResourceRef.parse("catalog://profiles/base@1.0")
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

        val mockResult = CompositionResult(
            pipelineId = pipelineId,
            parameters = mapOf("javaVersion" to ParameterValue.IntValue(21)),
            provenance = mapOf("javaVersion" to listOf(provenance)),
            fingerprint = "abc123def456abc123def456abc123def456abc123def456abc123def456abc1"
        )

        val recordingStore = RecordingGraphProjectionStore()
        val mockEngine = MockCompositionEngine(mockResult)
        val decorator = GraphEmittingCompositionEngine(mockEngine, recordingStore)

        // Create a dummy catalog and provenance sink
        val catalog = M1CatalogSource(parser = yamlParser, documents = emptyMap())
        val provenanceSink = object : ProvenanceSink {
            override fun emit(node: Provenance) {}
        }

        val pipeline = PipelineDefinitionResource(
            apiVersion = API,
            metadata = Metadata(name = pipelineId),
            spec = PipelineDefinitionSpec(
                profile = ResourceRef.parse("catalog://profiles/test@1.0"),
                parameters = emptyMap()
            )
        )
        val request = CompositionRequest(definition = pipeline)

        // When: calling compose on the decorator
        val result = decorator.compose(request, catalog, provenanceSink)

        // Then: the mock engine was called once
        assertEquals(1, mockEngine.composeCallCount)

        // And: the result is the same as the mock result
        assertSame(mockResult, result)

        // And: the store was called once with a change set containing edges
        assertEquals(1, recordingStore.appliedChangeSets.size)
        val changeSet = recordingStore.appliedChangeSets[0]
        assertTrue(changeSet.addedEdges.isNotEmpty())
    }

    @Test
    fun `decorator works with real DefaultCompositionEngine`() {
        // Given: a real DefaultCompositionEngine wrapped in the decorator
        val recordingStore = RecordingGraphProjectionStore()

        val importResolver = ImportResolver(parser = yamlParser)
        val mergeEngine = MergeEngine()
        val parameterBinder = ParameterBinder(DiagnosticSink {})
        val fingerprint = FingerprintComputer
        val realEngine = DefaultCompositionEngine(
            importResolver, mergeEngine, parameterBinder, fingerprint, yamlParser
        )

        val decorator = GraphEmittingCompositionEngine(realEngine, recordingStore)

        // Build the catalog
        val javaProfileDoc = SourceDocument(
            path = "profiles/java-maven-container@4.3.0",
            content = """
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
            """.trimIndent()
        )

        val paymentsApiDoc = SourceDocument(
            path = "payments-api",
            content = """
                apiVersion: pipelattice.dev/v1alpha1
                kind: PipelineDefinition
                metadata:
                  name: payments-api
                spec:
                  profile: catalog://profiles/java-maven-container@4.3.0
                  parameters:
                    javaVersion:
                      type: integer
                      value: 25
            """.trimIndent()
        )

        val catalog = M1CatalogSource(
            parser = yamlParser,
            documents = mapOf(
                ResourceRef.parse("catalog://profiles/java-maven-container@4.3.0") to javaProfileDoc,
                ResourceRef.parse("catalog://pipelines/payments-api") to paymentsApiDoc
            )
        )

        val provenanceSink = object : ProvenanceSink {
            override fun emit(node: Provenance) {}
        }

        val pipeline = PipelineDefinitionResource(
            apiVersion = API,
            metadata = Metadata(name = "payments-api"),
            spec = PipelineDefinitionSpec(
                profile = ResourceRef.parse("catalog://profiles/java-maven-container@4.3.0"),
                parameters = mapOf("javaVersion" to ParameterValue.IntValue(25))
            )
        )
        val request = CompositionRequest(definition = pipeline)

        // When: calling compose on the decorator
        val result = decorator.compose(request, catalog, provenanceSink)

        // Then: composition succeeded
        assertEquals(ParameterValue.IntValue(25), result.parameters["javaVersion"])

        // And: the store was called with a change set
        assertEquals(1, recordingStore.appliedChangeSets.size)
        val changeSet = recordingStore.appliedChangeSets[0]
        // Edges are produced based on provenance layers
        // For single profile: Layer.PROFILE produces SELECTS if workflowRef exists
        // For multiple profiles: Layer.PROFILE_IMPORT produces IMPORTS + EXTENDS
        // The important thing is that the store was called with a non-empty change set
        assertTrue(changeSet.addedEdges.isNotEmpty() || changeSet.removedEdges.isNotEmpty(),
            "Change set should have at least one edge")
    }
}
