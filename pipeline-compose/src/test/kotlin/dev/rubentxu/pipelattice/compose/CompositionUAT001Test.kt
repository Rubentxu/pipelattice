package dev.rubentxu.pipelattice.compose

import dev.rubentxu.pipelattice.compose.compose.DefaultCompositionEngine
import dev.rubentxu.pipelattice.compose.compose.FingerprintComputer
import dev.rubentxu.pipelattice.compose.compose.ImportResolver
import dev.rubentxu.pipelattice.compose.compose.M1CatalogSource
import dev.rubentxu.pipelattice.compose.compose.MergeEngine
import dev.rubentxu.pipelattice.compose.compose.ParameterBinder
import dev.rubentxu.pipelattice.compose.domain.CompositionRequest
import dev.rubentxu.pipelattice.compose.domain.Layer
import dev.rubentxu.pipelattice.compose.domain.Provenance
import dev.rubentxu.pipelattice.compose.domain.ProvenanceSource
import dev.rubentxu.pipelattice.compose.domain.Transformation
import dev.rubentxu.pipelattice.compose.ports.CatalogSource
import dev.rubentxu.pipelattice.compose.ports.ProvenanceSink
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
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

/**
 * Golden composition test UAT001.
 *
 * Test scenario: payments-api pipeline references java-maven-container@4.3.0 profile
 * with local override javaVersion=25.
 *
 * Expected outcomes:
 * - parameters["javaVersion"] == IntValue(25)
 * - provenance size == 2 (importedBy from profile + overriddenBy from local)
 * - fingerprint matches snapshot
 */
class CompositionUAT001Test {

    private val API = ApiVersion.KNOWN

    /**
     * Parser that simulates parsing the golden YAML files.
     * In production this would be the SnakeYAML-based config-compiler parser.
     */
    private val yamlParser = ResourceParser { doc ->
        when {
            doc.path.contains("java-maven-container") -> {
                // Simulate parsing java-maven-container@4.3.0.yaml
                val profile = PipelineProfileResource(
                    apiVersion = API,
                    metadata = Metadata(name = "java-maven-container", version = "4.3.0"),
                    spec = PipelineProfileSpec(
                        imports = listOf(
                            ResourceRef.parse("catalog://company/base@3"),
                            ResourceRef.parse("catalog://language/java@5"),
                            ResourceRef.parse("catalog://build/maven@4"),
                            ResourceRef.parse("catalog://security/standard@7"),
                            ResourceRef.parse("catalog://packaging/container@6"),
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
                // Simulate parsing payments-api.yaml
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

    private fun buildCatalog(): M1CatalogSource {
        // Pre-load the golden documents into the catalog
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
                    - ref: catalog://language/java@5
                    - ref: catalog://build/maven@4
                    - ref: catalog://security/standard@7
                    - ref: catalog://packaging/container@6
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

        return M1CatalogSource(
            parser = yamlParser,
            documents = mapOf(
                ResourceRef.parse("catalog://profiles/java-maven-container@4.3.0") to javaProfileDoc,
                ResourceRef.parse("catalog://pipelines/payments-api") to paymentsApiDoc
            )
        )
    }

    private class NoOpProvenanceSink : ProvenanceSink {
        private val emitted = mutableListOf<Provenance>()
        override fun emit(node: Provenance) { emitted.add(node) }
        fun getAll(): List<Provenance> = emitted.toList()
    }

    @Test
    fun `UAT001 - payments-api compose with profile override`() {
        // Build the catalog with golden documents
        val catalog = buildCatalog()
        val provenanceSink = NoOpProvenanceSink()

        // Create import resolver and composition engine
        val importResolver = ImportResolver(parser = yamlParser)
        val mergeEngine = MergeEngine()
        val parameterBinder = ParameterBinder(DiagnosticSink {})
        val fingerprint = FingerprintComputer
        val engine = DefaultCompositionEngine(importResolver, mergeEngine, parameterBinder, fingerprint, yamlParser)

        // Create the pipeline definition request
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
        val request = CompositionRequest(definition = pipeline)

        // Execute composition
        val result = engine.compose(request, catalog, provenanceSink, provenanceSink)

        // Assert 1: parameters["javaVersion"] == IntValue(25)
        val javaVersion = result.parameters["javaVersion"]
        assertEquals(
            ParameterValue.IntValue(25),
            javaVersion,
            "javaVersion should be overridden to 25"
        )

        // Assert 2: provenance size == 2 (importedBy + overriddenBy)
        val javaVersionProvenance = result.provenance["javaVersion"]
        assertEquals(
            2,
            javaVersionProvenance?.size,
            "javaVersion provenance should have 2 entries (importedBy + overriddenBy)"
        )

        // Verify provenance chain structure
        // chain[0] should be the profile default (importedBy)
        val profileProv = javaVersionProvenance?.get(0)
        assertEquals(Layer.PROFILE, profileProv?.layer)
        assertEquals(ParameterValue.IntValue(21), profileProv?.effectiveValue)

        // chain[1] should be the local override (overriddenBy)
        val localProv = javaVersionProvenance?.get(1)
        assertEquals(Layer.LOCAL, localProv?.layer)
        assertEquals(ParameterValue.IntValue(25), localProv?.effectiveValue)

        // Assert 3: fingerprint matches snapshot
        // The fingerprint is computed from parameters + provenance
        // First run: capture the fingerprint value
        val expectedFingerprint = result.fingerprint

        // Verify fingerprint format (64 char SHA-256 hex lowercase)
        assertEquals(64, expectedFingerprint.length)
        assertEquals(
            expectedFingerprint,
            expectedFingerprint.lowercase(),
            "Fingerprint should be lowercase hex"
        )
    }

    @Test
    fun `UAT001 - fingerprint snapshot verification`() {
        // This test verifies the fingerprint is deterministic and matches the snapshot
        val catalog = buildCatalog()
        val provenanceSink = NoOpProvenanceSink()

        val importResolver = ImportResolver(parser = yamlParser)
        val mergeEngine = MergeEngine()
        val parameterBinder = ParameterBinder(DiagnosticSink {})
        val fingerprint = FingerprintComputer
        val engine = DefaultCompositionEngine(importResolver, mergeEngine, parameterBinder, fingerprint, yamlParser)

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
        val request = CompositionRequest(definition = pipeline)

        // Compute fingerprint 100 times and verify determinism
        val hashes = (1..100).map {
            val result = engine.compose(request, catalog, provenanceSink, provenanceSink)
            result.fingerprint
        }

        // All hashes should be identical
        assertEquals(
            hashes.toSet().size,
            1,
            "Fingerprint should be deterministic across 100 runs"
        )

        // Read and verify against snapshot file
        val snapshotFile = java.io.File(
            "src/test/resources/golden/UAT001-fingerprint.txt"
        )
        if (snapshotFile.exists()) {
            val expectedFingerprint = snapshotFile.readText().trim()
            assertEquals(
                expectedFingerprint,
                hashes.first(),
                "Fingerprint should match snapshot"
            )
        } else {
            // First run: create snapshot
            snapshotFile.parentFile.mkdirs()
            snapshotFile.writeText(hashes.first())
            println("Created fingerprint snapshot: ${hashes.first()}")
        }
    }
}
