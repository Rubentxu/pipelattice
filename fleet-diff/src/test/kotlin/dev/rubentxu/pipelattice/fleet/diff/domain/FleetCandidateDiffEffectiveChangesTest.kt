package dev.rubentxu.pipelattice.fleet.diff.domain

import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import dev.rubentxu.pipelattice.graph.store.InMemoryGraphProjectionStore
import dev.rubentxu.pipelattice.fleet.diff.repository.InMemorySnapshotRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FleetCandidateDiffEffectiveChangesTest {

    @Test
    fun `effective changes filtered by effective kinds`() {
        val repo = InMemorySnapshotRepository()
        val store = InMemoryGraphProjectionStore()

        val project = GraphNode.Project(ResourceRef("projects/example"))
        val profile = GraphNode.PipelineProfile(ResourceRef("profiles/java"))

        // DERIVED_FROM is NOT in effective kinds
        val derivedFromEdge = Edge(profile, project, EdgeKind.DERIVED_FROM)
        // SELECTS is in effective kinds
        val selectsEdge = Edge(profile, project, EdgeKind.SELECTS)

        val baseline = GraphSnapshot(
            nodes = setOf(project, profile),
            edges = emptySet(),
            fingerprint = PlanFingerprint("0".repeat(64)),
        )

        val candidate = GraphSnapshot(
            nodes = setOf(project, profile),
            edges = setOf(derivedFromEdge, selectsEdge),
            fingerprint = PlanFingerprint("1".repeat(64)),
        )

        repo.store("baseline", baseline)
        repo.store("candidate", candidate)

        val report = FleetCandidateDiff(repo, store).diff("baseline", "candidate")

        // Only SELECTS should be in effectiveChanges, not DERIVED_FROM
        assertEquals(1, report.effectiveChanges.size)
        assertTrue(report.effectiveChanges.all { it.kind == EdgeKind.SELECTS })
    }

    @Test
    fun `GOVERNED_BY edges appear in providerChanges`() {
        val repo = InMemorySnapshotRepository()
        val store = InMemoryGraphProjectionStore()

        val project = GraphNode.Project(ResourceRef("projects/example"))
        val profile = GraphNode.PipelineProfile(ResourceRef("profiles/java"))

        val governedByEdge = Edge(profile, project, EdgeKind.GOVERNED_BY)

        val baseline = GraphSnapshot(
            nodes = setOf(project, profile),
            edges = emptySet(),
            fingerprint = PlanFingerprint("0".repeat(64)),
        )

        val candidate = GraphSnapshot(
            nodes = setOf(project, profile),
            edges = setOf(governedByEdge),
            fingerprint = PlanFingerprint("1".repeat(64)),
        )

        repo.store("baseline", baseline)
        repo.store("candidate", candidate)

        val report = FleetCandidateDiff(repo, store).diff("baseline", "candidate")

        // GOVERNED_BY should be in providerChanges
        assertEquals(1, report.providerChanges.size)
        assertTrue(report.providerChanges.all { it.kind == EdgeKind.GOVERNED_BY })
    }

    @Test
    fun `OVERRIDES edges appear in localOverrides`() {
        val repo = InMemorySnapshotRepository()
        val store = InMemoryGraphProjectionStore()

        val project = GraphNode.Project(ResourceRef("projects/example"))
        val fragment = GraphNode.ConfigSource(java.nio.file.Path.of("/config"), "hash")

        val overridesEdge = Edge(project, fragment, EdgeKind.OVERRIDES)

        val baseline = GraphSnapshot(
            nodes = setOf(project, fragment),
            edges = emptySet(),
            fingerprint = PlanFingerprint("0".repeat(64)),
        )

        val candidate = GraphSnapshot(
            nodes = setOf(project, fragment),
            edges = setOf(overridesEdge),
            fingerprint = PlanFingerprint("1".repeat(64)),
        )

        repo.store("baseline", baseline)
        repo.store("candidate", candidate)

        val report = FleetCandidateDiff(repo, store).diff("baseline", "candidate")

        // OVERRIDES should be in localOverrides
        assertEquals(1, report.localOverrides.size)
        assertTrue(report.localOverrides.all { it.kind == EdgeKind.OVERRIDES })
    }
}
