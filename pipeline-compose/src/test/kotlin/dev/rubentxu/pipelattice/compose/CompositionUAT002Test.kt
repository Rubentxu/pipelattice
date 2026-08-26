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
import dev.rubentxu.pipelattice.compose.ports.ProvenanceSink
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
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
 * Golden composition test UAT002.
 *
 * Test scenario: dotnet-pipeline references dotnet-container@1.0.0 profile
 * with local override dotnetVersion="8.0".
 *
 * Expected outcomes:
 * - parameters["dotnetVersion"] == StringValue("8.0")
 * - provenance size == 2 (importedBy from profile + overriddenBy from local)
 */
class CompositionUAT002Test {

    private val API = ApiVersion.KNOWN

    /**
     * Parser that simulates parsing the dotnet golden YAML files.
     * Profile has NO javaVersion - it uses dotnetVersion instead.
     */
    private val yamlParser = ResourceParser { doc ->
        when {
            doc.path.contains("dotnet-container") -> {
                // Simulate parsing dotnet-container@1.0.0.yaml
                // NOTE: This profile uses dotnetVersion, NOT javaVersion
                val profile = PipelineProfileResource(
                    apiVersion = API,
                    metadata = Metadata(name = "dotnet-container", version = "1.0.0"),
                    spec = PipelineProfileSpec(
                        imports = listOf(
                            ResourceRef.parse("catalog://company/base@3"),
                            ResourceRef.parse("catalog://language/dotnet@4"),
                            ResourceRef.parse("catalog://build/dotnet@3"),
                            ResourceRef.parse("catalog://security/standard@7"),
                            ResourceRef.parse("catalog://packaging/container@6"),
                        ),
                        parameters = mapOf(
                            "dotnetVersion" to ParameterDeclaration(
                                type = ParameterType.STRING,
                                default = ParameterValue.StringValue("8.0")
                            )
                        )
                    )
                )
                ParseResult(resources = listOf(profile))
            }
            doc.path.contains("dotnet-pipeline") -> {
                // Simulate parsing dotnet-pipeline.yaml
                val pipeline = PipelineDefinitionResource(
                    apiVersion = API,
                    metadata = Metadata(name = "dotnet-pipeline"),
                    spec = PipelineDefinitionSpec(
                        profile = ResourceRef.parse("catalog://profiles/dotnet-container@1.0.0"),
                        parameters = mapOf(
                            "dotnetVersion" to ParameterValue.StringValue("8.0")
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
        val dotnetProfileDoc = SourceDocument(
            path = "profiles/dotnet-container@1.0.0",
            content = """
                apiVersion: pipelattice.dev/v1alpha1
                kind: PipelineProfile
                metadata:
                  name: dotnet-container
                  version: 1.0.0
                spec:
                  imports:
                    - ref: catalog://company/base@3
                    - ref: catalog://language/dotnet@4
                    - ref: catalog://build/dotnet@3
                    - ref: catalog://security/standard@7
                    - ref: catalog://packaging/container@6
                  parameters:
                    dotnetVersion:
                      type: string
                      default: "8.0"
            """.trimIndent()
        )

        val dotnetPipelineDoc = SourceDocument(
            path = "dotnet-pipeline",
            content = """
                apiVersion: pipelattice.dev/v1alpha1
                kind: PipelineDefinition
                metadata:
                  name: dotnet-pipeline
                spec:
                  profile: catalog://profiles/dotnet-container@1.0.0
                  parameters:
                    dotnetVersion:
                      type: string
                      value: "8.0"
            """.trimIndent()
        )

        return M1CatalogSource(
            parser = yamlParser,
            documents = mapOf(
                ResourceRef.parse("catalog://profiles/dotnet-container@1.0.0") to dotnetProfileDoc,
                ResourceRef.parse("catalog://pipelines/dotnet-pipeline") to dotnetPipelineDoc
            )
        )
    }

    private class NoOpProvenanceSink : ProvenanceSink {
        private val emitted = mutableListOf<Provenance>()
        override fun emit(node: Provenance) { emitted.add(node) }
        fun getAll(): List<Provenance> = emitted.toList()
    }

    @Test
    fun `UAT002 - dotnet-pipeline compose with profile override`() {
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
            metadata = Metadata(name = "dotnet-pipeline"),
            spec = PipelineDefinitionSpec(
                profile = ResourceRef.parse("catalog://profiles/dotnet-container@1.0.0"),
                parameters = mapOf(
                    "dotnetVersion" to ParameterValue.StringValue("8.0")
                )
            )
        )
        val request = CompositionRequest(definition = pipeline)

        // Execute composition
        val result = engine.compose(request, catalog, provenanceSink)

        // Assert 1: parameters["dotnetVersion"] == StringValue("8.0")
        val dotnetVersion = result.parameters["dotnetVersion"]
        assertEquals(
            ParameterValue.StringValue("8.0"),
            dotnetVersion,
            "dotnetVersion should be StringValue(8.0)"
        )

        // Assert 2: provenance size == 2 (importedBy + overriddenBy)
        val dotnetVersionProvenance = result.provenance["dotnetVersion"]
        assertEquals(
            2,
            dotnetVersionProvenance?.size,
            "dotnetVersion provenance should have 2 entries (importedBy + overriddenBy)"
        )

        // Verify provenance chain structure
        // chain[0] should be the profile default (importedBy)
        val profileProv = dotnetVersionProvenance?.get(0)
        assertEquals(Layer.PROFILE, profileProv?.layer)
        assertEquals(ParameterValue.StringValue("8.0"), profileProv?.effectiveValue)

        // chain[1] should be the local override (overriddenBy)
        val localProv = dotnetVersionProvenance?.get(1)
        assertEquals(Layer.LOCAL, localProv?.layer)
        assertEquals(ParameterValue.StringValue("8.0"), localProv?.effectiveValue)

        // Verify fingerprint format (64 char SHA-256 hex lowercase)
        assertEquals(64, result.fingerprint.length)
        assertEquals(
            result.fingerprint,
            result.fingerprint.lowercase(),
            "Fingerprint should be lowercase hex"
        )
    }
}
