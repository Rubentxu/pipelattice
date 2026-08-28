package dev.rubentxu.pipelattice.release.adapter.artifact

import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.release.artifact.ArtifactCoordinate
import dev.rubentxu.pipelattice.release.artifact.ArtifactFailure
import dev.rubentxu.pipelattice.release.artifact.ArtifactRepository
import dev.rubentxu.pipelattice.release.artifact.DownloadRequest
import dev.rubentxu.pipelattice.release.artifact.DownloadResult
import dev.rubentxu.pipelattice.release.artifact.PublishResult
import dev.rubentxu.pipelattice.release.artifact.PublishRequest
import dev.rubentxu.pipelattice.release.artifact.ResolveRequest
import dev.rubentxu.pipelattice.release.artifact.ResolveResult
import dev.rubentxu.pipelattice.release.contract.ArtifactRepositoryContract
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Real adapter TCK shim for LocalFSArtifactRepository.
 *
 * Extends ArtifactRepositoryContract overriding newSubject(), expectation hooks,
 * and only the fake-only invariant (invariant_invocations_stable).
 *
 * Behavioral invariants are inherited from ArtifactRepositoryContract and execute
 * against real filesystem fixtures via the expected* hooks.
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

    private fun computeSha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // --- Contract expectation hooks (property-based compliance) ---

    /**
     * Real publish: creates fixture at request.localPath so publish() reads it.
     * The digest is computed from that content (which will be in the repo after publish).
     */
    override fun expectedPublishResult(coordinate: ArtifactCoordinate, request: PublishRequest): PublishResult {
        // Create the source file that publish() will read and copy to the repo
        Files.createDirectories(request.localPath.parent)
        Files.writeString(request.localPath, "jar-content-abc")
        val sha = computeSha256(request.localPath)
        return PublishResult(coordinate, "sha256:$sha")
    }

    /**
     * Real resolve: returns digest computed from the actual file in the repo.
     * The file is placed there by publish() before resolve() is called.
     */
    override fun expectedResolveResult(coordinate: ArtifactCoordinate, request: ResolveRequest): ResolveResult {
        // Construct path matching what LocalFSArtifactRepository.resolveCoordPath() computes
        val jarPath = tempDir.resolve("repo")
            .resolve(coordinate.groupId)
            .resolve(coordinate.artifactId)
            .resolve(coordinate.version)
            .resolve("${coordinate.artifactId}-${coordinate.version}.jar")
        if (!Files.exists(jarPath)) {
            Files.createDirectories(jarPath.parent)
            Files.writeString(jarPath, "jar-content-abc")
        }
        val sha = computeSha256(jarPath)
        return ResolveResult(coordinate, "sha256:$sha", Files.size(jarPath))
    }

    /**
     * Real download: creates fixture at repo path so download() can read and copy to destination.
     */
    override fun expectedDownloadResult(coordinate: ArtifactCoordinate, destination: Path, request: DownloadRequest): DownloadResult {
        // Construct path matching what LocalFSArtifactRepository.resolveCoordPath() computes
        val jarPath = tempDir.resolve("repo")
            .resolve(coordinate.groupId)
            .resolve(coordinate.artifactId)
            .resolve(coordinate.version)
            .resolve("${coordinate.artifactId}-${coordinate.version}.jar")
        Files.createDirectories(jarPath.parent)
        Files.writeString(jarPath, "download-content-xyz")
        val sha = computeSha256(jarPath)
        // Write SHA sibling so download doesn't fail on digest mismatch
        val shaPath = jarPath.resolveSibling("${coordinate.artifactId}-${coordinate.version}.jar.sha256")
        Files.writeString(shaPath, "sha256:$sha")
        return DownloadResult(coordinate, destination, Files.size(jarPath))
    }

    /**
     * Real adapters don't use queue-based scripting.
     */
    override fun supportsQueueBasedScripting(): Boolean = false

    // --- Contract setup hooks ---

    override suspend fun setupPublishSuccess(result: PublishResult, request: PublishRequest) {
        // Fixture created by expectedPublishResult at request.localPath
    }

    override suspend fun setupResolveSuccess(result: ResolveResult, request: ResolveRequest) {
        // Fixture already placed in repo by publish step
    }

    override suspend fun setupDownloadSuccess(result: DownloadResult, request: DownloadRequest) {
        // Fixture created by expectedDownloadResult at repo path
    }

    override suspend fun setupResolveFailure(failure: ArtifactFailure) {
        // Missing artifact triggered by requesting non-existent coordinate
    }

    override suspend fun setupDownloadFailure(failure: ArtifactFailure) {
        // Missing artifact triggered by requesting non-existent coordinate
    }

    // --- Only fake-only invariant override allowed per spec v5 matrix ---
    override fun invariant_invocations_stable() {
        // Real filesystem adapters keep no invocation log; skip this invariant.
        assertTrue(true, "real adapters don't use queue-based scripting")
    }
}
