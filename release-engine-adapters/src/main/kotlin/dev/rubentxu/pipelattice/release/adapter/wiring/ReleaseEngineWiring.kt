package dev.rubentxu.pipelattice.release.adapter.wiring

import dev.rubentxu.pipelattice.foundation.secret.SecretResolver
import dev.rubentxu.pipelattice.release.adapter.artifact.LocalFSArtifactRepository
import dev.rubentxu.pipelattice.release.adapter.release.GitTagBasedReleaseManager
import dev.rubentxu.pipelattice.release.adapter.scm.JGitScmSource
import dev.rubentxu.pipelattice.release.adapter.secret.EnvSecretResolver
import dev.rubentxu.pipelattice.release.artifact.ArtifactRepository
import dev.rubentxu.pipelattice.release.release.ReleaseManager
import dev.rubentxu.pipelattice.release.scm.ScmSource
import java.nio.file.Files
import java.nio.file.Path

/**
 * Composition root for the release engine adapters.
 *
 * Provides a factory to create a coherent set of adapters from environment variables
 * and directory roots. This is the single entry point for wiring the four real adapters
 * into an application.
 *
 * ## Usage
 * ```kotlin
 * val wiring = ReleaseEngineWiring.of(
 *     env = System.getenv(),
 *     artifactRoot = Path.of("/var/lib/pipelattice/artifacts"),
 *     repoRoots = mapOf(
 *         "scm" to Path.of("/var/lib/pipelattice/scm"),
 *         "policy" to Path.of("/var/lib/pipelattice/policy"),
 *     ),
 * )
 * val scm: ScmSource = wiring.scm
 * val repo: ArtifactRepository = wiring.repo
 * val release: ReleaseManager = wiring.release
 * val secrets: SecretResolver = wiring.secrets
 * ```
 *
 * ## Fail-fast
 * The factory throws [IllegalArgumentException] with a non-credential-shaped message
 * if any required directory is missing or not writable.
 */
public object ReleaseEngineWiring {

    /**
     * Creates a [ReleaseEngineWiring] instance from environment variables and directory roots.
     *
     * @param env Environment variables map. Defaults to [System.getenv].
     * @param artifactRoot Root directory for artifact storage. Must be writable.
     * @param repoRoots Map of repository name to root directory. Expected keys: `scm`, `policy`.
     * @return A [ReleaseEngineWiring] holding the four composed adapters.
     * @throws IllegalArgumentException if any directory is missing or not writable.
     */
    public fun of(
        env: Map<String, String> = System.getenv(),
        artifactRoot: Path,
        repoRoots: Map<String, Path>,
    ): ReleaseEngineWiringHolder {
        // Fail-fast: verify required directories
        requireDirectoryWritable(artifactRoot, "artifactRoot")

        val scmRoot = repoRoots["scm"]
            ?: throw IllegalArgumentException("repoRoots must contain 'scm' key")
        requireDirectoryWritable(scmRoot, "repoRoots[scm]")

        // Compose adapters
        val secrets: SecretResolver = EnvSecretResolver()
        val scm: ScmSource = JGitScmSource(secrets)
        val repo: ArtifactRepository = LocalFSArtifactRepository(artifactRoot)
        val release: ReleaseManager = GitTagBasedReleaseManager(scm, secrets)

        return ReleaseEngineWiringHolder(scm, repo, release, secrets)
    }

    private fun requireDirectoryWritable(path: Path, name: String) {
        require(path.isAbsolute) {
            "release-engine-wiring: ${name} must be an absolute path"
        }
        require(Files.exists(path)) {
            "release-engine-wiring: ${name} does not exist: ${path}"
        }
        require(Files.isDirectory(path)) {
            "release-engine-wiring: ${name} is not a directory: ${path}"
        }
        require(Files.isWritable(path)) {
            "release-engine-wiring: ${name} is not writable: ${path}"
        }
    }
}

/**
 * Holder for the four release engine adapters produced by [ReleaseEngineWiring.of].
 *
 * @property scm The configured [ScmSource] adapter (JGitScmSource).
 * @property repo The configured [ArtifactRepository] adapter (LocalFSArtifactRepository).
 * @property release The configured [ReleaseManager] adapter (GitTagBasedReleaseManager).
 * @property secrets The configured [SecretResolver] adapter (EnvSecretResolver).
 */
public class ReleaseEngineWiringHolder(
    public val scm: ScmSource,
    public val repo: ArtifactRepository,
    public val release: ReleaseManager,
    public val secrets: SecretResolver,
)
