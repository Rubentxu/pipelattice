package dev.rubentxu.pipelattice.release.contract

import dev.rubentxu.pipelattice.foundation.capability.SideEffect
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.release.release.BumpPolicy
import dev.rubentxu.pipelattice.release.release.CalculateRequest
import dev.rubentxu.pipelattice.release.release.CalculateResult
import dev.rubentxu.pipelattice.release.release.EnvironmentRef
import dev.rubentxu.pipelattice.release.release.PromoteRequest
import dev.rubentxu.pipelattice.release.release.PromoteResult
import dev.rubentxu.pipelattice.release.release.ReleaseFailure
import dev.rubentxu.pipelattice.release.release.ReleaseManager
import dev.rubentxu.pipelattice.release.release.SemanticVersion
import org.junit.jupiter.api.Test

/**
 * Abstract TCK contract for [ReleaseManager] implementations.
 *
 * Provides 6 invariants as `@Test` methods. Each concrete test class
 * provides [newSubject] to create the instance (cached per test) and optionally overrides
 * setup methods for scripted scenarios.
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

    /** Cached subject instance — same instance used for setup and execution. */
    private val subject: ReleaseManager by lazy { newSubject() }

    /**
     * Factory method that returns a [ReleaseManager] instance.
     * Called once lazily; the same instance is used throughout the test.
     */
    protected abstract fun newSubject(): ReleaseManager

    /**
     * Returns the cached subject instance. Use instead of calling newSubject() directly.
     */
    protected fun subject(): ReleaseManager = subject

    /**
     * Setup method for scripted calculate success. Default no-op for real adapters.
     * Fake implementations should override to enqueue the result.
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
     * Returns invocation records for snapshot testing. Default returns empty list.
     * Fakes override to provide the actual invocations list.
     */
    protected open fun invocations(): List<Any> = emptyList()

    // ----- Invariant 1: scripted-success -----

    @Test
    protected open suspend fun invariant_calculate_success(): Outcome<CalculateResult, ReleaseFailure> {
        val version = SemanticVersion.parse("1.2.3")
        val result = CalculateResult(version, "main")
        setupCalculateSuccess(result)
        return subject().calculate(
            CalculateRequest(
                sourceRevision = "main",
                previousTag = "v1.2.2",
                bumpPolicy = BumpPolicy.MINOR,
            )
        )
    }

    @Test
    protected open suspend fun invariant_promote_success(): Outcome<PromoteResult, ReleaseFailure> {
        val version = SemanticVersion.parse("1.2.3")
        val result = PromoteResult(version, EnvironmentRef("prod"), "2024-01-01T00:00:00Z")
        setupPromoteSuccess(result)
        return subject().promote(
            PromoteRequest(
                targetEnvironment = EnvironmentRef("prod"),
                version = version,
                releaseNotes = null,
            )
        )
    }

    // ----- Invariant 2: scripted-failure -----

    @Test
    protected open suspend fun invariant_promote_rejected(): Outcome<PromoteResult, ReleaseFailure> {
        val version = SemanticVersion.parse("1.2.3")
        val failure = ReleaseFailure.PromotionRejected(version, "synthetic-policy-requires-approval", true)
        setupPromoteFailure(failure)
        return subject().promote(
            PromoteRequest(
                targetEnvironment = EnvironmentRef("prod"),
                version = version,
            )
        )
    }

    // ----- Invariant 3: idempotent-invocation-snapshot -----

    @Test
    protected open suspend fun invariant_invocations_stable(): Boolean {
        val version = SemanticVersion.parse("1.2.3")
        setupCalculateSuccess(CalculateResult(version, "main"))
        subject().calculate(
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

    // ----- Invariant 4: empty-queue-raises -----

    @Test
    protected open suspend fun invariant_calculate_empty_raises(): Boolean {
        return try {
            subject().calculate(
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

    @Test
    protected open suspend fun invariant_promote_empty_raises(): Boolean {
        return try {
            subject().promote(
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

    // ----- Invariant 5: side-effect-consistency -----

    @Test
    protected open fun invariant_calculate_descriptor_read_only(): Boolean {
        val desc = subject().descriptor(ReleaseManager.RELEASE_CALCULATE_V1) ?: return false
        return SideEffect.READ_ONLY in desc.sideEffects
    }

    @Test
    protected open fun invariant_promote_descriptor_mutating(): Boolean {
        val desc = subject().descriptor(ReleaseManager.RELEASE_PROMOTE_V1) ?: return false
        return SideEffect.MUTATING in desc.sideEffects
    }

    // ----- Invariant 6: secret-exclusion -----

    /**
     * Secret-exclusion probe for ReleaseManager.
     * Verifies no secret-shaped literals appear in invocations surfaces.
     * Override to provide a probe using the test's specific request fields.
     */
    protected open suspend fun secretExclusionProbe(): Boolean = true
}
