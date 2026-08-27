package dev.rubentxu.pipelattice.release.artifact

import dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor
import dev.rubentxu.pipelattice.foundation.capability.CapabilityId
import dev.rubentxu.pipelattice.foundation.capability.FailureModel
import dev.rubentxu.pipelattice.foundation.capability.IdempotencyPolicy
import dev.rubentxu.pipelattice.foundation.capability.ProviderRequirements
import dev.rubentxu.pipelattice.foundation.capability.ProviderVersion
import dev.rubentxu.pipelattice.foundation.capability.SchemaId
import dev.rubentxu.pipelattice.foundation.capability.SideEffect
import dev.rubentxu.pipelattice.foundation.outcome.Outcome

/**
 * Port for interacting with artifact repositories.
 *
 * Provides publish, resolve, and download operations with typed request/result/failure models.
 */
public interface ArtifactRepository {

    /**
     * Publishes an artifact to the repository.
     *
     * @param request The publish request specifying artifact coordinates and content.
     * @return [Outcome.Success] with [PublishResult] on success,
     *         or [Outcome.Failure] with [ArtifactFailure] on error.
     */
    public suspend fun publish(request: PublishRequest): Outcome<PublishResult, ArtifactFailure>

    /**
     * Resolves the metadata for an artifact without downloading its content.
     *
     * @param request The resolve request specifying artifact coordinates.
     * @return [Outcome.Success] with [ResolveResult] on success,
     *         or [Outcome.Failure] with [ArtifactFailure] on error.
     */
    public suspend fun resolve(request: ResolveRequest): Outcome<ResolveResult, ArtifactFailure>

    /**
     * Downloads an artifact's content from the repository.
     *
     * @param request The download request specifying artifact coordinates and destination.
     * @return [Outcome.Success] with [DownloadResult] on success,
     *         or [Outcome.Failure] with [ArtifactFailure] on error.
     */
    public suspend fun download(request: DownloadRequest): Outcome<DownloadResult, ArtifactFailure>

    /**
     * Returns the [CapabilityDescriptor] for the given [CapabilityId],
     * or null if the operation is not supported.
     */
    public fun descriptor(id: CapabilityId): CapabilityDescriptor?

    public companion object {
        /** Capability constant for `artifact.publish/v1`. */
        public val ARTIFACT_PUBLISH_V1: CapabilityId =
            CapabilityId.parse("artifact.publish/v1")

        /** Capability constant for `artifact.resolve/v1`. */
        public val ARTIFACT_RESOLVE_V1: CapabilityId =
            CapabilityId.parse("artifact.resolve/v1")

        /** Capability constant for `artifact.download/v1`. */
        public val ARTIFACT_DOWNLOAD_V1: CapabilityId =
            CapabilityId.parse("artifact.download/v1")
    }
}

/**
 * Failure variants for [ArtifactRepository] operations.
 */
public sealed interface ArtifactFailure {

    /**
     * An unexpected error occurred.
     */
    public data class Unknown(
        public val coordinate: ArtifactCoordinate,
        public val reason: String,
    ) : ArtifactFailure {
        override fun toString(): String =
            "ArtifactFailure.Unknown(coordinate=$coordinate, reason=$reason)"
    }

    /**
     * The repository rejected the operation (e.g. insufficient permissions,
     * duplicate artifact, quota exceeded).
     */
    public data class Rejected(
        public val coordinate: ArtifactCoordinate,
        public val reason: String,
    ) : ArtifactFailure {
        override fun toString(): String =
            "ArtifactFailure.Rejected(coordinate=$coordinate, reason=$reason)"
    }
}

/**
 * Coordinates identifying an artifact in a repository.
 *
 * @property groupId The artifact group (e.g. `dev.example`).
 * @property artifactId The artifact identifier (e.g. `mylib`).
 * @property version The artifact version (e.g. `1.0.0`).
 */
public data class ArtifactCoordinate(
    public val groupId: String,
    public val artifactId: String,
    public val version: String,
) {
    init {
        require(groupId.isNotBlank()) { "ArtifactCoordinate.groupId must not be blank" }
        require(artifactId.isNotBlank()) { "ArtifactCoordinate.artifactId must not be blank" }
        require(version.isNotBlank()) { "ArtifactCoordinate.version must not be blank" }
    }

    override fun toString(): String = "$groupId:$artifactId:$version"
}

/**
 * Factory for creating [CapabilityDescriptor] constants for artifact operations.
 */
public object ArtifactCapabilities {

    private fun descriptor(
        id: CapabilityId,
        inputSchema: String,
        outputSchema: String,
        sideEffects: Set<SideEffect>,
        idempotency: IdempotencyPolicy,
    ): CapabilityDescriptor = CapabilityDescriptor(
        id = id,
        inputSchemaId = SchemaId(inputSchema),
        outputSchemaId = SchemaId(outputSchema),
        failureModel = FailureModel.Typed("ARTIFACT-${id.value.replace(".", "-").uppercase()}"),
        sideEffects = sideEffects,
        idempotencyPolicy = idempotency,
        providerRequirements = ProviderRequirements(
            minProviderVersion = ProviderVersion("V1"),
            authRequirements = emptySet(),
        ),
    )

    /** Descriptor for `artifact.publish/v1` — MUTATING. */
    public val ARTIFACT_PUBLISH_V1: CapabilityDescriptor = descriptor(
        id = ArtifactRepository.ARTIFACT_PUBLISH_V1,
        inputSchema = "artifact-publish-request",
        outputSchema = "artifact-publish-result",
        sideEffects = setOf(SideEffect.MUTATING),
        idempotency = IdempotencyPolicy.Strict(retrySafe = false),
    )

    /** Descriptor for `artifact.resolve/v1` — READ_ONLY. */
    public val ARTIFACT_RESOLVE_V1: CapabilityDescriptor = descriptor(
        id = ArtifactRepository.ARTIFACT_RESOLVE_V1,
        inputSchema = "artifact-resolve-request",
        outputSchema = "artifact-resolve-result",
        sideEffects = setOf(SideEffect.READ_ONLY),
        idempotency = IdempotencyPolicy.Strict(retrySafe = true),
    )

    /** Descriptor for `artifact.download/v1` — READ_ONLY. */
    public val ARTIFACT_DOWNLOAD_V1: CapabilityDescriptor = descriptor(
        id = ArtifactRepository.ARTIFACT_DOWNLOAD_V1,
        inputSchema = "artifact-download-request",
        outputSchema = "artifact-download-result",
        sideEffects = setOf(SideEffect.READ_ONLY),
        idempotency = IdempotencyPolicy.Strict(retrySafe = true),
    )
}
