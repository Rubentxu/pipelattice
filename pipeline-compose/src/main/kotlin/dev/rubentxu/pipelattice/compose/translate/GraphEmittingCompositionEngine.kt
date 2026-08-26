package dev.rubentxu.pipelattice.compose.translate

import dev.rubentxu.pipelattice.compose.CompositionEngine
import dev.rubentxu.pipelattice.compose.domain.CompositionRequest
import dev.rubentxu.pipelattice.compose.domain.CompositionResult
import dev.rubentxu.pipelattice.graph.domain.GraphChangeSet
import dev.rubentxu.pipelattice.graph.ports.GraphProjectionStore

/**
 * A [CompositionEngine] decorator that emits graph edges after successful composition.
 *
 * This decorator wraps a [CompositionEngine] implementation and translates the
 * [CompositionResult] into a [GraphChangeSet] that is applied to the
 * [GraphProjectionStore] after each composition.
 *
 * The decorator pattern ensures:
 * - [CompositionEngine] interface is NOT modified
 * - [DefaultCompositionEngine][dev.rubentxu.pipelattice.compose.compose.DefaultCompositionEngine] class is NOT modified
 * - All new code is isolated in the `translate` package
 *
 * ## Behavior
 *
 * 1. Delegates composition to the wrapped engine
 * 2. Translates the result to graph edges via [CompositionToGraphTranslator]
 * 3. Applies the edges to the [GraphProjectionStore]
 * 4. Returns the original result unchanged
 *
 * @param delegate The composition engine to wrap.
 * @param store The graph projection store to apply edges to.
 * @param translator The translator to convert composition results to graph changes.
 */
internal class GraphEmittingCompositionEngine(
    private val delegate: CompositionEngine,
    private val store: GraphProjectionStore,
    private val translator: CompositionToGraphTranslator = CompositionToGraphTranslator(),
) : CompositionEngine by delegate {

    /**
     * Composes a pipeline definition and emits graph edges.
     *
     * @param request The composition request.
     * @param catalog The catalog source for resolving imports.
     * @param provenance The provenance sink for recording resolution provenance.
     * @return The composition result from the delegate.
     */
    override fun compose(
        request: CompositionRequest,
        catalog: dev.rubentxu.pipelattice.compose.ports.CatalogSource,
        provenance: dev.rubentxu.pipelattice.compose.ports.ProvenanceSink,
    ): CompositionResult {
        val result = delegate.compose(request, catalog, provenance)
        val changeSet = translator.translate(result)
        store.apply(changeSet)
        return result
    }
}
