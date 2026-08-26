package dev.rubentxu.pipelattice.graph.ports

import dev.rubentxu.pipelattice.graph.domain.GraphChangeSet
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot

/**
 * Port for storing the reactive configuration graph projection.
 *
 * Mirrors the interface declared in spec 01_ARCHITECTURE.md §6:
 *
 * ```
 * interface GraphProjectionStore {
 *     fun apply(changeSet: GraphChangeSet)
 *     fun snapshot(): GraphSnapshot
 * }
 * ```
 *
 * ## Contract
 * - [apply] is **synchronous** (no suspend) and mutates the projection in place.
 * - [snapshot] returns an **immutable** point-in-time view; subsequent [apply]
 *   calls do not retroactively change previously returned snapshots.
 * - The store is **single-tenant** in A-min; concurrent access is the caller's
 *   responsibility (deferred to A-lite when concurrency primitives are needed).
 *
 * ## Thread safety
 * Implementations are expected to be thread-safe if used concurrently.
 * [dev.rubentxu.pipelattice.graph.store.InMemoryGraphProjectionStore] is not
 * thread-safe in A-min.
 *
 * ## Future evolution
 * The interface permits future persistent backends (Postgres, JGraphT-backed)
 * without changing call sites. Persistence is deferred per ADR-0014 until
 * scale metrics demand it.
 */
public interface GraphProjectionStore {

    /**
     * Applies [changeSet] to the projection.
     *
     * Edges in [GraphChangeSet.addedEdges] are added; edges in
     * [GraphChangeSet.removedEdges] are removed. Edges present in both
     * lists are treated as no-op (add wins per last-writer semantics).
     */
    public fun apply(changeSet: GraphChangeSet)

    /**
     * Returns an immutable snapshot of the current projection.
     *
     * The returned [GraphSnapshot] is detached from subsequent [apply] calls.
     */
    public fun snapshot(): GraphSnapshot
}
