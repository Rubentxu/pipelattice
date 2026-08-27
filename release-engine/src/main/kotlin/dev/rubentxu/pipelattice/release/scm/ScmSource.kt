package dev.rubentxu.pipelattice.release.scm

import dev.rubentxu.pipelattice.foundation.outcome.Outcome

/**
 * Port for interacting with Source Control Management systems.
 *
 * Provides checkout, tag, and push operations with typed request/result/failure models.
 *
 * ## Usage
 * ```kotlin
 * class JGitScmSource : ScmSource {
 *     override suspend fun checkout(request: CheckoutRequest): Outcome<CheckoutResult, ScmFailure> { ... }
 *     override suspend fun tag(request: TagRequest): Outcome<TagResult, ScmFailure> { ... }
 *     override suspend fun push(request: PushRequest): Outcome<PushResult, ScmFailure> { ... }
 * }
 * ```
 *
 * Each capability ships a [dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor]
 * constant via [ScmSourceCapabilities].
 */
public interface ScmSource {

    /**
     * Checks out a specific revision of a repository.
     *
     * @param request The checkout request specifying repository and revision.
     * @return [Outcome.Success] with [CheckoutResult] on success,
     *         or [Outcome.Failure] with [ScmFailure] on error.
     */
    public suspend fun checkout(request: CheckoutRequest): Outcome<CheckoutResult, ScmFailure>

    /**
     * Tags a revision in the repository.
     *
     * @param request The tag request specifying repository, revision, and tag name.
     * @return [Outcome.Success] with [TagResult] on success,
     *         or [Outcome.Failure] with [ScmFailure] on error.
     */
    public suspend fun tag(request: TagRequest): Outcome<TagResult, ScmFailure>

    /**
     * Pushes local commits to a remote repository.
     *
     * @param request The push request specifying repository and refs to push.
     * @return [Outcome.Success] with [PushResult] on success,
     *         or [Outcome.Failure] with [ScmFailure] on error.
     */
    public suspend fun push(request: PushRequest): Outcome<PushResult, ScmFailure>

    /**
     * Returns the [dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor]
     * for the given [dev.rubentxu.pipelattice.foundation.capability.CapabilityId],
     * or null if the operation is not supported.
     */
    public fun descriptor(id: dev.rubentxu.pipelattice.foundation.capability.CapabilityId):
        dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor?

    public companion object {
        /** Capability constant for `scm.checkout/v1`. */
        public val SCM_CHECKOUT_V1: dev.rubentxu.pipelattice.foundation.capability.CapabilityId =
            dev.rubentxu.pipelattice.foundation.capability.CapabilityId.parse("scm.checkout/v1")

        /** Capability constant for `scm.tag/v1`. */
        public val SCM_TAG_V1: dev.rubentxu.pipelattice.foundation.capability.CapabilityId =
            dev.rubentxu.pipelattice.foundation.capability.CapabilityId.parse("scm.tag/v1")

        /** Capability constant for `scm.push/v1`. */
        public val SCM_PUSH_V1: dev.rubentxu.pipelattice.foundation.capability.CapabilityId =
            dev.rubentxu.pipelattice.foundation.capability.CapabilityId.parse("scm.push/v1")
    }
}
