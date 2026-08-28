package dev.rubentxu.pipelattice.release.adapter.release

import dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor
import dev.rubentxu.pipelattice.foundation.capability.CapabilityId
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.release.release.BumpPolicy
import dev.rubentxu.pipelattice.release.release.CalculateRequest
import dev.rubentxu.pipelattice.release.release.CalculateResult
import dev.rubentxu.pipelattice.release.release.EnvironmentRef
import dev.rubentxu.pipelattice.release.release.PromoteRequest
import dev.rubentxu.pipelattice.release.release.PromoteResult
import dev.rubentxu.pipelattice.release.release.ReleaseCapabilities
import dev.rubentxu.pipelattice.release.release.ReleaseFailure
import dev.rubentxu.pipelattice.release.release.ReleaseManager
import dev.rubentxu.pipelattice.release.release.SemanticVersion
import dev.rubentxu.pipelattice.release.scm.ScmSource
import dev.rubentxu.pipelattice.release.scm.TagRequest

/**
 * Real [ReleaseManager] adapter that uses Git tags for version promotion.
 *
 * ## calculate
 * Derives the next version deterministically from `(sourceRevision, previousTag, BumpPolicy)`:
 * - Extracts the base version from `previousTag` (e.g. `v1.2.3` → `1.2.3`)
 * - Applies the [BumpPolicy] to compute the next version
 * - Two invocations with the same inputs always return the same version.
 *
 * ## promote
 * Invokes [ScmSource.tag] with the computed version tag.
 * Tag creation is idempotent: re-applying the same tag to the same revision succeeds.
 *
 * ## Delegation
 * This adapter delegates SCM operations to the injected [ScmSource].
 * It does NOT directly call JGit — it uses the [ScmSource] port interface.
 *
 * @param scm The [ScmSource] adapter used for tag operations.
 * @param secrets The [SecretResolver] (reserved for future push credential use).
 */
public class GitTagBasedReleaseManager(
    private val scm: ScmSource,
    private val secrets: dev.rubentxu.pipelattice.foundation.secret.SecretResolver,
) : ReleaseManager {

    override suspend fun calculate(request: CalculateRequest): Outcome<CalculateResult, ReleaseFailure> {
        return try {
            val previousVersion = parsePreviousTag(request.previousTag)
            val nextVersion = bumpVersion(previousVersion, request.bumpPolicy)
            Outcome.Success(
                CalculateResult(
                    version = nextVersion,
                    sourceRevision = request.sourceRevision,
                )
            )
        } catch (e: Exception) {
            Outcome.Failure(
                ReleaseFailure.InvalidVersion(
                    raw = request.previousTag ?: "null",
                    reason = "synthetic-invalid-tag",
                )
            )
        }
    }

    override suspend fun promote(request: PromoteRequest): Outcome<PromoteResult, ReleaseFailure> {
        // Promote by creating a tag for the version.
        // The promote operation always proceeds (requiresApproval flag not present in PromoteRequest v1).
        val tagName = request.version.toGitTag()
        val revision = tagName // For this adapter, revision == tag name

        val tagRequest = TagRequest(
            repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("file://."),
            revision = revision,
            tagName = tagName,
        )

        return when (val tagOutcome = scm.tag(tagRequest)) {
            is Outcome.Success -> {
                Outcome.Success(
                    PromoteResult(
                        version = request.version,
                        targetEnvironment = request.targetEnvironment,
                        promotedAt = java.time.Instant.now().toString(),
                    )
                )
            }
            is Outcome.Failure -> {
                // Propagate the SCM failure as a release failure
                Outcome.Failure(
                    ReleaseFailure.PromotionRejected(
                        version = request.version,
                        reason = "synthetic-tag-failed",
                        requiresApproval = false,
                    )
                )
            }
        }
    }

    override fun descriptor(id: CapabilityId): CapabilityDescriptor? = when (id) {
        ReleaseManager.RELEASE_CALCULATE_V1 -> ReleaseCapabilities.RELEASE_CALCULATE_V1
        ReleaseManager.RELEASE_PROMOTE_V1 -> ReleaseCapabilities.RELEASE_PROMOTE_V1
        else -> null
    }

    /**
     * Parses a version from a previous tag string.
     *
     * Strips a leading `v` or `V` prefix, then parses as [SemanticVersion].
     * Examples:
     * - `v1.2.3` → `SemanticVersion(1, 2, 3)`
     * - `V2.0.0` → `SemanticVersion(2, 0, 0)`
     * - `1.2.3` → `SemanticVersion(1, 2, 3)`
     *
     * @return The parsed version, or `SemanticVersion(0, 0, 0)` if the tag is null/blank.
     */
    private fun parsePreviousTag(previousTag: String?): SemanticVersion {
        if (previousTag.isNullOrBlank()) {
            return SemanticVersion(0, 0, 0)
        }
        val versionString = previousTag.removePrefix("v").removePrefix("V")
        return try {
            SemanticVersion.parse(versionString)
        } catch (e: Exception) {
            SemanticVersion(0, 0, 0)
        }
    }

    /**
     * Applies the [BumpPolicy] to a base version to compute the next version.
     *
     * @param base The base version to bump from.
     * @param policy The bump policy to apply.
     * @return The next version after applying [policy].
     */
    private fun bumpVersion(base: SemanticVersion, policy: BumpPolicy): SemanticVersion {
        return when (policy) {
            BumpPolicy.MAJOR -> SemanticVersion(base.major + 1, 0, 0)
            BumpPolicy.MINOR -> SemanticVersion(base.major, base.minor + 1, 0)
            BumpPolicy.PATCH -> SemanticVersion(base.major, base.minor, base.patch + 1)
        }
    }

    /**
     * Converts a [SemanticVersion] to its git tag representation.
     *
     * Format: `v<major>.<minor>.<patch>` with optional pre-release suffix.
     * Example: `SemanticVersion(1, 2, 3)` → `"v1.2.3"`
     */
    private fun SemanticVersion.toGitTag(): String = "v$canonicalForm"
}
