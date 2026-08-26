package dev.rubentxu.pipelattice.compose

import dev.rubentxu.pipelattice.compose.compose.DefaultCompositionEngine
import dev.rubentxu.pipelattice.compose.compose.FingerprintComputer
import dev.rubentxu.pipelattice.compose.compose.ImportResolver
import dev.rubentxu.pipelattice.compose.compose.M1CatalogSource
import dev.rubentxu.pipelattice.compose.compose.MergeEngine
import dev.rubentxu.pipelattice.compose.compose.ParameterBinder
import dev.rubentxu.pipelattice.compose.domain.CompositionRequest
import dev.rubentxu.pipelattice.compose.domain.ExplainResult
import dev.rubentxu.pipelattice.compose.domain.Layer
import dev.rubentxu.pipelattice.compose.domain.Provenance
import dev.rubentxu.pipelattice.compose.domain.ProvenanceSource
import dev.rubentxu.pipelattice.compose.domain.Transformation
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
import kotlin.test.assertIs

/**
 * Golden explain test UAT015.
 *
 * Verifies that engine.explain() returns the correct provenance chain for
 * "parameters.javaVersion" path.
 *
 * Expected chain (root-to-leaf):
 * - chain[0]: profile default imported, effectiveValue = IntValue(21)
 * - chain[1]: local override applied, effectiveValue = IntValue(25)
 *
 * DUAL assertion: structural PRIMARY (assertEquals on values) + snapshot string SECONDARY.
 */
class ExplainGoldenTest {

    private val API = ApiVersion.KNOWN

    private val yamlParser = ResourceParser { doc ->
        when {
            doc.path.contains("java-maven-container") -> {
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
    fun `UAT015 - explain returns correct provenance chain for javaVersion`() {
        // Build the catalog and engine
        val catalog = buildCatalog()
        val provenanceSink = NoOpProvenanceSink()

        val importResolver = ImportResolver(parser = yamlParser)
        val mergeEngine = MergeEngine()
        val parameterBinder = ParameterBinder(DiagnosticSink {})
        val fingerprint = FingerprintComputer
        val engine = DefaultCompositionEngine(importResolver, mergeEngine, parameterBinder, fingerprint, yamlParser)

        // Create and compose the pipeline
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
        val result = engine.compose(request, catalog, provenanceSink)

        // PRIMARY structural assertion: explain() returns Hit with correct chain
        val explainResult = engine.explain(result, "parameters.javaVersion")

        assertIs<ExplainResult.Hit>(explainResult, "explain should return Hit for existing key")
        assertEquals(2, explainResult.chain.size, "Chain should have 2 entries")

        // Verify chain[0] - profile default imported
        val profileEntry = explainResult.chain[0]
        assertEquals(Layer.PROFILE, profileEntry.layer)
        assertEquals(
            ParameterValue.IntValue(21),
            profileEntry.effectiveValue,
            "chain[0] effectiveValue should be IntValue(21) - profile default"
        )
        assertEquals(
            Transformation.IMPORTED_BY,
            profileEntry.transformations.firstOrNull()?.kind,
            "Profile entry should have IMPORTED_BY transformation"
        )

        // Verify chain[1] - local override applied
        val localEntry = explainResult.chain[1]
        assertEquals(Layer.LOCAL, localEntry.layer)
        assertEquals(
            ParameterValue.IntValue(25),
            localEntry.effectiveValue,
            "chain[1] effectiveValue should be IntValue(25) - local override"
        )
        assertEquals(
            Transformation.OVERRIDDEN_BY,
            localEntry.transformations.firstOrNull()?.kind,
            "Local entry should have OVERRIDDEN_BY transformation"
        )

        // SECONDARY snapshot assertion: verify explain output matches golden file
        val explainText = buildExplainText(explainResult.chain)
        val snapshotFile = java.io.File("src/test/resources/golden/explain-UAT015.txt")
        val expectedSnapshot = snapshotFile.readText().trim()
        assertEquals(
            expectedSnapshot,
            explainText,
            "Explain output should match golden snapshot"
        )
    }

    /**
     * Builds a human-readable explain text from a provenance chain.
     * Format matches the golden snapshot:
     * [profile default imported] catalog://profiles/java-maven-container@4.3.0 → IntValue(21) (importedBy: catalog://profiles/java-maven-container@4.3.0)
     * [local override applied] payments-api → IntValue(25) (overriddenBy: payments-api)
     */
    private fun buildExplainText(chain: List<Provenance>): String {
        return chain.mapIndexed { index, prov ->
            val label = when {
                prov.layer == Layer.PROFILE && prov.transformations.any { it.kind == Transformation.IMPORTED_BY } ->
                    "profile default imported"
                prov.layer == Layer.LOCAL && prov.transformations.any { it.kind == Transformation.OVERRIDDEN_BY } ->
                    "local override applied"
                else -> prov.layer.name.lowercase()
            }

            val location = prov.source.location.path
            val value = prov.effectiveValue?.let { valueToString(it) } ?: "null"
            // Convert transformation kind to camelCase: IMPORTED_BY -> importedBy, OVERRIDDEN_BY -> overriddenBy
            val transformKind = prov.transformations.firstOrNull()?.kind?.let { kindToCamelCase(it) } ?: "unknown"

            "[$label] $location → $value ($transformKind: $location)"
        }.joinToString("\n")
    }

    /**
     * Converts a transformation kind like IMPORTED_BY to camelCase like importedBy.
     * IMPORTED_BY -> importedBy
     * OVERRIDDEN_BY -> overriddenBy
     */
    private fun kindToCamelCase(kind: String): String {
        val parts = kind.split("_")
        return if (parts.size == 2) {
            parts[0].lowercase() + parts[1].lowercase().replaceFirstChar { it.uppercase() }
        } else {
            kind.lowercase()
        }
    }

    private fun valueToString(value: ParameterValue): String = when (value) {
        is ParameterValue.IntValue -> "IntValue(${value.value})"
        is ParameterValue.BoolValue -> "BoolValue(${value.value})"
        is ParameterValue.StringValue -> "StringValue(${value.value})"
    }
}
