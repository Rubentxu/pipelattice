package dev.rubentxu.pipelattice.fleet.diff.domain

import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot

/**
 * Port for loading [GraphSnapshot]s by reference.
 *
 * Implementations may be in-memory (for testing/CLI) or persistent
 * (for CI/CD integration).
 */
public interface SnapshotRepository {
    /**
     * Loads the snapshot identified by [ref].
     *
     * @param ref A string reference (e.g., "catalog://baseline@v1").
     * @return The [GraphSnapshot] or null if not found.
     */
    public fun load(ref: String): GraphSnapshot?
}
