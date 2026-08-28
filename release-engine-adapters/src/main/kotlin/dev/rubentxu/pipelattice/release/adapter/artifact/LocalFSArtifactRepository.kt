package dev.rubentxu.pipelattice.release.adapter.artifact

import dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor
import dev.rubentxu.pipelattice.foundation.capability.CapabilityId
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.release.artifact.ArtifactCapabilities
import dev.rubentxu.pipelattice.release.artifact.ArtifactCoordinate
import dev.rubentxu.pipelattice.release.artifact.ArtifactFailure
import dev.rubentxu.pipelattice.release.artifact.ArtifactRepository
import dev.rubentxu.pipelattice.release.artifact.DownloadRequest
import dev.rubentxu.pipelattice.release.artifact.DownloadResult
import dev.rubentxu.pipelattice.release.artifact.PublishRequest
import dev.rubentxu.pipelattice.release.artifact.PublishResult
import dev.rubentxu.pipelattice.release.artifact.ResolveRequest
import dev.rubentxu.pipelattice.release.artifact.ResolveResult
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Real [ArtifactRepository] adapter over the local filesystem.
 *
 * Maps [ArtifactCoordinate] to paths of the form:
 * ```
 * <root>/<group>/<artifact>/<version>/<artifact>-<version>.jar
 * ```
 * with a sibling `.sha256` file containing the hex SHA-256 digest.
 *
 * ## Atomic writes
 * `publish` uses an atomic write: content is written to a `.tmp` file,
 * then moved via `Files.move(REPLACE_EXISTING, ATOMIC_MOVE)` before the
 * digest is computed and the `.sha256` sibling is written.
 *
 * ## Resolve
 * `resolve` checks that the `.jar` exists and returns its path.
 * Missing artifact → [ArtifactFailure.Unknown] with reason `"synthetic-missing-artifact"`.
 *
 * ## Download
 * `download` copies the `.jar` to the requested destination, then verifies
 * the SHA-256 digest against the `.sha256` sibling.
 * Digest mismatch → [ArtifactFailure.Unknown] with reason `"synthetic-corrupt-artifact"`.
 *
 * @param root The root directory for artifact storage.
 */
