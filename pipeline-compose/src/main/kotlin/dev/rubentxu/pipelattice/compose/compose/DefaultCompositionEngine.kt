package dev.rubentxu.pipelattice.compose.compose

import dev.rubentxu.pipelattice.compose.CompositionEngine
import dev.rubentxu.pipelattice.compose.domain.CompositionRequest
import dev.rubentxu.pipelattice.compose.domain.CompositionResult
import dev.rubentxu.pipelattice.compose.domain.ExplainResult
import dev.rubentxu.pipelattice.compose.domain.Layer
import dev.rubentxu.pipelattice.compose.domain.MergeRule
import dev.rubentxu.pipelattice.compose.domain.ParameterNode
import dev.rubentxu.pipelattice.compose.domain.Provenance
import dev.rubentxu.pipelattice.compose.domain.ProvenanceSource
import dev.rubentxu.pipelattice.compose.domain.Transformation
import dev.rubentxu.pipelattice.compose.diagnostics.CompositionDiagnosticCodes
import dev.rubentxu.pipelattice.compose.ports.CatalogSource
import dev.rubentxu.pipelattice.compose.ports.ProvenanceSink
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticCode
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
import dev.rubentxu.pipelattice.resource.ParameterValue
import dev.rubentxu.pipelattice.resource.PipelineProfileResource
import dev.rubentxu.pipelattice.resource.ResourceParser

/**
 * Default composition engine that orchestrates the full pipeline composition workflow.
 *
 * Workflow:
 * 1. Validate the composition request
 * 2. Resolve profile imports via [ImportResolver]
 * 3. Flatten the profile chain into a single parameter map
 * 4. Bind parameters with local overrides via [ParameterBinder]
 * 5. Merge bound parameters with local overrides
 * 6. Compute fingerprint for change detection
 * 7. Return [CompositionResult]
 *
 * @param importResolver Resolves profile import chains.
 * @param mergeEngine Applies merge rules for parameter composition.
 * @param parameterBinder Binds profile parameters with local overrides.
 * @param fingerprint Computes stable fingerprints for change detection.
 * @param parser Resource parser for converting source documents to parsed resources.
 */
