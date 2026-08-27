package dev.rubentxu.pipelattice.release.artifact

import java.nio.file.Path

/**
 * Request to publish an artifact to the repository.
 *
 * @property coordinate The artifact coordinates.
 * @property localPath Path to the artifact file to publish.
 * @property checksum Optional SHA-256 checksum for integrity verification.
 */
public data class PublishRequest(
    public val coordinate: ArtifactCoordinate,
    public val localPath: Path,
    public val checksum: String? = null,
)

/**
 * Result of a successful publish operation.
 *
 * @property coordinate The published artifact coordinates.
 * @property digest The content-addressable digest assigned by the repository.
 * @property signature Optional signature reference.
 */
public data class PublishResult(
    public val coordinate: ArtifactCoordinate,
    public val digest: String,
    public val signature: SignatureRef? = null,
) {
    public sealed interface Success {
        public val coordinate: ArtifactCoordinate
        public val digest: String
        public val signature: SignatureRef?

        public data class PublishOk(
            override val coordinate: ArtifactCoordinate,
            override val digest: String,
            override val signature: SignatureRef? = null,
        ) : Success
    }
}

/**
 * Reference to a cryptographic signature.
 *
 * @property value The signature reference URI or identifier.
 */
public @JvmInline value class SignatureRef(public val value: String) {
    init {
        require(value.isNotBlank()) { "SignatureRef.value must not be blank" }
    }

    override fun toString(): String = value
}

/**
 * Request to resolve artifact metadata without downloading content.
 *
 * @property coordinate The artifact coordinates to resolve.
 */
public data class ResolveRequest(
    public val coordinate: ArtifactCoordinate,
)

/**
 * Result of a successful resolve operation.
 *
 * @property coordinate The resolved artifact coordinates.
 * @property digest The content-addressable digest.
 * @property sizeBytes The artifact size in bytes (if known).
 * @property createdAt Timestamp of when the artifact was created (if available).
 */
public data class ResolveResult(
    public val coordinate: ArtifactCoordinate,
    public val digest: String,
    public val sizeBytes: Long? = null,
    public val createdAt: String? = null,
) {
    public sealed interface Success {
        public val coordinate: ArtifactCoordinate
        public val digest: String
        public val sizeBytes: Long?
        public val createdAt: String?

        public data class ResolveOk(
            override val coordinate: ArtifactCoordinate,
            override val digest: String,
            override val sizeBytes: Long? = null,
            override val createdAt: String? = null,
        ) : Success
    }
}

/**
 * Request to download an artifact's content.
 *
 * @property coordinate The artifact coordinates.
 * @property destination Path where the artifact should be saved.
 */
public data class DownloadRequest(
    public val coordinate: ArtifactCoordinate,
    public val destination: Path,
)

/**
 * Result of a successful download operation.
 *
 * @property coordinate The downloaded artifact coordinates.
 * @property savedPath Path where the artifact was saved.
 * @property sizeBytes The downloaded artifact size in bytes.
 */
public data class DownloadResult(
    public val coordinate: ArtifactCoordinate,
    public val savedPath: Path,
    public val sizeBytes: Long,
) {
    public sealed interface Success {
        public val coordinate: ArtifactCoordinate
        public val savedPath: Path
        public val sizeBytes: Long

        public data class DownloadOk(
            override val coordinate: ArtifactCoordinate,
            override val savedPath: Path,
            override val sizeBytes: Long,
        ) : Success
    }
}
