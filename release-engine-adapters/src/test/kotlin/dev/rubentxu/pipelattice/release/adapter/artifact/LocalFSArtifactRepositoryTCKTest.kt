package dev.rubentxu.pipelattice.release.adapter.artifact

import dev.rubentxu.pipelattice.foundation.capability.SideEffect
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.release.artifact.ArtifactCoordinate
import dev.rubentxu.pipelattice.release.artifact.ArtifactFailure
import dev.rubentxu.pipelattice.release.artifact.ArtifactRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TCK contract tests for [LocalFSArtifactRepository] as a real ArtifactRepository adapter.
 *
 * Tests the invariants that apply to real adapters:
 * - Invariant 5: descriptor side-effects match expected values
 * - Invariant 1: publish/resolve/download returns real outcomes against real filesystem
 *
 * Invariants 3 and 4 (queue-based: empty-queue-raises, invocations-stability)
 * are NOT applicable to real adapters — they don't have scripted queues.
 */
class LocalFSArtifactRepositoryTCKTest {

    @TempDir
    lateinit var tempDir: Path

    private fun newRepo(): LocalFSArtifactRepository =
        LocalFSArtifactRepository(tempDir.resolve("repo"))

    private fun coord(
        groupId: String = "dev.example",
        artifactId: String = "mylib",
        version: String = "1.0.0",
    ) = ArtifactCoordinate(groupId, artifactId, version)

    // ----- Invariant 5: side-effect consistency -----

    @Test
    fun `descriptor publish is MUTATING`() {
        val repo = newRepo()
        val desc = repo.descriptor(ArtifactRepository.ARTIFACT_PUBLISH_V1)
        assertNotNull(desc, "ARTIFACT_PUBLISH_V1 must be advertised")
        assertTrue(SideEffect.MUTATING in desc.sideEffects, "publish must be MUTATING")
    }

    @Test
    fun `descriptor resolve is READ_ONLY`() {
        val repo = newRepo()
        val desc = repo.descriptor(ArtifactRepository.ARTIFACT_RESOLVE_V1)
        assertNotNull(desc, "ARTIFACT_RESOLVE_V1 must be advertised")
        assertTrue(SideEffect.READ_ONLY in desc.sideEffects, "resolve must be READ_ONLY")
    }

    @Test
    fun `descriptor download is READ_ONLY`() {
        val repo = newRepo()
        val desc = repo.descriptor(ArtifactRepository.ARTIFACT_DOWNLOAD_V1)
        assertNotNull(desc, "ARTIFACT_DOWNLOAD_V1 must be advertised")
        assertTrue(SideEffect.READ_ONLY in desc.sideEffects, "download must be READ_ONLY")
    }

    // ----- Invariant 1: real success operations -----

    @Test
    fun `publish real artifact returns Success with digest`() = runBlocking {
        val sourceJar = tempDir.resolve("mylib-1.0.0.jar").also {
            Files.writeString(it, "jar-content-abc")
        }
        val repo = newRepo()

        val outcome = repo.publish(
            dev.rubentxu.pipelattice.release.artifact.PublishRequest(
                coordinate = coord(),
                localPath = sourceJar,
            )
        )

        assertIs<Outcome.Success<*>>(outcome)
        val result = outcome.value as dev.rubentxu.pipelattice.release.artifact.PublishResult
        assertTrue(result.digest.startsWith("sha256:"), "digest must be sha256 prefixed")
    }

    @Test
    fun `resolve existing artifact returns Success with digest`() = runBlocking {
        val repo = newRepo()
        // Pre-create an artifact
        val jarPath = tempDir.resolve("repo/dev.example/mylib/1.0.0/mylib-1.0.0.jar")
        Files.createDirectories(jarPath.parent)
        Files.writeString(jarPath, "resolved-content")

        val outcome = repo.resolve(
            dev.rubentxu.pipelattice.release.artifact.ResolveRequest(coordinate = coord())
        )

        assertIs<Outcome.Success<*>>(outcome)
        val result = outcome.value as dev.rubentxu.pipelattice.release.artifact.ResolveResult
        assertTrue(result.digest.startsWith("sha256:"), "digest must be sha256 prefixed")
        assertTrue(result.coordinate.groupId == "dev.example")
    }

    @Test
    fun `download existing artifact returns Success with size`() = runBlocking {
        val repo = newRepo()
        // Pre-create an artifact
        val jarPath = tempDir.resolve("repo/dev.example/mylib/1.0.0/mylib-1.0.0.jar")
        Files.createDirectories(jarPath.parent)
        Files.writeString(jarPath, "download-content")

        val destination = tempDir.resolve("downloaded.jar")
        val outcome = repo.download(
            dev.rubentxu.pipelattice.release.artifact.DownloadRequest(
                coordinate = coord(),
                destination = destination,
            )
        )

        assertIs<Outcome.Success<*>>(outcome)
        val result = outcome.value as dev.rubentxu.pipelattice.release.artifact.DownloadResult
        assertTrue(Files.exists(destination), "destination file must exist")
        assertTrue(result.sizeBytes > 0L, "size must be greater than 0")
    }

    // ----- Invariant 2: typed failures -----

    @Test
    fun `resolve missing artifact returns typed Unknown failure`() = runBlocking {
        val repo = newRepo()

        val outcome = repo.resolve(
            dev.rubentxu.pipelattice.release.artifact.ResolveRequest(coordinate = coord())
        )

        assertIs<Outcome.Failure<ArtifactFailure>>(outcome)
        val unknown = outcome.reason as ArtifactFailure.Unknown
        assertTrue(unknown.reason == "synthetic-missing-artifact")
    }

    @Test
    fun `download missing artifact returns typed Unknown failure`() = runBlocking {
        val repo = newRepo()
        val destination = tempDir.resolve("missing-download.jar")

        val outcome = repo.download(
            dev.rubentxu.pipelattice.release.artifact.DownloadRequest(
                coordinate = coord(),
                destination = destination,
            )
        )

        assertIs<Outcome.Failure<ArtifactFailure>>(outcome)
        val unknown = outcome.reason as ArtifactFailure.Unknown
        assertTrue(unknown.reason == "synthetic-missing-artifact")
    }
}