internal class DefaultCompositionEngine(
    private val importResolver: ImportResolver,
    private val mergeEngine: MergeEngine,
    private val parameterBinder: ParameterBinder,
    private val fingerprint: FingerprintComputer,
    private val parser: ResourceParser,
) : CompositionEngine {

    /**
     * Composes a pipeline definition from the given request.
     *
     * @param request The composition request containing the pipeline definition and optional overrides.
     * @param catalog The catalog source for resolving imported resources.
     * @param provenance The provenance sink for recording resolution provenance.
     * @param diagnostics The diagnostic sink (currently not used - diagnostics collected internally).
     * @return A [CompositionResult] containing the resolved pipeline parameters, provenance,
     *         fingerprint, and any diagnostics collected during composition.
     */
    override fun compose(
        request: CompositionRequest,
        catalog: CatalogSource,
        provenance: ProvenanceSink,
        diagnostics: ProvenanceSink,
    ): CompositionResult {
        // Internal diagnostic collection
        val collectedDiagnostics = mutableListOf<Diagnostic>()

        // Step 1: Validate request
        if (request.definition.metadata.name.isBlank()) {
            collectedDiagnostics.add(
                Diagnostic(
                    code = CompositionDiagnosticCodes.UNKNOWN_KIND,
                    severity = DiagnosticSeverity.ERROR,
                    message = "Pipeline definition must have a non-blank name"
                )
            )
        }

        // Step 2: Resolve profile imports
        val profileRef = request.definition.spec.profile
        if (profileRef == null) {
            // No profile - just use local parameters
            val params = request.definition.spec.parameters
            val finalParams = flattenParams(params)

            val computedFingerprint = fingerprint.compute(
                parameters = finalParams,
                provenance = emptyMap()
            )

            return CompositionResult(
                pipelineId = request.definition.metadata.name,
                parameters = finalParams,
                provenance = emptyMap(),
                fingerprint = computedFingerprint,
                diagnostics = collectedDiagnostics.toList()
            )
        }

        // Create a DiagnosticSink adapter that collects diagnostics
        val diagnosticSink: dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink =
            dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink { diagnostic ->
                collectedDiagnostics.add(diagnostic)
            }

        // Resolve profile chain
        val profileResources: List<dev.rubentxu.pipelattice.resource.ParsedResource> = try {
            importResolver.resolve(profileRef, catalog, diagnosticSink)
        } catch (e: MergeEngine.MergeUnsupportedException) {
            collectedDiagnostics.add(
                Diagnostic(
                    code = CompositionDiagnosticCodes.MERGE_CONFLICT,
                    severity = DiagnosticSeverity.ERROR,
                    message = e.message ?: "Merge conflict at ${e.path}",
                    location = SourceLocation(path = e.path)
                )
            )
            return CompositionResult(
                pipelineId = request.definition.metadata.name,
                parameters = emptyMap(),
                provenance = emptyMap(),
                fingerprint = "",
                diagnostics = collectedDiagnostics.toList()
            )
        }

        // Step 3: Flatten profile chain
        val profileParams: MutableMap<String, ParameterNode> = mutableMapOf()
        val profileProvenance: MutableMap<String, MutableList<Provenance>> = mutableMapOf()

        for ((index, resource) in profileResources.withIndex()) {
            if (resource !is PipelineProfileResource) continue
            val layer = if (index == 0) Layer.PROFILE else Layer.PROFILE_IMPORT

            for ((key, paramDecl) in resource.spec.parameters.entries) {
                val effectiveValue = paramDecl.default
                if (effectiveValue != null) {
                    val node = ParameterNode.ScalarNode(effectiveValue)
                    profileParams[key] = node

                    val provList = profileProvenance.getOrPut(key) { mutableListOf() }
                    provList.add(
                        Provenance(
                            key = key,
                            layer = layer,
                            source = ProvenanceSource(
                                resource = ResourceRef.parse("catalog://${resource.metadata.name}"),
                                location = SourceLocation(path = resource.metadata.name)
                            ),
                            transformations = listOf(
                                Transformation(kind = Transformation.PROVIDED_BY, detail = "profile default")
                            ),
                            effectiveValue = effectiveValue
                        )
                    )
                }
            }
        }

        // Step 4: Bind profile parameters with overrides
        val pipelineRef = ResourceRef.parse("catalog://pipelines/${request.definition.metadata.name}")
        val profileDecls = profileResources
            .filterIsInstance<PipelineProfileResource>()
            .firstOrNull()
            ?.spec?.parameters
            ?: emptyMap()

        val bindingResult = parameterBinder.bind(
            profileDecls = profileDecls,
            profileRef = profileRef,
            localOverrides = request.definition.spec.parameters,
            pipelineRef = pipelineRef,
        )

        // Convert bindings to parameter nodes with provenance
        val boundParams: Map<String, ParameterNode> = bindingResult.bindings.mapValues { entry ->
            val (key, value) = entry
            val provList = profileProvenance.getOrPut(key) { mutableListOf() }
            provList.add(
                Provenance(
                    key = key,
                    layer = Layer.LOCAL,
                    source = ProvenanceSource(
                        resource = pipelineRef,
                        location = SourceLocation(path = request.definition.metadata.name)
                    ),
                    transformations = listOf(
                        Transformation(kind = Transformation.OVERRIDDEN_BY, detail = "local override")
                    ),
                    effectiveValue = value
                )
            )
            ParameterNode.ScalarNode(value)
        }

        // Step 5: Merge profile params with bound params (bound params are local override)
        val mergedParams: MutableMap<String, ParameterNode> = mutableMapOf()

        // First, add all profile params
        for (entry in profileParams.entries) {
            val key = entry.key
            val node = entry.value
            val boundNode = boundParams[key]
            if (boundNode != null) {
                mergedParams[key] = boundNode
            } else {
                mergedParams[key] = node
            }
        }

        // Add any bound params that weren't in profile
        for (entry in boundParams.entries) {
            val key = entry.key
            if (key !in mergedParams) {
                mergedParams[key] = entry.value
            }
        }

        // Step 6: Flatten to final parameter map
        val finalParams = flattenParamNodes(mergedParams)

        // Emit provenance
        for ((key, provList) in profileProvenance.entries) {
            for (prov in provList) {
                provenance.emit(prov)
            }
        }

        // Step 7: Compute fingerprint
        val provenanceForFingerprint = profileProvenance.mapValues { it.value.toList() }
        val computedFingerprint = fingerprint.compute(finalParams, provenanceForFingerprint)

        return CompositionResult(
            pipelineId = request.definition.metadata.name,
            parameters = finalParams,
            provenance = profileProvenance.mapValues { it.value.toList() },
            fingerprint = computedFingerprint,
            diagnostics = collectedDiagnostics.toList()
        )
    }

    /**
     * Explains how a specific key was resolved in a previous composition result.
     *
     * Phase 6 implements the full explain functionality.
     * This placeholder throws NotImplementedError.
     *
     * @param result The composition result to explain.
     * @param path The dot-notation path to the key (e.g., "pipeline.stages.build").
     * @return An [ExplainResult] describing the resolution.
     * @throws NotImplementedError This method is not yet implemented.
     */
    override fun explain(result: CompositionResult, path: String): ExplainResult {
        throw NotImplementedError("explain() will be implemented in Phase 6")
    }

    /**
     * Flattens a map of ParameterValues to a flat Map<String, ParameterValue>.
     */
    private fun flattenParams(params: Map<String, ParameterValue>): Map<String, ParameterValue> {
        return params
    }

    /**
     * Flattens a map of ParameterNodes to a flat Map<String, ParameterValue>.
     */
    private fun flattenParamNodes(nodes: Map<String, ParameterNode>): Map<String, ParameterValue> {
        val result = mutableMapOf<String, ParameterValue>()

        for ((key, node) in nodes.entries) {
            when (node) {
                is ParameterNode.ScalarNode -> {
                    result[key] = node.value
                }
                is ParameterNode.MapNode -> {
                    // Skip complex nested structures for now
                }
                is ParameterNode.ListNode -> {
                    // Skip complex nested structures for now
                }
            }
        }

        return result
    }
}
