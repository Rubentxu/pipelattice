package dev.rubentxu.pipelattice.graph.domain

/**
 * An immutable point-in-time view of the graph.
 *
 * [nodes] are derived from edges; [fingerprint] is a deterministic hash of
 * the canonical serialization of nodes + edges.
 */
public data class GraphSnapshot(
    public val nodes: Set<GraphNode>,
    public val edges: Set<Edge>,
    public val fingerprint: PlanFingerprint,
)
