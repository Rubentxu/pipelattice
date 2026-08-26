package dev.rubentxu.pipelattice.graph.store

import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphChangeSet
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import dev.rubentxu.pipelattice.graph.ports.GraphProjectionStore
import java.security.MessageDigest

/**
 * In-memory [GraphProjectionStore] implementation.
 *
 * Per spec 04 §11 + ADR-0014: V1 uses in-memory storage. Persistence is
 * introduced only after scale metrics demonstrate real need.
 *
 * Backed by a [LinkedHashSet] to preserve insertion order, which is then
 * sorted by canonical [Edge] representation to produce a deterministic
 * fingerprint regardless of apply order.
 *
 * ## Thread safety
 * Not thread-safe in A-min. Use single-threaded or wrap externally.
 *
 * ## Determinism
 * [snapshot] returns nodes and edges sorted by their canonical string form,
 * so [PlanFingerprint] is stable across runs for identical logical graphs.
 */
public class InMemoryGraphProjectionStore : GraphProjectionStore {

    private val edges: LinkedHashSet<Edge> = LinkedHashSet()

    /**
     * Tracks the number of apply() calls for invalidation purposes.
     * AdjacencyIndex uses this to detect stale caches.
     */
    internal var applyVersion: Long = 0L
        private set

    override fun apply(changeSet: GraphChangeSet) {
        // Remove first, then add — so "add wins" semantics hold even when
        // the same edge is in both lists.
        changeSet.removedEdges.forEach(edges::remove)
        changeSet.addedEdges.forEach(edges::add)
        applyVersion++
    }

    override fun snapshot(): GraphSnapshot {
        // Sort by canonical form for deterministic iteration + fingerprint.
        val sortedEdges = edges.sortedBy(::canonicalForm)
        val nodes = sortedEdges.flatMap { listOf(it.source, it.target) }.toSet()
        val fingerprint = computeFingerprint(nodes, sortedEdges)
        return GraphSnapshot(
            nodes = nodes,
            edges = sortedEdges.toSet(),
            fingerprint = fingerprint,
        )
    }

    private fun canonicalForm(edge: Edge): String =
        "${canonicalForm(edge.source)}\t${edge.kind::class.simpleName}\t${canonicalForm(edge.target)}"

    private fun canonicalForm(node: GraphNode): String = when (node) {
        is GraphNode.Project -> "Project(${node.id.canonicalForm})"
        is GraphNode.Component -> "Component(${node.id.canonicalForm},owner=${node.owner.canonicalForm})"
        is GraphNode.PipelineProfile -> "PipelineProfile(${node.id.canonicalForm})"
        is GraphNode.ConfigSource -> "ConfigSource(${node.path},hash=${node.contentHash})"
        is GraphNode.ResolvedPipelinePlan -> "ResolvedPipelinePlan(${node.projectId.canonicalForm},digest=${node.planDigest})"
    }

    private fun computeFingerprint(nodes: Set<GraphNode>, edges: List<Edge>): PlanFingerprint {
        val digest = MessageDigest.getInstance("SHA-256")
        nodes.sortedBy { canonicalForm(it) }.forEach {
            digest.update(canonicalForm(it).toByteArray())
            digest.update(0.toByte())
        }
        edges.forEach {
            digest.update(canonicalForm(it).toByteArray())
            digest.update(0.toByte())
        }
        return PlanFingerprint(digest.digest().joinToString("") { "%02x".format(it) })
    }
}
