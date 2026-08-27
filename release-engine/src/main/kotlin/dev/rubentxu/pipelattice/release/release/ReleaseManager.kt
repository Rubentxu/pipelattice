package dev.rubentxu.pipelattice.release.release

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
 * Port for managing release versioning and promotion.
 *
 * Provides version calculation and environment promotion with typed request/result/failure models.
 */
public interface ReleaseManager {

    /**
     * Calculates the next version based on source revision and bump policy.
     *
     * This operation is deterministic: the same inputs always produce the same version.
     *
     * @param request The calculation request specifying source revision and bump policy.
     * @return [Outcome.Success] with [CalculateResult] on success,
     *         or [Outcome.Failure] with [ReleaseFailure] on error.
     */
    public suspend fun calculate(request: CalculateRequest): Outcome<CalculateResult, ReleaseFailure>

    /**
     * Promotes a version to a target environment.
     *
     * This operation honors the `requiresApproval` metadata WITHOUT executing approval —
     * it returns [ReleaseFailure.PromotionRejected] with [requiresApproval = true] when
     * the policy requires approval rather than performing the approval itself.
     *
     * @param request The promotion request specifying target environment and version.
     * @return [Outcome.Success] with [PromoteResult] on success,
     *         or [Outcome.Failure] with [ReleaseFailure] on error.
     */
    public suspend fun promote(request: PromoteRequest): Outcome<PromoteResult, ReleaseFailure>

    /**
     * Returns the [CapabilityDescriptor] for the given [CapabilityId],
     * or null if the operation is not supported.
     */
    public fun descriptor(id: CapabilityId): CapabilityDescriptor?

    public companion object {
        /** Capability constant for `release.calculate/v1`. */
        public val RELEASE_CALCULATE_V1: CapabilityId =
            CapabilityId.parse("release.calculate/v1")

        /** Capability constant for `release.promote/v1`. */
        public val RELEASE_PROMOTE_V1: CapabilityId =
            CapabilityId.parse("release.promote/v1")
    }
}

/**
 * Failure variants for [ReleaseManager] operations.
 */
public sealed interface ReleaseFailure {

    /**
     * The provided version string is invalid.
     */
    public data class InvalidVersion(
        public val raw: String,
        public val reason: String,
    ) : ReleaseFailure {
        override fun toString(): String = "ReleaseFailure.InvalidVersion(raw=$raw, reason=$reason)"
    }

    /**
     * Promotion was rejected due to policy constraints.
     *
     * @property version The version that was rejected.
     * @property reason Human-readable reason for the rejection.
     * @property requiresApproval If true, the promotion requires explicit approval before retry.
     */
    public data class PromotionRejected(
        public val version: SemanticVersion,
        public val reason: String,
        public val requiresApproval: Boolean,
    ) : ReleaseFailure {
        override fun toString(): String =
            "ReleaseFailure.PromotionRejected(version=$version, reason=$reason, " +
                "requiresApproval=$requiresApproval)"
    }
}

/**
 * A semantic version with major, minor, and patch components.
 *
 * @property major Major version.
 * @property minor Minor version.
 * @property patch Patch version.
 * @property preRelease Optional pre-release suffix (e.g. `alpha.1`).
 */
public data class SemanticVersion(
    public val major: Int,
    public val minor: Int,
    public val patch: Int,
    public val preRelease: String? = null,
) {
    init {
        require(major >= 0) { "SemanticVersion.major must be non-negative: $major" }
        require(minor >= 0) { "SemanticVersion.minor must be non-negative: $minor" }
        require(patch >= 0) { "SemanticVersion.patch must be non-negative: $patch" }
    }

    /**
     * Returns the canonical string representation, e.g. `1.2.3` or `1.2.3-alpha.1`.
     */
    public val canonicalForm: String
        get() = buildString {
            append("$major.$minor.$patch")
            if (preRelease != null) {
                append("-$preRelease")
            }
        }

    override fun toString(): String = canonicalForm

    public companion object {
        private val SEMVER_PATTERN = Regex(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
                "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)" +
                "(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?$"
        )

        public fun parse(raw: String): SemanticVersion {
            require(raw.isNotBlank()) { "SemanticVersion must not be blank" }
            val match = SEMVER_PATTERN.matchEntire(raw)
                ?: throw IllegalArgumentException(
                    "Invalid semantic version: $raw. " +
                        "Expected format: MAJOR.MINOR.PATCH[-prerelease]"
                )
            val major = match.groupValues[1].toInt()
            val minor = match.groupValues[2].toInt()
            val patch = match.groupValues[3].toInt()
            val preRelease = match.groupValues[4].ifBlank { null }
            return SemanticVersion(major, minor, patch, preRelease)
        }
    }
}

/**
 * Bump policy for version calculation.
 */
public enum class BumpPolicy {
    /** Increment the major version (1.2.3 → 2.0.0). */
    MAJOR,
    /** Increment the minor version (1.2.3 → 1.3.0). */
    MINOR,
    /** Increment the patch version (1.2.3 → 1.2.4). */
    PATCH,
}

/**
 * Reference to a deployment environment.
 *
 * @property value The environment identifier, e.g. `prod`, `staging`.
 */
public @JvmInline value class EnvironmentRef(public val value: String) {
    init {
        require(value.isNotBlank()) { "EnvironmentRef.value must not be blank" }
    }

    override fun toString(): String = value
}

/**
 * Factory for creating [CapabilityDescriptor] constants for release operations.
 */
public object ReleaseCapabilities {

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
        failureModel = FailureModel.Typed("RELEASE-${id.value.replace(".", "-").uppercase()}"),
        sideEffects = sideEffects,
        idempotencyPolicy = idempotency,
        providerRequirements = ProviderRequirements(
            minProviderVersion = ProviderVersion("V1"),
            authRequirements = emptySet(),
        ),
    )

    /** Descriptor for `release.calculate/v1` — READ_ONLY. */
    public val RELEASE_CALCULATE_V1: CapabilityDescriptor = descriptor(
        id = ReleaseManager.RELEASE_CALCULATE_V1,
        inputSchema = "release-calculate-request",
        outputSchema = "release-calculate-result",
        sideEffects = setOf(SideEffect.READ_ONLY),
        idempotency = IdempotencyPolicy.Strict(retrySafe = true),
    )

    /** Descriptor for `release.promote/v1` — MUTATING. */
    public val RELEASE_PROMOTE_V1: CapabilityDescriptor = descriptor(
        id = ReleaseManager.RELEASE_PROMOTE_V1,
        inputSchema = "release-promote-request",
        outputSchema = "release-promote-result",
        sideEffects = setOf(SideEffect.MUTATING),
        idempotency = IdempotencyPolicy.None,
    )
}
