package dev.rubentxu.pipelattice.release.adapter.artifact

import dev.rubentxu.pipelattice.foundation.capability.SideEffect
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.release.artifact.ArtifactCoordinate
import dev.rubentxu.pipelattice.release.artifact.ArtifactFailure
import dev.rubentxu.pipelattice.release.artifact.ArtifactRepository
import dev.rubentxu.pipelattice.release.artifact.DownloadResult
import dev.rubentxu.pipelattice.release.artifact.PublishResult
import dev.rubentxu.pipelattice.release.artifact.PublishRequest
import dev.rubentxu.pipelattice.release.artifact.ResolveResult
import dev.rubentxu.pipelattice.release.contract.ArtifactRepositoryContract
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Real adapter TCK shim for LocalFSArtifactRepository.
 *
 * Extends ArtifactRepositoryContract overriding newSubject() and the invariant methods
 * that need real filesystem behavior.
 */
class LocalFSArtifactRepositoryContractTest : ArtifactRepositoryContract() {

    @TempDir
    lateinit var tempDir: Path

    private val subject: ArtifactRepository by lazy {
        LocalFSArtifactRepository(tempDir.resolve("repo"))
    }

    override fun newSubject(): ArtifactRepository = subject

    private fun coord(
        groupId: String = "dev.example",
        artifactId: String = "mylib",
        version: String = "1.0.0",
    ) = ArtifactCoordinate(groupId, artifactId, version)

    // ----- Invariant overrides for real filesystem adapter -----

    /** Real adapters don't use queue-based scripting */
    override suspend fun invariant_invocations_stable(): Boolean = true

    /** Real publish uses atomic filesystem writes */
    override suspend fun invariant_publish_success(): Outcome<PublishResult, ArtifactFailure> {
        val c = coord()
        val sourceJar = tempDir.resolve("test-lib.jar").also {
            Files.writeString(it, "jar-content-abc")
        }
        return subject().publish(PublishRequest(c, sourceJar))
    }

    /** Real resolve against published artifact */
    override suspend fun invariant_resolve_success(): Outcome<ResolveResult, ArtifactFailure> {
        val c = coord()
        val jarPath = tempDir.resolve("repo/dev.example/mylib/1.0.0/mylib-1.0.0.jar")
        Files.createDirectories(jarPath.parent)
        Files.writeString(jarPath, "resolved-content-xyz")
        return subject().resolve(
            dev.rubentxu.pipelattice.release.artifact.ResolveRequest(c)
        )
    }

    /** Real download against existing artifact */
    override suspend fun invariant_download_success(): Outcome<DownloadResult, ArtifactFailure> {
        val c = coord("dev.example", "mylib", "2.0.0")
        val jarPath = tempDir.resolve("repo/dev.example/mylib/2.0.0/mylib-2.0.0.jar")
        Files.createDirectories(jarPath.parent)
        Files.writeString(jarPath, "download-content-xyz")
        val dest = tempDir.resolve("downloaded.jar")
        return subject().download(
            dev.rubentxu.pipelattice.release.artifact.DownloadRequest(c, dest)
        )
    }

    /** Real resolve failure for missing artifact */
    override suspend fun invariant_resolve_failure(): Outcome<ResolveResult, ArtifactFailure> {
        return subject().resolve(
            dev.rubentxu.pipelattice.release.artifact.ResolveRequest(coord("dev.example", "lib", "9.9.9"))
        )
    }

    /** Real download failure for missing artifact */
    override suspend fun invariant_download_failure(): Outcome<DownloadResult, ArtifactFailure> {
        return subject().download(
            dev.rubentxu.pipelattice.release.artifact.DownloadRequest(
                coord("dev.example", "lib", "9.9.9"),
                tempDir.resolve("nonexistent-dest.jar"),
            )
        )
    }

    // ----- Additional tests -----

    @Test
    fun `tck_publish_returns_success_with_digest`() = runBlocking {
        val outcome = invariant_publish_success()
        assertIs<Outcome.Success<PublishResult>>(outcome)
        assertTrue(outcome.value.digest.startsWith("sha256:"))
    }

    @Test
    fun `tck_resolve_returns_success_with_digest`() = runBlocking {
        val outcome = invariant_resolve_success()
        assertIs<Outcome.Success<ResolveResult>>(outcome)
        assertTrue(outcome.value.digest.startsWith("sha256:"))
    }

    @Test
    fun `tck_resolve_missing_returns_typed_failure`() = runBlocking {
        val outcome = invariant_resolve_failure()
        assertIs<Outcome.Failure<ArtifactFailure>>(outcome)
        assertTrue((outcome.reason as ArtifactFailure.Unknown).reason == "synthetic-missing-artifact")
    }
}
