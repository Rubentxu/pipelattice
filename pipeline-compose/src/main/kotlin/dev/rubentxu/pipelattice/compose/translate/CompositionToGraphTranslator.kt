package dev.rubentxu.pipelattice.compose.translate

import dev.rubentxu.pipelattice.compose.domain.CompositionResult
import dev.rubentxu.pipelattice.compose.domain.Layer
import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphChangeSet
import dev.rubentxu.pipelattice.graph.domain.GraphNode

/**
 * Translates a [CompositionResult] into a [GraphChangeSet] for the reactive configuration graph.
 *
 * This mapper implements the Q8 mapping table from the design:
 *
 * | CompositionResult source              | EdgeKind        | Direction         |
 * |-------------------------------------|-----------------|-------------------|
 * | ProfileImport.entry (PROFILE_IMPORT) | IMPORTS + EXTENDS | source → target   |
 * | Profile with selects (PROFILE)        | SELECTS          | profile → project |
 * | LocalOverride.entry (LOCAL)            | OVERRIDES        | local → pipeline  |
 *
 * Nodes are inferred from edges (source and target nodes are automatically included
 * in the resulting snapshot).
 */
internal class CompositionToGraphTranslator {

    /**
     * Translates a [CompositionResult] into a [GraphChangeSet].
     *
     * @param result The composition result to translate.
     * @return A [GraphChangeSet] containing the graph edges derived from provenance.
     */
    fun translate(result: CompositionResult): GraphChangeSet {
        val addedEdges = buildList<Edge> {
            for ((_, provenanceChain) in result.provenance) {
                for (provenance in provenanceChain) {
                    addProvenanceEdges(provenance, result.pipelineId, this)
                }
            }
        }

        return GraphChangeSet(
            addedEdges = addedEdges,
            removedEdges = emptyList(),
        )
    }

    private fun addProvenanceEdges(
        provenance: dev.rubentxu.pipelattice.compose.domain.Provenance,
        pipelineId: String,
        edges: MutableList<Edge>,
    ) {
        val sourceNode = provenance.source.resource.toGraphNode()
        val pipelineNode = GraphNode.Project(
            dev.rubentxu.pipelattice.foundation.ResourceRef.parse("catalog://pipelines/$pipelineId")
        )

        when (provenance.layer) {
            Layer.PROFILE_IMPORT -> {
                // ProfileImport.entry → IMPORTS + EXTENDS edges (source → target = imported → pipeline)
                edges.add(Edge(source = sourceNode, target = pipelineNode, kind = EdgeKind.IMPORTS))
                edges.add(Edge(source = sourceNode, target = pipelineNode, kind = EdgeKind.EXTENDS))
            }
            Layer.PROFILE -> {
                // Profile with selects → SELECTS edge (profile → project)
                // The effectiveValue contains the workflow/project reference
                val projectRef = provenance.effectiveValue?.let { value ->
                    when (value) {
                        is dev.rubentxu.pipelattice.resource.ParameterValue.StringValue ->
                            dev.rubentxu.pipelattice.foundation.ResourceRef.parse(value.value)
                        else -> null
                    }
                }
                if (projectRef != null) {
                    val projectNode = GraphNode.Project(projectRef)
                    edges.add(Edge(source = sourceNode, target = projectNode, kind = EdgeKind.SELECTS))
                }
            }
            Layer.LOCAL -> {
                // LocalOverride.entry → OVERRIDES edge (local → pipeline)
                edges.add(Edge(source = sourceNode, target = pipelineNode, kind = EdgeKind.OVERRIDES))
            }
        }
    }

    private fun dev.rubentxu.pipelattice.foundation.ResourceRef.toGraphNode(): GraphNode {
        return GraphNode.PipelineProfile(this)
    }
}
