package dev.rubentxu.pipelattice.release.scm

import java.nio.file.Path

/**
 * Fixture for push arrival testing: a bare repository paired with a working clone.
 *
 * @property bareDir Path to the bare repository that receives pushes.
 * @property workDir Path to the working clone that pushes to the bare repo.
 */
public data class PushFixture(
    public val bareDir: Path,
    public val workDir: Path,
)

/**
 * Request to push commits to a remote repository.
 *
 * @property repository The repository reference.
 * @property remote The remote name (e.g. `origin`).
 * @property refSpecs The refs to push (e.g. `main`, `refs/tags/v1.0.0`).
 */
public data class PushRequest(
    public val repository: RepositoryRef,
    public val remote: String,
    public val refSpecs: List<String>,
)

/**
 * Result of a successful push operation.
 *
 * @property pushedRefs The refs that were successfully pushed.
 * @property updatedRef The primary updated ref (if applicable).
 */
public data class PushResult(
    public val pushedRefs: List<String>,
    public val updatedRef: String?,
) {
    public sealed interface Success {
        public val pushedRefs: List<String>
        public val updatedRef: String?

        public data class PushOk(
            override val pushedRefs: List<String>,
            override val updatedRef: String?,
        ) : Success
    }
}
