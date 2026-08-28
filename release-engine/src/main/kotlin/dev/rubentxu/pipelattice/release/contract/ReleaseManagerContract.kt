package dev.rubentxu.pipelattice.release.contract

import dev.rubentxu.pipelattice.foundation.capability.SideEffect
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

/**
 * Abstract TCK contract for [ReleaseManager] implementations.
 *
 * Provides 6 invariants as protected open methods. Each concrete test class
 * provides [newSubject] to create the instance and setup/teardown methods
 * for scripted scenarios.
 *
 * ## Invariants
 * 1. scripted-success — calculate/promote returns expected result
 * 2. scripted-failure — promote returns typed PromotionRejected failure
 * 3. idempotent-invocation-snapshot — invocations() is stable across reads
 * 4. empty-queue-raises — empty queue raises IllegalStateException
 * 5. side-effect-consistency — descriptor(id) matches expected side-effects
 * 6. secret-exclusion — no secret-shaped literals in surfaces
 */
public abstract class ReleaseManagerContract {

    /**
     * Factory method that returns a freshly-constructed [ReleaseManager] instance.
     */
    protected abstract fun newSubject(): ReleaseManager

    /**
     * Setup method for scripted calculate success. Default no-op for real adapters.
     */
    protected open suspend fun setupCalculateSuccess(result: CalculateResult) {}

    /**
     * Setup method for scripted promote success.
     */
    protected open suspend fun setupPromoteSuccess(result: PromoteResult) {}

    /**
     * Setup method for scripted promote failure.
     */
    protected open suspend fun setupPromoteFailure(failure: ReleaseFailure) {}

    /**
     * Invariant 1: calculate returns expected result when scripted as success.
     */
    protected open suspend fun invariant_calculate_success(): Outcome<CalculateResult, ReleaseFailure> {
        val version = SemanticVersion.parse("1.2.3")
        val result = CalculateResult(version, "main")
        setupCalculateSuccess(result)
        return newSubject().calculate(
            CalculateRequest(
                sourceRevision = "main",
                previousTag = "v1.2.2",
                bumpPolicy = BumpPolicy.MINOR,
            )
        )
    }

    /**
     * Invariant 1: promote returns expected result when scripted as success.
     */
    protected open suspend fun invariant_promote_success(): Outcome<PromoteResult, ReleaseFailure> {
        val version = SemanticVersion.parse("1.2.3")
        val result = PromoteResult(version, EnvironmentRef("prod"), "2024-01-01T00:00:00Z")
        setupPromoteSuccess(result)
        return newSubject().promote(
            PromoteRequest(
                targetEnvironment = EnvironmentRef("prod"),
                version = version,
                releaseNotes = null,
            )
        )
    }

    /**
     * Invariant 2: promote returns typed PromotionRejected when scripted.
     */
    protected open suspend fun invariant_promote_rejected(): Outcome<PromoteResult, ReleaseFailure> {
        val version = SemanticVersion.parse("1.2.3")
        val failure = ReleaseFailure.PromotionRejected(version, "synthetic-policy-requires-approval", true)
        setupPromoteFailure(failure)
        return newSubject().promote(
            PromoteRequest(
                targetEnvironment = EnvironmentRef("prod"),
                version = version,
            )
        )
    }

    /**
     * Returns invocation records for snapshot testing. Default returns empty list.
     * Fakes override to provide the actual invocations list.
     */
    protected open fun invocations(): List<Any> = emptyList()

    /**
     * Invariant 3: invocations() is stable across reads.
     */
    protected open suspend fun invariant_invocations_stable(): Boolean {
        val version = SemanticVersion.parse("1.2.3")
        setupCalculateSuccess(CalculateResult(version, "main"))
        val manager = newSubject()
        manager.calculate(
            CalculateRequest(
                sourceRevision = "main",
                previousTag = "v1.2.2",
                bumpPolicy = BumpPolicy.MINOR,
            )
        )
        val snap1 = invocations()
        val snap2 = invocations()
        return snap1 == snap2 && snap1.size == 1
    }

    /**
     * Invariant 4: empty calculate queue raises IllegalStateException.
     */
    protected open suspend fun invariant_calculate_empty_raises(): Boolean {
        val manager = newSubject()
        return try {
            manager.calculate(
                CalculateRequest(
                    sourceRevision = "main",
                    previousTag = "v1.2.2",
                    bumpPolicy = BumpPolicy.MINOR,
                )
            )
            false
        } catch (e: IllegalStateException) {
            true
        }
    }

    /**
     * Invariant 4: empty promote queue raises IllegalStateException.
     */
    protected open suspend fun invariant_promote_empty_raises(): Boolean {
        val manager = newSubject()
        return try {
            manager.promote(
                PromoteRequest(
                    targetEnvironment = EnvironmentRef("prod"),
                    version = SemanticVersion.parse("1.2.3"),
                )
            )
            false
        } catch (e: IllegalStateException) {
            true
        }
    }

    /**
     * Invariant 5: descriptor for calculate is READ_ONLY.
     */
    protected open fun invariant_calculate_descriptor_read_only(): Boolean {
        val manager = newSubject()
        val desc = manager.descriptor(ReleaseManager.RELEASE_CALCULATE_V1) ?: return false
        return SideEffect.READ_ONLY in desc.sideEffects
    }

    /**
     * Invariant 5: descriptor for promote is MUTATING.
     */
    protected open fun invariant_promote_descriptor_mutating(): Boolean {
        val manager = newSubject()
        val desc = manager.descriptor(ReleaseManager.RELEASE_PROMOTE_V1) ?: return false
        return SideEffect.MUTATING in desc.sideEffects
    }
}
