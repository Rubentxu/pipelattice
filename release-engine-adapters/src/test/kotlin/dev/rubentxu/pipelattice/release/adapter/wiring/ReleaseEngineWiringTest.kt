package dev.rubentxu.pipelattice.release.adapter.wiring

import dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor
import dev.rubentxu.pipelattice.foundation.secret.SecretResolver
import dev.rubentxu.pipelattice.release.artifact.ArtifactRepository
import dev.rubentxu.pipelattice.release.release.ReleaseManager
import dev.rubentxu.pipelattice.release.scm.ScmSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * M-1: Tests for ReleaseEngineWiring covering S27/S28.
 * S27: fail-fast requireDirectoryWritable; IllegalArgumentException on invalid config.
 * S28: wiring yields functioning adapters with correct descriptors.
 */
class ReleaseEngineWiringTest {

    @TempDir
    lateinit var tempDir: Path

    private val env = mapOf(
        "GITHUB_ACCESS_TOKEN" to "synthetic-payload-X",
    )

    // ----- S27: fail-fast on invalid config -----

    @Test
    fun `wiring throws IllegalArgumentException when artifactRoot does not exist`() {
        val nonexistent = tempDir.resolve("nonexistent")
        val repoRoots = mapOf("scm" to tempDir.resolve("scm"))

        val exception = kotlin.test.assertFailsWith<IllegalArgumentException> {
            ReleaseEngineWiring.of(env, nonexistent, repoRoots)
        }
        assertTrue(exception.message?.contains("does not exist") == true)
        assertTrue(exception.message?.contains("artifactRoot") == true)
    }

    @Test
    fun `wiring throws IllegalArgumentException when artifactRoot is not writable`() {
        val readOnlyDir = tempDir.resolve("readonly")
        Files.createDirectories(readOnlyDir)
        readOnlyDir.toFile().setWritable(false)
        try {
            val repoRoots = mapOf("scm" to tempDir.resolve("scm"))
            val exception = kotlin.test.assertFailsWith<IllegalArgumentException> {
                ReleaseEngineWiring.of(env, readOnlyDir, repoRoots)
            }
            assertTrue(exception.message?.contains("not writable") == true)
            assertTrue(exception.message?.contains("artifactRoot") == true)
        } finally {
            readOnlyDir.toFile().setWritable(true)
        }
    }

    @Test
    fun `wiring throws IllegalArgumentException when scm root is missing`() {
        val artifactRoot = tempDir.resolve("artifacts")
        Files.createDirectories(artifactRoot)
        val repoRoots = mapOf<String, Path>() // missing 'scm' key

        val exception = kotlin.test.assertFailsWith<IllegalArgumentException> {
            ReleaseEngineWiring.of(env, artifactRoot, repoRoots)
        }
        assertTrue(exception.message?.contains("'scm' key") == true)
    }

    @Test
    fun `wiring throws IllegalArgumentException when scm root does not exist`() {
        val artifactRoot = tempDir.resolve("artifacts")
        Files.createDirectories(artifactRoot)
        val nonexistent = tempDir.resolve("nonexistent-scm")
        val repoRoots = mapOf("scm" to nonexistent)

        val exception = kotlin.test.assertFailsWith<IllegalArgumentException> {
            ReleaseEngineWiring.of(env, artifactRoot, repoRoots)
        }
        assertTrue(exception.message?.contains("does not exist") == true)
        assertTrue(exception.message?.contains("repoRoots[scm]") == true)
    }

    // ----- S28: wiring yields functioning adapters -----

    @Test
    fun `wiring returns holder with non-null adapters`() {
        val artifactRoot = tempDir.resolve("artifacts")
        Files.createDirectories(artifactRoot)
        val scmRoot = tempDir.resolve("scm")
        Files.createDirectories(scmRoot)
        val repoRoots = mapOf("scm" to scmRoot)

        val holder = ReleaseEngineWiring.of(env, artifactRoot, repoRoots)

        assertNotNull(holder.scm)
        assertNotNull(holder.repo)
        assertNotNull(holder.release)
        assertNotNull(holder.secrets)

        assertIs<ScmSource>(holder.scm)
        assertIs<ArtifactRepository>(holder.repo)
        assertIs<ReleaseManager>(holder.release)
        assertIs<SecretResolver>(holder.secrets)
    }

    @Test
    fun `wiring adapters expose correct capability descriptors`() {
        val artifactRoot = tempDir.resolve("artifacts")
        Files.createDirectories(artifactRoot)
        val scmRoot = tempDir.resolve("scm")
        Files.createDirectories(scmRoot)
        val repoRoots = mapOf("scm" to scmRoot)

        val holder = ReleaseEngineWiring.of(env, artifactRoot, repoRoots)

        // ScmSource capabilities
        assertNotNull(holder.scm.descriptor(dev.rubentxu.pipelattice.release.scm.ScmSource.SCM_CHECKOUT_V1))
        assertNotNull(holder.scm.descriptor(dev.rubentxu.pipelattice.release.scm.ScmSource.SCM_TAG_V1))
        assertNotNull(holder.scm.descriptor(dev.rubentxu.pipelattice.release.scm.ScmSource.SCM_PUSH_V1))

        // ArtifactRepository capabilities
        assertNotNull(holder.repo.descriptor(dev.rubentxu.pipelattice.release.artifact.ArtifactRepository.ARTIFACT_PUBLISH_V1))
        assertNotNull(holder.repo.descriptor(dev.rubentxu.pipelattice.release.artifact.ArtifactRepository.ARTIFACT_RESOLVE_V1))
        assertNotNull(holder.repo.descriptor(dev.rubentxu.pipelattice.release.artifact.ArtifactRepository.ARTIFACT_DOWNLOAD_V1))

        // ReleaseManager capabilities
        assertNotNull(holder.release.descriptor(dev.rubentxu.pipelattice.release.release.ReleaseManager.RELEASE_CALCULATE_V1))
        assertNotNull(holder.release.descriptor(dev.rubentxu.pipelattice.release.release.ReleaseManager.RELEASE_PROMOTE_V1))
    }
}
