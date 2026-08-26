package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.fleet.diff.domain.SnapshotRepository
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory [SnapshotRepository] implementation.
 *
 * Backed by a [ConcurrentHashMap] to support concurrent reads and writes
 * from multiple threads.
 */
public class InMemorySnapshotRepository : SnapshotRepository {

    private val snapshots: ConcurrentHashMap<String, GraphSnapshot> = ConcurrentHashMap()

    override fun load(ref: String): GraphSnapshot? = snapshots[ref]

    /**
     * Stores a snapshot under the given reference.
     *
     * @param ref The reference key (e.g., "baseline", "candidate").
     * @param snapshot The [GraphSnapshot] to store.
     */
    public fun store(ref: String, snapshot: GraphSnapshot) {
        snapshots[ref] = snapshot
    }
}