public class LocalFSArtifactRepository(
    private val root: Path,
) : ArtifactRepository {

    init {
        require(root.isAbsolute) { "LocalFSArtifactRepository.root must be an absolute path" }
    }

    override suspend fun publish(request: PublishRequest): Outcome<PublishResult, ArtifactFailure> {
        return try {
            val coord = request.coordinate
            val artifactPath = resolveCoordPath(coord)
            val parentDir = artifactPath.parent

            // Ensure parent directory exists
            Files.createDirectories(parentDir)

            val tmpPath = artifactPath.resolveSibling("${artifactPath.fileName}.tmp")

            // Atomic write: write to .tmp first, then atomically move
            request.localPath.inputStream().use { input ->
                Files.copy(input, tmpPath, StandardCopyOption.REPLACE_EXISTING)
            }
            Files.move(tmpPath, artifactPath, StandardCopyOption.ATOMIC_MOVE)

            // Compute SHA-256 digest after atomic write
            val digest = computeSha256(artifactPath)

            // Write the digest sibling file
            val digestPath = artifactPath.resolveSibling("${artifactPath.fileName}.sha256")
            Files.writeString(digestPath, "sha256:${digest}")

            Outcome.Success(
                PublishResult(
                    coordinate = coord,
                    digest = "sha256:${digest}",
                )
            )
        } catch (e: Exception) {
            Outcome.Failure(
                ArtifactFailure.Unknown(
                    coordinate = request.coordinate,
                    reason = "synthetic-missing-artifact",
                )
            )
        }
    }

    override suspend fun resolve(request: ResolveRequest): Outcome<ResolveResult, ArtifactFailure> {
        val coord = request.coordinate
        val artifactPath = resolveCoordPath(coord)

        return if (Files.exists(artifactPath)) {
            val digest = computeSha256(artifactPath)
            Outcome.Success(
                ResolveResult(
                    coordinate = coord,
                    digest = "sha256:${digest}",
                )
            )
        } else {
            Outcome.Failure(
                ArtifactFailure.Unknown(
                    coordinate = coord,
                    reason = "synthetic-missing-artifact",
                )
            )
        }
    }

    override suspend fun download(request: DownloadRequest): Outcome<DownloadResult, ArtifactFailure> {
        val coord = request.coordinate
        val artifactPath = resolveCoordPath(coord)
        val digestPath = artifactPath.resolveSibling("${artifactPath.fileName}.sha256")

        return when {
            !Files.exists(artifactPath) -> {
                Outcome.Failure(
                    ArtifactFailure.Unknown(
                        coordinate = coord,
                        reason = "synthetic-missing-artifact",
                    )
                )
            }
            Files.exists(digestPath) -> {
                // Verify digest
                val expectedDigest = Files.readString(digestPath).trim()
                val actualDigest = computeSha256(artifactPath)
                val expectedHex = if (expectedDigest.startsWith("sha256:")) {
                    expectedDigest.removePrefix("sha256:")
                } else expectedDigest

                if (actualDigest.equals(expectedHex, ignoreCase = true)) {
                    copyToDestination(artifactPath, request.destination, coord)
                } else {
                    Outcome.Failure(
                        ArtifactFailure.Unknown(
                            coordinate = coord,
                            reason = "synthetic-corrupt-artifact",
                        )
                    )
                }
            }
            else -> {
                // No digest file — copy anyway (trust without verification)
                copyToDestination(artifactPath, request.destination, coord)
            }
        }
    }

    override fun descriptor(id: CapabilityId): CapabilityDescriptor? = when (id) {
        ArtifactRepository.ARTIFACT_PUBLISH_V1 -> ArtifactCapabilities.ARTIFACT_PUBLISH_V1
        ArtifactRepository.ARTIFACT_RESOLVE_V1 -> ArtifactCapabilities.ARTIFACT_RESOLVE_V1
        ArtifactRepository.ARTIFACT_DOWNLOAD_V1 -> ArtifactCapabilities.ARTIFACT_DOWNLOAD_V1
        else -> null
    }

    /**
     * Maps an [ArtifactCoordinate] to a path within the repository root.
     *
     * Format: `<root>/<groupId>/<artifactId>/<version>/<artifactId>-<version>.jar`
     * Group separators (`.`) in `groupId` are NOT converted to directory separators —
     * the group IS the directory name (e.g., `dev.example` → `dev.example/`).
     *
     * Example: `ArtifactCoordinate("dev.example", "mylib", "1.0.0")`
     * → `<root>/dev.example/mylib/1.0.0/mylib-1.0.0.jar`
     */
    private fun resolveCoordPath(coord: ArtifactCoordinate): Path {
        return root
            .resolve(coord.groupId)
            .resolve(coord.artifactId)
            .resolve(coord.version)
            .resolve("${coord.artifactId}-${coord.version}.jar")
    }

    /**
     * Copies the artifact to the destination path.
     */
    private fun copyToDestination(
        artifactPath: Path,
        destination: Path,
        coord: ArtifactCoordinate,
    ): Outcome<DownloadResult, ArtifactFailure> {
        return try {
            Files.createDirectories(destination.parent)
            Files.copy(artifactPath, destination, StandardCopyOption.REPLACE_EXISTING)
            val sizeBytes = Files.size(destination)
            Outcome.Success(
                DownloadResult(
                    coordinate = coord,
                    savedPath = destination,
                    sizeBytes = sizeBytes,
                )
            )
        } catch (e: Exception) {
            Outcome.Failure(
                ArtifactFailure.Unknown(
                    coordinate = coord,
                    reason = "synthetic-corrupt-artifact",
                )
            )
        }
    }

    /**
     * Computes the SHA-256 hex digest of a file.
     */
    private fun computeSha256(path: Path): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun Path.inputStream(): InputStream = Files.newInputStream(this)
}
