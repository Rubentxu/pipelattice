package dev.rubentxu.pipelattice.compose.compose

import dev.rubentxu.pipelattice.compose.domain.CompositionRequest
import dev.rubentxu.pipelattice.compose.domain.CompositionResult
import dev.rubentxu.pipelattice.compose.domain.ExplainResult
import dev.rubentxu.pipelattice.compose.domain.Layer
import dev.rubentxu.pipelattice.compose.ports.CatalogSource
import dev.rubentxu.pipelattice.compose.ports.ProvenanceSink
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
import dev.rubentxu.pipelattice.resource.ApiVersion
import dev.rubentxu.pipelattice.resource.Metadata
import dev.rubentxu.pipelattice.resource.PipelineDefinitionResource
import dev.rubentxu.pipelattice.resource.PipelineDefinitionSpec
import dev.rubentxu.pipelattice.resource.PipelineProfileResource
import dev.rubentxu.pipelattice.resource.PipelineProfileSpec
import dev.rubentxu.pipelattice.resource.ResourceParser
import dev.rubentxu.pipelattice.resource.SourceDocument
import dev.rubentxu.pipelattice.resource.ParseResult
import dev.rubentxu.pipelattice.resource.ParameterValue
import dev.rubentxu.pipelattice.resource.ParameterDeclaration
import dev.rubentxu.pipelattice.resource.ParameterType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * End-to-end tests for [DefaultCompositionEngine].
 *
 * Test scenarios:
 * 1. payments-api happy path - profile with parameters, no overrides
 * 2. governance transport (Caso F) - optional parameter with no default and no override
 * 3. import cycle - propagates IMPORT-001 diagnostic
 */
class DefaultCompositionEngineTest {

    private val API = ApiVersion.KNOWN

    // Helper to create a pipeline definition resource
    private fun pipelineDef(
        name: String,
        profileRef: ResourceRef? = null,
        parameters: Map<String, ParameterValue> = emptyMap(),
    ): PipelineDefinitionResource {
        return PipelineDefinitionResource(
            apiVersion = API,
            metadata = Metadata(name = name),
            spec = PipelineDefinitionSpec(
                profile = profileRef,
                parameters = parameters,
            )
        )
    }

    // Helper to create a profile resource
    private fun profile(
        name: String,
        imports: List<ResourceRef> = emptyList(),
        parameters: Map<String, ParameterDeclaration> = emptyMap(),
    ): PipelineProfileResource {
        return PipelineProfileResource(
            apiVersion = API,
            metadata = Metadata(name = name),
            spec = PipelineProfileSpec(
                imports = imports,
                parameters = parameters,
            )
        )
    }

    // Helper to create a parameter declaration
    private fun paramDecl(type: ParameterType, default: ParameterValue? = null) =
        ParameterDeclaration(type = type, default = default)

    // Mock parser that returns profile resources
    private class MockParser(private val profiles: Map<String, PipelineProfileResource>) : ResourceParser {
        override fun parse(document: SourceDocument): ParseResult {
            val profile = profiles[document.path]
            return if (profile != null) {
                ParseResult(resources = listOf(profile))
            } else {
                ParseResult.failed(emptyList())
            }
        }
    }

    // Mock catalog that returns source documents for known paths
    private class MockCatalog(private val paths: Set<String>) : CatalogSource {
        override fun resolve(ref: ResourceRef, sink: DiagnosticSink): SourceDocument? {
            return if (ref.path in paths) {
                SourceDocument(ref.path, "")
            } else {
                null
            }
        }
    }

    // Collecting provenance sink
    private class CollectingProvenanceSink : ProvenanceSink {
        val emitted = mutableListOf<dev.rubentxu.pipelattice.compose.domain.Provenance>()
        override fun emit(node: dev.rubentxu.pipelattice.compose.domain.Provenance) {
            emitted.add(node)
        }
    }

    // Empty provenance sink
    private class NoOpProvenanceSink : ProvenanceSink {
        override fun emit(node: dev.rubentxu.pipelattice.compose.domain.Provenance) {}
    }

    // --- Scenario 1: payments-api happy path ---

    @Test
    fun `compose happy path - profile with parameters and no overrides`() {
        // Create profile "payments-api" with timeout parameter
        val profileDecls = mapOf(
            "timeout" to paramDecl(ParameterType.INTEGER, ParameterValue.IntValue(30)),
            "enabled" to paramDecl(ParameterType.BOOLEAN, ParameterValue.BoolValue(true))
        )
        val profileResource = profile("payments-api", parameters = profileDecls)
        val parser = MockParser(mapOf("payments-api" to profileResource))
        val catalog = MockCatalog(setOf("payments-api"))

        // Create import resolver and composition engine
        val importResolver = ImportResolver(parser = parser)
        val mergeEngine = MergeEngine()
        val parameterBinder = ParameterBinder(DiagnosticSink {})
        val fingerprint = FingerprintComputer
        val engine = DefaultCompositionEngine(importResolver, mergeEngine, parameterBinder, fingerprint, parser)

        // Create pipeline definition that references the profile
        val pipeline = pipelineDef("build-payments", ResourceRef.parse("catalog://payments-api"))
        val request = CompositionRequest(definition = pipeline)
        val provenanceSink = NoOpProvenanceSink()
        val diagnosticsSink = NoOpProvenanceSink()

        val result = engine.compose(request, catalog, provenanceSink)

        assertEquals("build-payments", result.pipelineId)
        assertEquals(30L, (result.parameters["timeout"] as ParameterValue.IntValue).value)
        assertEquals(true, (result.parameters["enabled"] as ParameterValue.BoolValue).value)
        assertTrue(result.fingerprint.isNotEmpty())
        assertTrue(result.fingerprint.length == 64)
        assertTrue(result.diagnostics.isEmpty(), "Expected no errors")
    }

