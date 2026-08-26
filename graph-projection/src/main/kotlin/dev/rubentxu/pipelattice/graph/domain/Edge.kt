package dev.rubentxu.pipelattice.graph.domain

/**
 * A directed, typed edge between two [GraphNode]s.
 *
 * Identity is the triple (source, target, kind); edges with identical triples
 * are considered the same edge in the snapshot set.
 */
public data class Edge(
    public val source: GraphNode,
    public val target: GraphNode,
    public val kind: EdgeKind,
)
