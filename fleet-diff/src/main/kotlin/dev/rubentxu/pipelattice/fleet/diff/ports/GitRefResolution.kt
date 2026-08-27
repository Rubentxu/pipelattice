package dev.rubentxu.pipelattice.fleet.diff.ports

/**
 * Result of resolving a git ref to a commit SHA.
 *
 * Internal port surface used by [dev.rubentxu.pipelattice.fleet.diff.repository.GitSnapshotRepository].
 * Not exposed in the public API.
 *
 * @see gitRevParse
 */
public sealed interface GitRefResolution {

    /**
     * The ref was successfully resolved to a full SHA.
     *
     * @property sha The 40-hex-character commit SHA.
     */
    public data class Resolved(val sha: String) : GitRefResolution

    /**
     * The ref was not found in the repository.
     *
     * Corresponds to `git` exiting with code 128 and stderr containing "bad revision".
     */
    public object NotFound : GitRefResolution

    /**
     * An I/O error occurred while running git (not a git repository, binary missing, etc.).
     *
     * @property cause A human-readable description of the error.
     */
    public data class IoError(val cause: String) : GitRefResolution
}