    // --- Scenario 2: governance transport (Caso F) - optional param with no default ---

    @Test
    fun `compose with optional unbound parameter (Caso F)`() {
        // Create profile with optional parameter that has no default
        val profileDecls = mapOf(
            "timeout" to paramDecl(ParameterType.INTEGER, ParameterValue.IntValue(30)),
            "optionalFlag" to paramDecl(ParameterType.BOOLEAN, default = null)  // Optional, no default
        )
        val profileResource = profile("transport-profile", parameters = profileDecls)
        val parser = MockParser(mapOf("transport-profile" to profileResource))
        val catalog = MockCatalog(setOf("transport-profile"))

        val importResolver = ImportResolver(parser = parser)
        val mergeEngine = MergeEngine()
        val parameterBinder = ParameterBinder(DiagnosticSink {})
        val fingerprint = FingerprintComputer
        val engine = DefaultCompositionEngine(importResolver, mergeEngine, parameterBinder, fingerprint, parser)

        // Pipeline with no overrides
        val pipeline = pipelineDef("transport-pipeline", ResourceRef.parse("catalog://transport-profile"))
        val request = CompositionRequest(definition = pipeline)
        val provenanceSink = NoOpProvenanceSink()
        val diagnosticsSink = NoOpProvenanceSink()

        val result = engine.compose(request, catalog, provenanceSink)

        // timeout should be bound, optionalFlag should be absent (unbound)
        assertEquals(30L, (result.parameters["timeout"] as ParameterValue.IntValue).value)
        assertTrue(result.parameters["optionalFlag"] == null, "Optional parameter with no default should not be bound")
        assertTrue(result.diagnostics.isEmpty(), "No errors expected for unbound optional parameter")
    }

    // --- Scenario 3: import cycle propagates IMPORT-001 ---

    @Test
    fun `compose with import cycle propagates IMPORT-001 diagnostic`() {
        // Create two profiles that import each other
        val profileA = profile(
            "cycle-a",
            imports = listOf(ResourceRef.parse("catalog://cycle-b"))
        )
        val profileB = profile(
            "cycle-b",
            imports = listOf(ResourceRef.parse("catalog://cycle-a"))
        )

        val parser = MockParser(mapOf(
            "cycle-a" to profileA,
            "cycle-b" to profileB
        ))
        val catalog = MockCatalog(setOf("cycle-a", "cycle-b"))

        val importResolver = ImportResolver(parser = parser)
        val mergeEngine = MergeEngine()
        val parameterBinder = ParameterBinder(DiagnosticSink {})
        val fingerprint = FingerprintComputer
        val engine = DefaultCompositionEngine(importResolver, mergeEngine, parameterBinder, fingerprint, parser)

        val pipeline = pipelineDef("cyclic-pipeline", ResourceRef.parse("catalog://cycle-a"))
        val request = CompositionRequest(definition = pipeline)
        val provenanceSink = NoOpProvenanceSink()
        val diagnosticsSink = NoOpProvenanceSink()

        val result = engine.compose(request, catalog, provenanceSink)

        // Should have IMPORT-001 diagnostic
        assertTrue(
            result.diagnostics.any { it.code.value == "COMPOSE-IMPORT-001" },
            "Expected IMPORT-001 diagnostic for import cycle. Got: ${result.diagnostics.map { it.code.value }}"
        )
    }

    // --- REQ-006: null profile uses local parameters only ---

    @Test
    fun `compose with null profile uses local parameters only`() {
        // Pipeline with no profile reference, only local parameters
        val pipeline = pipelineDef(
            name = "local-only-pipeline",
            profileRef = null,
            parameters = mapOf("javaVersion" to ParameterValue.IntValue(25))
        )
        val request = CompositionRequest(definition = pipeline)
        val provenanceSink = NoOpProvenanceSink()

        val parser = MockParser(emptyMap())
        val importResolver = ImportResolver(parser = parser)
        val mergeEngine = MergeEngine()
        val parameterBinder = ParameterBinder(DiagnosticSink {})
        val fingerprint = FingerprintComputer
        val engine = DefaultCompositionEngine(importResolver, mergeEngine, parameterBinder, fingerprint, parser)

        val result = engine.compose(request, MockCatalog(emptySet()), provenanceSink)

        // parameters should contain javaVersion with value 25
        assertEquals(
            ParameterValue.IntValue(25),
            result.parameters["javaVersion"],
            "javaVersion should be IntValue(25)"
        )
        // provenance should be empty (no profile)
        assertTrue(result.provenance.isEmpty(), "provenance should be empty for null profile")
        // fingerprint should be computed
        assertTrue(result.fingerprint.isNotEmpty(), "fingerprint should be computed")
        assertEquals(64, result.fingerprint.length, "fingerprint should be 64-char SHA-256 hex")
    }

