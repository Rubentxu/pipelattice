package dev.rubentxu.pipelattice.compose.domain

import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.resource.ParameterValue
import dev.rubentxu.pipelattice.resource.PipelineDefinitionResource

/**
 * Request object for the composition pipeline.
 *
 * @param definition The parsed pipeline definition resource to compose.
 * @param parametersOverride Optional top-level parameter overrides applied after composition.
 */
public data class CompositionRequest(
    public val definition: PipelineDefinitionResource,
    public val parametersOverride: Map<String, ParameterValue> = emptyMap(),
)

/**
 * Result of a successful or partially-successful composition run.
 *
 * @param pipelineId The canonical pipeline identifier.
 * @param parameters The fully resolved parameter map.
 * @param provenance The per-key provenance chain map.
 * @param fingerprint A stable hash of the resolved parameters for change detection.
 * @param diagnostics Any warnings or errors collected during composition.
 */
public data class CompositionResult(
    public val pipelineId: String,
    public val parameters: Map<String, ParameterValue>,
    public val provenance: Map<String, List<Provenance>>,
    public val fingerprint: String,
    public val diagnostics: List<Diagnostic> = emptyList(),
) {
    /** True iff any diagnostic has severity ERROR. */
    public val hasErrors: Boolean
        get() = diagnostics.any { it.severity == dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity.ERROR }
}

/**
 * Sealed hierarchy for explaining the resolution of a single configuration key.
 */
public sealed interface ExplainResult {
    /**
     * The key was resolved; [chain] contains the full provenance chain.
     */
    public data class Hit(public val chain: List<Provenance>) : ExplainResult

    /**
     * The key was not resolved (no matching profile or local value).
     */
    public data object Miss : ExplainResult
}
