package dev.rubentxu.pipelattice.release.scm

/**
 * Request to create a tag in a repository.
 *
 * @property repository The repository reference.
 * @property revision The revision to tag.
 * @property tagName The name of the tag to create.
 * @property message Optional tag message (annotation).
 */
public data class TagRequest(
    public val repository: RepositoryRef,
    public val revision: String,
    public val tagName: String,
    public val message: String? = null,
)

/**
 * Result of a successful tag operation.
 *
 * @property tagName The name of the created tag.
 * @property revision The revision that was tagged.
 */
public data class TagResult(
    public val tagName: String,
    public val revision: String,
) {
    public sealed interface Success {
        public val tagName: String
        public val revision: String

        public data class TagOk(
            override val tagName: String,
            override val revision: String,
        ) : Success
    }
}
