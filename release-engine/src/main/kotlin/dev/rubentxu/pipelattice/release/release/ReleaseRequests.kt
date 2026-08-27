package dev.rubentxu.pipelattice.release.release

/**
 * Request to calculate the next version based on source state.
 *
 * @property sourceRevision The source revision (branch, tag, or commit).
 * @property previousTag The previous release tag (if any).
 * @property bumpPolicy The version bump policy to apply.
 */
public data class CalculateRequest(
    public val sourceRevision: String,
    public val previousTag: String?,
    public val bumpPolicy: BumpPolicy,
)

/**
 * Result of a successful version calculation.
 *
 * @property version The calculated semantic version.
 * @property sourceRevision The source revision used for calculation.
 */
public data class CalculateResult(
    public val version: SemanticVersion,
    public val sourceRevision: String,
) {
    public sealed interface Success {
        public val version: SemanticVersion
        public val sourceRevision: String

        public data class CalculateOk(
            override val version: SemanticVersion,
            override val sourceRevision: String,
        ) : Success
    }
}

/**
 * Request to promote a version to a target environment.
 *
 * @property targetEnvironment The environment to promote to.
 * @property version The version to promote.
 * @property releaseNotes Optional release notes.
 */
public data class PromoteRequest(
    public val targetEnvironment: EnvironmentRef,
    public val version: SemanticVersion,
    public val releaseNotes: String? = null,
)

/**
 * Result of a successful promotion operation.
 *
 * @property version The promoted version.
 * @property targetEnvironment The environment it was promoted to.
 * @property promotedAt Timestamp of promotion.
 */
public data class PromoteResult(
    public val version: SemanticVersion,
    public val targetEnvironment: EnvironmentRef,
    public val promotedAt: String,
) {
    public sealed interface Success {
        public val version: SemanticVersion
        public val targetEnvironment: EnvironmentRef
        public val promotedAt: String

        public data class PromoteOk(
            override val version: SemanticVersion,
            override val targetEnvironment: EnvironmentRef,
            override val promotedAt: String,
        ) : Success
    }
}
