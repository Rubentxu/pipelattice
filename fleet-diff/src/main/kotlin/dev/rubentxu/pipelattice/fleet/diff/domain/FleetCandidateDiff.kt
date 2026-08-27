package dev.rubentxu.pipelattice.fleet.diff.domain

import dev.rubentxu.pipelattice.graph.domain.EdgeKind
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
 *
 * ## m16 additions
 * - Plan validation pass via [compileAffectedValidator] populates [invalidPlans]
 *   as the PRIMARY signal; the removed-edge heuristic becomes secondary.
 * - Policy violations via [policySource] seam (empty in m16; spec-M3 deferred).
 */
public class FleetCandidateDiff(
    private val snapshotRepo: SnapshotRepository,
    private val graphStore: GraphProjectionStore,
    private val policySource: PolicyViolationSource = PolicyViolationSource(snapshotRepo),
    private val compileAffectedValidator: CompileAffectedValidator = CompileAffectedValidator(),
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

        // Primary signal: compile-affected validation
        val affectedProjects = affected.affectedNodes.filterIsInstance<GraphNode.Project>().toSet()
        val primaryInvalidPlans = compileAffectedValidator(affectedProjects.map { it.id }.toSet())

        // Secondary signal: removed-edge heuristic (for backward compatibility)
        val (secondaryInvalidPlans, secondaryHeuristicReport) = computeSecondaryHeuristic(baselineSnapshot, effective)

        // Merge primary and secondary (secondary is flagged in report for consumer awareness)
        val allInvalidPlans = primaryInvalidPlans + secondaryInvalidPlans

        // Policy violations via seam (empty in m16; D4 debt item)
        val baselineViolations = policySource()
        val candidateViolations = policySource()
        val newViolations = (candidateViolations - baselineViolations).toList()
        val resolved = (baselineViolations - candidateViolations).toList()

        val providerChanges = effective.filterByKind(EdgeKind.GOVERNED_BY)
        val localOverrides = effective.filterByKind(EdgeKind.OVERRIDES)

        return FleetDiffReport(
            affectedProjects = affectedProjects.toSet(),
            effectiveChanges = effective,
            invalidPlans = allInvalidPlans,
            newPolicyViolations = newViolations,
            resolvedPolicyViolations = resolved,
            providerChanges = providerChanges,
            localOverrides = localOverrides,
            secondaryHeuristic = secondaryHeuristicReport,
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
     * Computes invalid plans from the removed-edge heuristic (secondary signal).
     *
     * A plan is invalid if its project was affected by removed edges.
     * For SELECTS/IMPORTS edges, the affected node is the target (Project).
     * For OVERRIDES/PATCHES edges, the affected node is the source (Project).
     *
     * Returns a pair of (secondary invalid plans, secondary heuristic report).
     */
    private fun computeSecondaryHeuristic(
        baseline: GraphSnapshot,
        effective: List<FleetDiffChange>,
    ): Pair<Set<PlanReference>, SecondaryHeuristicReport> {
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

        val secondaryPlans = baseline.nodes
            .filterIsInstance<GraphNode.ResolvedPipelinePlan>()
            .filter { plan -> plan.projectId in affectedProjectIds }
            .map { PlanReference(it.projectId, it.planDigest) }
            .toSet()

        val secondaryReport = SecondaryHeuristicReport(
            invalidPlanIds = affectedProjectIds.toList()
        )

        return secondaryPlans to secondaryReport
    }

    private fun List<FleetDiffChange>.filterByKind(kind: EdgeKind): List<FleetDiffChange> {
        return filter { it.kind == kind }
    }
}
