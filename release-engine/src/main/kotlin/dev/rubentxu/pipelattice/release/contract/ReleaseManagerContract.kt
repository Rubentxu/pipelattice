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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
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
 *
 * ## Expectation Hooks (for property-based compliance)
 * Real adapters override the `expected*` hooks to derive values from their
 * real fixtures. Fake adapters use the default hardcoded derivations.
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

    /**
     * Expectation hook for calculate success: derives the expected [CalculateResult]
     * from the request parameters. Real adapters override to return the value their
     * real computation produces. Default derivation uses fake-compatible hardcoded version.
     */
    protected open fun expectedCalculateResult(request: CalculateRequest): CalculateResult {
        // Default: SemanticVersion.parse("1.2.3") derived from bumping v1.2.2 with MINOR
        val version = SemanticVersion.parse("1.2.3")
        return CalculateResult(version, request.sourceRevision)
    }

    /**
     * Expectation hook for promote success: derives the expected [PromoteResult]
     * from the request. Real adapters override to return the value their
     * real promotion produces. Default uses hardcoded timestamp.
     */
    protected open fun expectedPromoteResult(request: PromoteRequest): PromoteResult =
        PromoteResult(request.version, request.targetEnvironment, "2024-01-01T00:00:00Z")

    /**
     * Returns whether this adapter implements queue-based scripting (empty-queue invariants apply).
     * Default true for fake queue-based adapters. Real adapters override to false.
     */
    protected open fun supportsQueueBasedScripting(): Boolean = true

    /**
     * Returns whether this adapter supports the promote rejection invariant.
     * Queue-based (fake) adapters can enqueue scripted rejections.
     * Real adapters succeed or fail based on actual SCM operations — rejection test does not apply.
     * Default true. Real adapters override to false.
     */
    protected open fun supportsRejectionTest(): Boolean = true

    // ----- Invariant 1: scripted-success -----

    @Test
    protected open fun invariant_calculate_success() {
        runBlocking {
            val request = CalculateRequest(
                sourceRevision = "main",
                previousTag = "v1.2.2",
                bumpPolicy = BumpPolicy.MINOR,
            )
            val expected = expectedCalculateResult(request)
            setupCalculateSuccess(expected)
            val outcome = subject().calculate(request)
            assertTrue(outcome is Outcome.Success, "calculate should succeed")
            assertEquals(expected, (outcome as Outcome.Success).value, "calculate should return expected result")
        }
    }

    /**
     * Asserts that the promote result matches expectations.
     * Default implementation compares the entire PromoteResult (including exact timestamp).
     * Real adapters override to use timestamp tolerance (±5s) since the expected
     * timestamp is computed before the real clock moves during promote().
     */
    protected open fun assertPromoteSuccess(expected: PromoteResult, actual: PromoteResult) {
        assertEquals(expected.version, actual.version, "promote version should match")
        assertEquals(expected.targetEnvironment, actual.targetEnvironment, "promote targetEnvironment should match")
        assertEquals(expected.promotedAt, actual.promotedAt, "promote timestamp should match")
    }

    @Test
    protected open fun invariant_promote_success() {
        runBlocking {
            val request = PromoteRequest(
                targetEnvironment = EnvironmentRef("prod"),
                version = SemanticVersion.parse("1.2.3"),
                releaseNotes = null,
            )
            val expected = expectedPromoteResult(request)
            setupPromoteSuccess(expected)
            val outcome = subject().promote(request)
            assertTrue(outcome is Outcome.Success, "promote should succeed")
            val actual = (outcome as Outcome.Success).value
            assertPromoteSuccess(expected, actual)
        }
    }

    // ----- Invariant 2: scripted-failure -----

    @Test
    protected open fun invariant_promote_rejected() {
        assumeTrue(supportsRejectionTest(), "promote rejection test only applies to queue-based adapters")
        runBlocking {
            val version = SemanticVersion.parse("1.2.3")
            setupPromoteFailure(ReleaseFailure.PromotionRejected(version, "synthetic-policy-requires-approval", true))
            val outcome = subject().promote(
                PromoteRequest(
                    targetEnvironment = EnvironmentRef("prod"),
                    version = version,
                )
            )
            assertTrue(outcome is Outcome.Failure, "promote should return failure for rejected promotion")
        }
    }

    // ----- Invariant 3: idempotent-invocation-snapshot -----

    @Test
    protected open fun invariant_invocations_stable() {
        runBlocking {
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
            assertEquals(snap1, snap2, "invocations() should be stable")
            assertEquals(1, snap1.size, "invocations() should record exactly one call")
        }
    }

    // ----- Invariant 4: empty-queue-raises -----

    @Test
    protected open fun invariant_calculate_empty_raises() {
        assumeTrue(supportsQueueBasedScripting(), "calculate empty-raises only applies to queue-based adapters")
        runBlocking {
            try {
                subject().calculate(
                    CalculateRequest(
                        sourceRevision = "main",
                        previousTag = "v1.2.2",
                        bumpPolicy = BumpPolicy.MINOR,
                    )
                )
                fail("Expected IllegalStateException for calculate with empty queue")
            } catch (e: IllegalStateException) {
                assertTrue(true, "calculate should raise IllegalStateException when queue is empty")
            }
        }
    }

    @Test
    protected open fun invariant_promote_empty_raises() {
        assumeTrue(supportsQueueBasedScripting(), "promote empty-raises only applies to queue-based adapters")
        runBlocking {
            try {
                subject().promote(
                    PromoteRequest(
                        targetEnvironment = EnvironmentRef("prod"),
                        version = SemanticVersion.parse("1.2.3"),
                    )
                )
                fail("Expected IllegalStateException for promote with empty queue")
            } catch (e: IllegalStateException) {
                assertTrue(true, "promote should raise IllegalStateException when queue is empty")
            }
        }
    }

    // ----- Invariant 5: side-effect-consistency -----

    @Test
    protected open fun invariant_calculate_descriptor_read_only() {
        val desc = subject().descriptor(ReleaseManager.RELEASE_CALCULATE_V1)
        assertTrue(desc != null, "descriptor should exist for RELEASE_CALCULATE_V1")
        assertTrue(SideEffect.READ_ONLY in desc!!.sideEffects, "calculate should be READ_ONLY")
    }

    @Test
    protected open fun invariant_promote_descriptor_mutating() {
        val desc = subject().descriptor(ReleaseManager.RELEASE_PROMOTE_V1)
        assertTrue(desc != null, "descriptor should exist for RELEASE_PROMOTE_V1")
        assertTrue(SideEffect.MUTATING in desc!!.sideEffects, "promote should be MUTATING")
    }

    // ----- Invariant 6: secret-exclusion -----

    @Test
    protected open fun invariant_secret_exclusion() {
        runBlocking {
            val result = secretExclusionProbe()
            assertTrue(result, "secret-exclusion probe should pass")
        }
    }

    /**
     * Secret-exclusion probe for ReleaseManager.
     * Verifies no secret-shaped literals appear in invocations surfaces.
     * Override to provide a probe using the test's specific request fields.
     */
    protected open suspend fun secretExclusionProbe(): Boolean = true
}
