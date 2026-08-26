package dev.rubentxu.pipelattice.compose

import dev.rubentxu.pipelattice.compose.domain.CompositionRequest
import dev.rubentxu.pipelattice.compose.domain.CompositionResult
import dev.rubentxu.pipelattice.compose.domain.ExplainResult
import dev.rubentxu.pipelattice.compose.ports.CatalogSource
import dev.rubentxu.pipelattice.compose.ports.ProvenanceSink

/**
 * Core composition engine interface.
 *
 * The engine is the central orchestrator of the composition pipeline. It receives a
 * [CompositionRequest], resolves all imports via [CatalogSource], merges profiles using
 * the layer precedence rules, and emits provenance via [ProvenanceSink].
 *
 * **Public API:** This interface is part of the public API of the composition pipeline.
 *
 * ## Workflow
 *
 * 1. **Parse**: The [CatalogSource] resolves import references to [SourceDocument]s.
 * 2. **Merge**: Profiles are merged according to [Layer] precedence (PROFILE_IMPORT < PROFILE < LOCAL).
 * 3. **Emit**: Each resolved key emits a [Provenance] node to [ProvenanceSink].
 * 4. **Result**: A [CompositionResult] is returned with resolved parameters and diagnostics.
 *
 * ## Explain
 *
 * The [explain] method provides a human-readable breakdown of how a specific key was resolved,
 * including its full provenance chain.
 */
public interface CompositionEngine {

    /**
     * Composes a pipeline definition from the given request.
     *
     * @param request The composition request containing the pipeline definition and optional overrides.
     * @param catalog The catalog source for resolving imported resources.
     * @param provenance The provenance sink for recording resolution provenance.
     * @return A [CompositionResult] containing the resolved pipeline parameters, provenance,
     *         fingerprint, and any diagnostics collected during composition.
     */
    public fun compose(
        request: CompositionRequest,
        catalog: CatalogSource,
        provenance: ProvenanceSink,
    ): CompositionResult

    /**
     * Explains how a specific key was resolved in a previous composition result.
     *
     * @param result The composition result to explain.
     * @param path The dot-notation path to the key (e.g., "pipeline.stages.build").
     * @return An [ExplainResult] describing the resolution: either a [ExplainResult.Hit]
     *         with the full provenance chain, or [ExplainResult.Miss] if the key was not resolved.
     */
    public fun explain(result: CompositionResult, path: String): ExplainResult
}
