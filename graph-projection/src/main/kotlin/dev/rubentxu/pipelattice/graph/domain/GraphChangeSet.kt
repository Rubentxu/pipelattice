package dev.rubentxu.pipelattice.graph.domain

/**
 * A delta to apply to a [dev.rubentxu.pipelattice.graph.ports.GraphProjectionStore].
 *
 * Edges are referenced by their full [Edge] triple; nodes are inferred from
 * edges (any node that appears as source or target of any applied edge is
 * present in the resulting snapshot).
 */
public data class GraphChangeSet(
    public val addedEdges: List<Edge>,
    public val removedEdges: List<Edge>,
)
