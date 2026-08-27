package dev.rubentxu.pipelattice.fleet.diff.domain

import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.foundation.ResourceRef
import java.time.Instant

/**
 * Result of comparing two fleet snapshots.
 *
 * Contains seven sections:
 * - affectedProjects: projects whose pipelines may be affected by the change
 * - effectiveChanges: all changes that have direct effect
 * - invalidPlans: plans that are no longer valid after the change
 * - newPolicyViolations: violations introduced by the candidate
 * - resolvedPolicyViolations: violations that existed in baseline but are fixed in candidate
 * - providerChanges: effective changes filtered to GOVERNED_BY edges
 * - localOverrides: effective changes filtered to OVERRIDES edges
 * - secondaryHeuristic: results from the removed-edge heuristic (secondary signal)
 */
public data class FleetDiffReport(
    val affectedProjects: Set<GraphNode>,
    val effectiveChanges: List<FleetDiffChange>,
    val invalidPlans: Set<PlanReference>,
    val newPolicyViolations: List<PolicyViolation>,
    val resolvedPolicyViolations: List<PolicyViolation>,
    val providerChanges: List<FleetDiffChange>,
    val localOverrides: List<FleetDiffChange>,
    val secondaryHeuristic: SecondaryHeuristicReport? = null,
    val schema: String = "fleet-diff/v1",
    val generatedAt: Instant = Instant.now(),
)

/**
 * Report of invalid plans detected via the removed-edge heuristic (secondary signal).
 *
 * The removed-edge heuristic detects invalid plans by finding projects that were targets
 * of removed SELECTS/IMPORTS/REQUIRES edges or sources of removed OVERRIDES/PATCHES edges.
 * This is a secondary signal - the primary signal is the compile-affected validation pass.
 */
public data class SecondaryHeuristicReport(
    val invalidPlanIds: List<ResourceRef> = emptyList(),
)

/**
 * Reference to a plan by its project and digest.
 *
 * @param projectId The project ref for this plan.
 * @param planDigest The digest of the composed plan.
 * @param diagnosticCode Optional diagnostic code when this plan is invalid due to
 *        a composition failure. Set to `E-COMPOSE-AFFECTED-001` when the compile-affected
 *        validator detects a composition error. Null when the plan is valid.
 */
public data class PlanReference(
    val projectId: ResourceRef,
    val planDigest: String,
    val diagnosticCode: String? = null,
)

/**
 * A policy violation detected in a snapshot.
 */
public data class PolicyViolation(
    val projectId: ResourceRef,
    val rule: String,
    val message: String,
)

/**
 * Change to a graph edge, categorized by type.
 */
public sealed class FleetDiffChange {
    public abstract val source: GraphNode
    public abstract val target: GraphNode
    public abstract val kind: EdgeKind

    public data class Added(
        override val source: GraphNode,
        override val target: GraphNode,
        override val kind: EdgeKind,
    ) : FleetDiffChange()

    public data class Removed(
        override val source: GraphNode,
        override val target: GraphNode,
        override val kind: EdgeKind,
    ) : FleetDiffChange()

    public data class Modified(
        override val source: GraphNode,
        override val target: GraphNode,
        override val kind: EdgeKind,
    ) : FleetDiffChange()
}
