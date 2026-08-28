package dev.rubentxu.pipelattice.release.adapter.artifact

import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.release.artifact.ArtifactCoordinate
import dev.rubentxu.pipelattice.release.artifact.ArtifactFailure
import dev.rubentxu.pipelattice.release.artifact.DownloadRequest
import dev.rubentxu.pipelattice.release.artifact.DownloadResult
import dev.rubentxu.pipelattice.release.artifact.PublishRequest
import dev.rubentxu.pipelattice.release.artifact.PublishResult
import dev.rubentxu.pipelattice.release.artifact.ResolveRequest
import dev.rubentxu.pipelattice.release.artifact.ResolveResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [LocalFSArtifactRepository].
 */
class LocalFSArtifactRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var repoRoot: Path

    private fun newRepo() = LocalFSArtifactRepository(repoRoot)

    private fun coord(
        groupId: String = "dev.example",
        artifactId: String = "mylib",
        version: String = "1.0.0",
    ) = ArtifactCoordinate(groupId, artifactId, version)

    // ----- publish -----

    @Test
    fun `publish writes artifact and sha256 sibling file`() = runBlocking {
        repoRoot = tempDir.resolve("repo")
        val sourceJar = tempDir.resolve("mylib-1.0.0.jar").also {
            Files.writeString(it, "jar-content-123")
        }
        val request = PublishRequest(
            coordinate = coord(),
            localPath = sourceJar,
        )

        val outcome = newRepo().publish(request)

        assertIs<Outcome.Success<PublishResult>>(outcome)
        assertEquals("dev.example", outcome.value.coordinate.groupId)

        // Verify .jar was written
        val jarPath = repoRoot.resolve("dev.example/mylib/1.0.0/mylib-1.0.0.jar")
        assertTrue(Files.exists(jarPath), "artifact jar should exist")
        assertEquals("jar-content-123", Files.readString(jarPath))

        // Verify .sha256 sibling was written
        val shaPath = jarPath.resolveSibling("mylib-1.0.0.jar.sha256")
        assertTrue(Files.exists(shaPath), "sha256 sibling should exist")
        val digestLine = Files.readString(shaPath).trim()
        assertTrue(digestLine.startsWith("sha256:"), "digest should start with sha256:")
    }

    @Test
    fun `publish creates parent directories as needed`() = runBlocking {
        repoRoot = tempDir.resolve("repo2")
        val sourceJar = tempDir.resolve("deep-2.0.0.jar").also {
            Files.writeString(it, "deep-content")
        }
        val request = PublishRequest(
            coordinate = ArtifactCoordinate("org.deep", "artifact", "2.0.0"),
            localPath = sourceJar,
        )

        val outcome = newRepo().publish(request)

        assertIs<Outcome.Success<PublishResult>>(outcome)
        val jarPath = repoRoot.resolve("org.deep/artifact/2.0.0/artifact-2.0.0.jar")
        assertTrue(Files.exists(jarPath))
    }

    @Test
    fun `publish missing source file returns failure`() = runBlocking {
        repoRoot = tempDir.resolve("repo3")
        val missingJar = tempDir.resolve("nonexistent.jar")
        val request = PublishRequest(
            coordinate = coord(),
            localPath = missingJar,
        )

        val outcome = newRepo().publish(request)

        assertIs<Outcome.Failure<ArtifactFailure>>(outcome)
        assertEquals("synthetic-missing-artifact", (outcome.reason as ArtifactFailure.Unknown).reason)
    }

    // ----- resolve -----

    @Test
    fun `resolve returns digest for existing artifact`() = runBlocking {
        repoRoot = tempDir.resolve("repo4")
        // Pre-create an artifact
        val jarPath = repoRoot.resolve("dev.example/mylib/1.0.0/mylib-1.0.0.jar")
        Files.createDirectories(jarPath.parent)
        Files.writeString(jarPath, "resolved-content")

        val outcome = newRepo().resolve(ResolveRequest(coordinate = coord()))

        assertIs<Outcome.Success<ResolveResult>>(outcome)
        assertEquals("dev.example", outcome.value.coordinate.groupId)
        assertTrue(outcome.value.digest.startsWith("sha256:"), "digest should be sha256: prefixed")
    }

    @Test
    fun `resolve missing artifact returns failure`() = runBlocking {
        repoRoot = tempDir.resolve("repo5")

        val outcome = newRepo().resolve(ResolveRequest(coordinate = coord()))

        assertIs<Outcome.Failure<ArtifactFailure>>(outcome)
        assertEquals("synthetic-missing-artifact", (outcome.reason as ArtifactFailure.Unknown).reason)
    }

    // ----- download -----

    @Test
    fun `download copies artifact to destination`() = runBlocking {
        repoRoot = tempDir.resolve("repo6")
        // Pre-create an artifact
        val jarPath = repoRoot.resolve("dev.example/mylib/1.0.0/mylib-1.0.0.jar")
        Files.createDirectories(jarPath.parent)
        Files.writeString(jarPath, "download-content")

        val destination = tempDir.resolve("downloaded")
        val outcome = newRepo().download(
            DownloadRequest(coordinate = coord(), destination = destination)
        )

        assertIs<Outcome.Success<DownloadResult>>(outcome)
        assertEquals("download-content", Files.readString(destination))
        assertTrue(outcome.value.sizeBytes > 0L)
    }

    @Test
    fun `download missing artifact returns failure`() = runBlocking {
        repoRoot = tempDir.resolve("repo7")

        val destination = tempDir.resolve("downloaded")
        val outcome = newRepo().download(
            DownloadRequest(coordinate = coord(), destination = destination)
        )

        assertIs<Outcome.Failure<ArtifactFailure>>(outcome)
        assertEquals("synthetic-missing-artifact", (outcome.reason as ArtifactFailure.Unknown).reason)
    }

    @Test
    fun `download verifies sha256 and fails on mismatch`() = runBlocking {
        repoRoot = tempDir.resolve("repo8")
        // Pre-create artifact + mismatched sha256
        val jarPath = repoRoot.resolve("dev.example/mylib/1.0.0/mylib-1.0.0.jar")
        Files.createDirectories(jarPath.parent)
        Files.writeString(jarPath, "corrupt-content")

        val shaPath = jarPath.resolveSibling("mylib-1.0.0.jar.sha256")
        Files.writeString(shaPath, "sha256:0000000000000000000000000000000000000000000000000000000000000000")

        val destination = tempDir.resolve("should-not-exist")
        val outcome = newRepo().download(
            DownloadRequest(coordinate = coord(), destination = destination)
        )

        assertIs<Outcome.Failure<ArtifactFailure>>(outcome)
        assertEquals("synthetic-corrupt-artifact", (outcome.reason as ArtifactFailure.Unknown).reason)
        assertTrue(Files.notExists(destination), "destination should not exist on corrupt download")
    }

    @Test
    fun `download without sha256 sibling copies without verification`() = runBlocking {
        repoRoot = tempDir.resolve("repo9")
        // Artifact without sha256 sibling
        val jarPath = repoRoot.resolve("dev.example/mylib/1.0.0/mylib-1.0.0.jar")
        Files.createDirectories(jarPath.parent)
        Files.writeString(jarPath, "no-digest-content")

        val destination = tempDir.resolve("downloaded-no-digest")
        val outcome = newRepo().download(
            DownloadRequest(coordinate = coord(), destination = destination)
        )

        assertIs<Outcome.Success<DownloadResult>>(outcome)
        assertEquals("no-digest-content", Files.readString(destination))
    }

    // ----- path mapping -----

    @Test
    fun `groupId with dots is not path-separated`() = runBlocking {
        repoRoot = tempDir.resolve("repo10")
        // Group "dev.example" should be a single directory segment, not "dev/example"
        val jarPath = repoRoot.resolve("dev.example/mylib/1.0.0/mylib-1.0.0.jar")
        Files.createDirectories(jarPath.parent)
        Files.writeString(jarPath, "dot-group-content")

        val outcome = newRepo().resolve(ResolveRequest(coordinate = coord()))

        assertIs<Outcome.Success<ResolveResult>>(outcome)
        assertTrue(Files.exists(jarPath), "jar should be at dev.example/mylib/..., not dev/example/mylib/...")
    }

    // ----- capabilities -----

    @Test
    fun `descriptor returns correct capabilities`() {
        repoRoot = tempDir.resolve("repo11")
        val repo = newRepo()

        val publish = repo.descriptor(dev.rubentxu.pipelattice.release.artifact.ArtifactRepository.ARTIFACT_PUBLISH_V1)
        val resolve = repo.descriptor(dev.rubentxu.pipelattice.release.artifact.ArtifactRepository.ARTIFACT_RESOLVE_V1)
        val download = repo.descriptor(dev.rubentxu.pipelattice.release.artifact.ArtifactRepository.ARTIFACT_DOWNLOAD_V1)
        val unknown = repo.descriptor(dev.rubentxu.pipelattice.foundation.capability.CapabilityId("unknown"))

        assertTrue(publish != null, "should advertise ARTIFACT_PUBLISH_V1")
        assertTrue(resolve != null, "should advertise ARTIFACT_RESOLVE_V1")
        assertTrue(download != null, "should advertise ARTIFACT_DOWNLOAD_V1")
        assertTrue(unknown == null, "should not advertise unknown capability")
    }
}
