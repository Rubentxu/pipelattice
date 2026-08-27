package dev.rubentxu.pipelattice.release.scm

import java.nio.file.Path

/**
 * Request to checkout a revision from a repository.
 *
 * @property repository The repository reference (e.g. `git://example/repo`).
 * @property revisionHint The revision to checkout (branch, tag, commit SHA).
 */
public data class CheckoutRequest(
    public val repository: RepositoryRef,
    public val revisionHint: String,
)

/**
 * Result of a successful checkout operation.
 *
 * @property workingDirectory Local path where the revision was checked out.
 * @property revision The resolved revision that was checked out (may differ from hint).
 */
public data class CheckoutResult(
    public val workingDirectory: Path,
    public val revision: String,
) {
    public sealed interface Success {
        public val workingDirectory: Path
        public val revision: String

        public data class CheckoutOk(
            override val workingDirectory: Path,
            override val revision: String,
        ) : Success
    }
}

/**
 * Reference to a repository in URI form.
 *
 * @property value The URI string, e.g. `git://example/repo`.
 */
public @JvmInline value class RepositoryRef(public val value: String) {
    init {
        require(value.isNotBlank()) { "RepositoryRef.value must not be blank" }
    }

    override fun toString(): String = value

    public companion object {
        private val SCHEME_PATTERN = Regex("^[a-z]+://[^#?]+$")

        public fun parse(raw: String): RepositoryRef {
            require(raw.isNotBlank()) { "RepositoryRef must not be blank" }
            require(SCHEME_PATTERN.matches(raw)) {
                "RepositoryRef must match <scheme>://<path>: $raw"
            }
            return RepositoryRef(raw)
        }
    }
}