    // --- Explain tests ---

    @Test
    fun `explain returns Hit for existing key`() {
        val parser = MockParser(emptyMap())
        val importResolver = ImportResolver(parser = parser)
        val mergeEngine = MergeEngine()
        val parameterBinder = ParameterBinder(DiagnosticSink {})
        val fingerprint = FingerprintComputer
        val engine = DefaultCompositionEngine(importResolver, mergeEngine, parameterBinder, fingerprint, parser)

        // Create provenance with a chain
        val provenanceSource = dev.rubentxu.pipelattice.compose.domain.ProvenanceSource(
            resource = ResourceRef.parse("catalog://payments-api"),
            location = SourceLocation(path = "payments-api")
        )
        val transformation = dev.rubentxu.pipelattice.compose.domain.Transformation(
            kind = dev.rubentxu.pipelattice.compose.domain.Transformation.PROVIDED_BY,
            detail = "profile default"
        )
        val prov1 = dev.rubentxu.pipelattice.compose.domain.Provenance(
            key = "timeout",
            layer = Layer.PROFILE_IMPORT,
            source = provenanceSource,
            transformations = listOf(transformation),
            effectiveValue = ParameterValue.IntValue(30)
        )
        val prov2 = dev.rubentxu.pipelattice.compose.domain.Provenance(
            key = "timeout",
            layer = Layer.LOCAL,
            source = provenanceSource,
            transformations = listOf(transformation),
            effectiveValue = ParameterValue.IntValue(60)
        )

        val provenance = mapOf("timeout" to listOf(prov1, prov2))
        val result = CompositionResult(
            pipelineId = "test",
            parameters = mapOf("timeout" to ParameterValue.IntValue(60)),
            provenance = provenance,
            fingerprint = "abc123"
        )

        val explainResult = engine.explain(result, "timeout")

        assertIs<ExplainResult.Hit>(explainResult)
        assertEquals(2, explainResult.chain.size)
        // Chain should be root-to-leaf: PROFILE_IMPORT first, then LOCAL
        assertEquals(Layer.PROFILE_IMPORT, explainResult.chain[0].layer)
        assertEquals(Layer.LOCAL, explainResult.chain[1].layer)
    }

    @Test
    fun `explain returns Miss for non-existing key`() {
        val parser = MockParser(emptyMap())
        val importResolver = ImportResolver(parser = parser)
        val mergeEngine = MergeEngine()
        val parameterBinder = ParameterBinder(DiagnosticSink {})
        val fingerprint = FingerprintComputer
        val engine = DefaultCompositionEngine(importResolver, mergeEngine, parameterBinder, fingerprint, parser)

        val result = CompositionResult(
            pipelineId = "test",
            parameters = emptyMap(),
            provenance = emptyMap(),
            fingerprint = "abc123"
        )

        val explainResult = engine.explain(result, "nonexistent")

        assertIs<ExplainResult.Miss>(explainResult)
    }

    @Test
    fun `explain parses nested path and returns last component`() {
        val parser = MockParser(emptyMap())
        val importResolver = ImportResolver(parser = parser)
        val mergeEngine = MergeEngine()
        val parameterBinder = ParameterBinder(DiagnosticSink {})
        val fingerprint = FingerprintComputer
        val engine = DefaultCompositionEngine(importResolver, mergeEngine, parameterBinder, fingerprint, parser)

        val provenanceSource = dev.rubentxu.pipelattice.compose.domain.ProvenanceSource(
            resource = ResourceRef.parse("catalog://profiles/java"),
            location = SourceLocation(path = "profiles/java")
        )
        val transformation = dev.rubentxu.pipelattice.compose.domain.Transformation(
            kind = dev.rubentxu.pipelattice.compose.domain.Transformation.PROVIDED_BY,
            detail = "profile default"
        )
        val prov = dev.rubentxu.pipelattice.compose.domain.Provenance(
            key = "timeout",
            layer = Layer.PROFILE,
            source = provenanceSource,
            transformations = listOf(transformation),
            effectiveValue = ParameterValue.IntValue(30)
        )

        val provenance = mapOf("timeout" to listOf(prov))
        val result = CompositionResult(
            pipelineId = "test",
            parameters = mapOf("timeout" to ParameterValue.IntValue(30)),
            provenance = provenance,
            fingerprint = "abc123"
        )

        // Path "pipeline.stages.build" should look up "build" key
        val explainResult = engine.explain(result, "pipeline.stages.build")

        // Key "build" not found in provenance which only has "timeout"
        assertIs<ExplainResult.Miss>(explainResult)
    }
}
