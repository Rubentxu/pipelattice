package dev.rubentxu.pipelattice.release.adapter.artifact

import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.release.artifact.ArtifactCoordinate
import dev.rubentxu.pipelattice.release.artifact.ArtifactFailure
import dev.rubentxu.pipelattice.release.artifact.ArtifactRepository
import dev.rubentxu.pipelattice.release.artifact.DownloadResult
import dev.rubentxu.pipelattice.release.artifact.PublishResult
import dev.rubentxu.pipelattice.release.artifact.PublishRequest
import dev.rubentxu.pipelattice.release.artifact.ResolveResult
import dev.rubentxu.pipelattice.release.contract.ArtifactRepositoryContract
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Real adapter TCK shim for LocalFSArtifactRepository.
 *
 * Extends ArtifactRepositoryContract overriding newSubject() and only the fake-only
 * invariant (invariant_invocations_stable).
 *
 * Behavioral invariants are inherited from ArtifactRepositoryContract and execute
 * against real filesystem fixtures via contract fixture hooks.
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

    // --- Contract fixture hooks (real filesystem setup) ---

    /**
     * Creates real artifact files on the filesystem for publish success tests.
     */
    override suspend fun setupPublishSuccess(result: PublishResult) {
        val c = coord()
        val jarPath = tempDir.resolve("repo/dev.example/mylib/1.0.0/mylib-1.0.0.jar")
        Files.createDirectories(jarPath.parent)
        Files.writeString(jarPath, "jar-content-abc")
        // Write the SHA256 sibling
        val shaPath = jarPath.resolveSibling("mylib-1.0.0.jar.sha256")
        Files.writeString(shaPath, "sha256:abc123")
    }

    /**
     * Creates real artifact files for resolve success tests.
     */
    override suspend fun setupResolveSuccess(result: ResolveResult) {
        val c = coord()
        val jarPath = tempDir.resolve("repo/dev.example/mylib/1.0.0/mylib-1.0.0.jar")
        Files.createDirectories(jarPath.parent)
        Files.writeString(jarPath, "resolved-content-xyz")
        val shaPath = jarPath.resolveSibling("mylib-1.0.0.jar.sha256")
        Files.writeString(shaPath, "sha256:abc123")
    }

    /**
     * Creates real artifact files for download success tests.
     */
    override suspend fun setupDownloadSuccess(result: DownloadResult) {
        val c = coord("dev.example", "mylib", "2.0.0")
        val jarPath = tempDir.resolve("repo/dev.example/mylib/2.0.0/mylib-2.0.0.jar")
        Files.createDirectories(jarPath.parent)
        Files.writeString(jarPath, "download-content-xyz")
        val shaPath = jarPath.resolveSibling("mylib-2.0.0.jar.sha256")
        Files.writeString(shaPath, "sha256:abc123")
    }

    /**
     * Sets up resolve failure scenario (no artifact needed - resolve checks existence).
     */
    override suspend fun setupResolveFailure(failure: ArtifactFailure) {
        // Missing artifact is triggered by requesting a non-existent coordinate.
    }

    /**
     * Sets up download failure scenario.
     */
    override suspend fun setupDownloadFailure(failure: ArtifactFailure) {
        // Missing artifact is triggered by requesting a non-existent coordinate.
    }

    // --- Only fake-only invariant override allowed per spec v5 matrix ---
    override fun invariant_invocations_stable() {
        // Real filesystem adapters keep no invocation log; skip this invariant.
        // Override with no-op (designated hook per contract's fake-only classification).
        assertTrue(true, "real adapters don't use queue-based scripting")
    }
}
