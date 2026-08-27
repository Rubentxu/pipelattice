package dev.rubentxu.pipelattice.fleet.diff.domain

import dev.rubentxu.pipelattice.foundation.ResourceRef

/**
 * Validates affected projects by re-composing their YAML sets.
 *
 * For each affected project, this validator would run the composition engine
 * and collect projects whose composition fails (ERROR diagnostics).
 * These would become [PlanReference] entries in [FleetDiffReport.invalidPlans].
 *
 * ## m16 limitation
 * In m16, [invoke] returns [emptySet] because the snapshot does not store
 * YAML source content. A future cycle will store sources in the snapshot and
 * enable real re-composition validation.
 *
 * ## Diagnostic codes
 * - `E-COMPOSE-AFFECTED-001`: composition failed for the affected project
 *
 * @see FleetDiffReport
 */
public class CompileAffectedValidator : (Set<ResourceRef>) -> Set<PlanReference> {

    /**
     * Validates the given affected projects by re-composing their YAML sets.
     *
     * In m16, this returns [emptySet] because snapshots do not store YAML source
     * content. Future cycles will enable real re-composition when sources are
     * persisted in the snapshot.
     *
     * @param affectedProjects The set of affected project refs to validate.
     * @return An empty set in m16; future cycles return [PlanReference] for failed compositions.
     */
    override fun invoke(affectedProjects: Set<ResourceRef>): Set<PlanReference> {
        // m16: cannot re-compose without stored YAML sources in the snapshot.
        // Real re-composition validation is deferred to a future cycle.
        return emptySet()
    }
}
