package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotRepositoryThreadSafeTest {

    @Test
    fun `concurrent stores and loads are safe`() {
        val repo = InMemorySnapshotRepository()
        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(100)
        val successCount = AtomicInteger(0)

        // Store 100 different snapshots concurrently
        repeat(100) { i ->
            executor.submit {
                try {
                    val snapshot = GraphSnapshot(
                        nodes = emptySet(),
                        edges = emptySet(),
                        fingerprint = PlanFingerprint("%064d".format(i)),
                    )
                    repo.store("key-$i", snapshot)
                    successCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        assertEquals(100, successCount.get())

        // Verify all 100 snapshots can be loaded
        repeat(100) { i ->
            val loaded = repo.load("key-$i")
            assertEquals("%064d".format(i), loaded?.fingerprint?.value)
        }
    }

    @Test
    fun `concurrent loads of same key are safe`() {
        val repo = InMemorySnapshotRepository()

        val snapshot = GraphSnapshot(
            nodes = emptySet(),
            edges = emptySet(),
            fingerprint = PlanFingerprint("a".repeat(64)),
        )
        repo.store("shared-key", snapshot)

        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(100)
        val loadCount = AtomicInteger(0)

        repeat(100) {
            executor.submit {
                try {
                    val loaded = repo.load("shared-key")
                    if (loaded != null) {
                        loadCount.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        assertEquals(100, loadCount.get())
    }
}
