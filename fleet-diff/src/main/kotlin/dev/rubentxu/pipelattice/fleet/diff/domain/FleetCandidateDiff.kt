package dev.rubentxu.pipelattice.fleet.diff.domain

import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphChangeSet
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.StructuralDiff
import dev.rubentxu.pipelattice.graph.ports.GraphProjectionStore

/**
 * Orchestrates diff analysis between two fleet snapshots.
 *
 * Takes a [SnapshotRepository] (for loading named snapshots) and a
 * [GraphProjectionStore] (for computing affected subgraphs) and produces
 * a [FleetDiffReport] with seven sections.
 */
public class FleetCandidateDiff(
    private val snapshotRepo: SnapshotRepository,
    private val graphStore: GraphProjectionStore,
) {

    /**
     * Computes the diff between baseline and candidate snapshots.
     *
     * @param baseline A string reference to the baseline snapshot.
     * @param candidate A string reference to the candidate snapshot.
     * @return A [FleetDiffReport] with all seven sections populated.
     * @throws IllegalArgumentException if either reference is not found.
     */
    public fun diff(baseline: String, candidate: String): FleetDiffReport {
        val baselineSnapshot = snapshotRepo.load(baseline)
            ?: throw IllegalArgumentException("Baseline snapshot not found: $baseline")
        val candidateSnapshot = snapshotRepo.load(candidate)
            ?: throw IllegalArgumentException("Candidate snapshot not found: $candidate")

        val affected = computeAffected(baselineSnapshot, candidateSnapshot)
        val effective = computeEffective(affected)
        val invalid = computeInvalid(baselineSnapshot, effective)
        val newViolations = (candidateSnapshot.policyViolations() - baselineSnapshot.policyViolations()).toList()
        val resolved = (baselineSnapshot.policyViolations() - candidateSnapshot.policyViolations()).toList()
        val providerChanges = effective.filterByKind(EdgeKind.GOVERNED_BY)
        val localOverrides = effective.filterByKind(EdgeKind.OVERRIDES)

        return FleetDiffReport(
            affectedProjects = affected.affectedNodes.filterIsInstance<GraphNode.Project>().toSet(),
            effectiveChanges = effective,
            invalidPlans = invalid,
            newPolicyViolations = newViolations,
            resolvedPolicyViolations = resolved,
            providerChanges = providerChanges,
            localOverrides = localOverrides,
        )
    }

    /**
     * Computes the structural diff between baseline and candidate.
     */
    private fun computeAffected(baseline: GraphSnapshot, candidate: GraphSnapshot): StructuralDiff {
        return StructuralDiff.diff(baseline, candidate)
    }

    /**
     * Filters the structural diff to produce effective changes.
     *
     * Effective changes include GOVERNED_BY and OVERRIDES to support
     * providerChanges and localOverrides reporting.
     */
    private fun computeEffective(diff: StructuralDiff): List<FleetDiffChange> {
        val effectiveKinds = setOf(
            EdgeKind.SELECTS,
            EdgeKind.IMPORTS,
            EdgeKind.OVERRIDES,
            EdgeKind.PATCHES,
            EdgeKind.REQUIRES,
            EdgeKind.GOVERNED_BY,
        )

        val addedChanges = diff.addedEdges.map { edge ->
            FleetDiffChange.Added(edge.source, edge.target, edge.kind)
        }
        val removedChanges = diff.removedEdges.map { edge ->
            FleetDiffChange.Removed(edge.source, edge.target, edge.kind)
        }

        return (addedChanges + removedChanges).filter { change ->
            change.kind in effectiveKinds
        }
    }

    /**
     * Computes invalid plans from the effective changes.
     *
     * A plan is invalid if its project was affected by removed edges.
     * For SELECTS/IMPORTS edges, the affected node is the target (Project).
     * For OVERRIDES/PATCHES edges, the affected node is the source (Project).
     */
    private fun computeInvalid(baseline: GraphSnapshot, effective: List<FleetDiffChange>): Set<PlanReference> {
        val removedSelectsOrImports = effective.filterIsInstance<FleetDiffChange.Removed>()
            .filter { it.kind in setOf(EdgeKind.SELECTS, EdgeKind.IMPORTS, EdgeKind.REQUIRES) }
            .map { it.target }
            .filterIsInstance<GraphNode.Project>()
            .map { it.id }
            .toSet()

        val removedOverridesOrPatches = effective.filterIsInstance<FleetDiffChange.Removed>()
            .filter { it.kind in setOf(EdgeKind.OVERRIDES, EdgeKind.PATCHES) }
            .map { it.source }
            .filterIsInstance<GraphNode.Project>()
            .map { it.id }
            .toSet()

        val affectedProjectIds = removedSelectsOrImports + removedOverridesOrPatches

        return baseline.nodes
            .filterIsInstance<GraphNode.ResolvedPipelinePlan>()
            .filter { plan -> plan.projectId in affectedProjectIds }
            .map { PlanReference(it.projectId, it.planDigest) }
            .toSet()
    }

    private fun List<FleetDiffChange>.filterByKind(kind: EdgeKind): List<FleetDiffChange> {
        return filter { it.kind == kind }
    }

    /**
     * Extracts policy violations from a snapshot.
     *
     * A-min provides a stub returning empty; full implementation deferred
     * to when the compiler emits violation data.
     */
    private fun GraphSnapshot.policyViolations(): Set<PolicyViolation> {
        // A-min stub: no policy violations tracked yet
        return emptySet()
    }
}
