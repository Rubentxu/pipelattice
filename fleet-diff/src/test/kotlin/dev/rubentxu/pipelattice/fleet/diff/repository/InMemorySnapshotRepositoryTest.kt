package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.fleet.diff.domain.SnapshotRepository
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemorySnapshotRepositoryTest {

    @Test
    fun `load returns stored snapshot`() {
        val repo = InMemorySnapshotRepository()

        val snapshot = GraphSnapshot(
            nodes = emptySet(),
            edges = emptySet(),
            fingerprint = PlanFingerprint("a".repeat(64)),
        )

        repo.store("ref1", snapshot)

        val loaded = repo.load("ref1")

        assertEquals(snapshot, loaded)
    }

    @Test
    fun `load returns null for unknown reference`() {
        val repo = InMemorySnapshotRepository()

        val loaded = repo.load("unknown")

        assertNull(loaded)
    }

    @Test
    fun `store overwrites existing snapshot`() {
        val repo = InMemorySnapshotRepository()

        val snapshot1 = GraphSnapshot(
            nodes = emptySet(),
            edges = emptySet(),
            fingerprint = PlanFingerprint("a".repeat(64)),
        )

        val snapshot2 = GraphSnapshot(
            nodes = emptySet(),
            edges = emptySet(),
            fingerprint = PlanFingerprint("b".repeat(64)),
        )

        repo.store("ref1", snapshot1)
        repo.store("ref1", snapshot2)

        val loaded = repo.load("ref1")

        assertEquals(snapshot2, loaded)
    }
}
