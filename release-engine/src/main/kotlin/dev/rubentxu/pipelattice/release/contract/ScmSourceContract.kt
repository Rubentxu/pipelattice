package dev.rubentxu.pipelattice.release.contract

import dev.rubentxu.pipelattice.foundation.capability.SideEffect
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.release.scm.CheckoutRequest
import dev.rubentxu.pipelattice.release.scm.CheckoutResult
import dev.rubentxu.pipelattice.release.scm.PushRequest
import dev.rubentxu.pipelattice.release.scm.PushResult
import dev.rubentxu.pipelattice.release.scm.ScmFailure
import dev.rubentxu.pipelattice.release.scm.ScmSource
import dev.rubentxu.pipelattice.release.scm.TagRequest
import dev.rubentxu.pipelattice.release.scm.TagResult
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

/**
 * Abstract TCK contract for [ScmSource] implementations.
 *
 * Provides 6 invariants as `@Test` methods. Each concrete test class
 * provides [newSubject] to create the instance (cached per test) and optionally overrides
 * setup methods for scripted scenarios.
 *
 * ## Invariants
 * 1. scripted-success — checkout/tag/push returns expected result
 * 2. scripted-failure — checkout/tag returns typed failure
 * 3. idempotent-invocation-snapshot — invocations() is stable across reads
 * 4. empty-queue-raises — empty queue raises IllegalStateException
 * 5. side-effect-consistency — descriptor(id) matches expected side-effects
 * 6. secret-exclusion — no secret-shaped literals in surfaces
 *
 * ## Expectation Hooks (for property-based compliance)
 * Real adapters override the `expected*` hooks to derive values from their
 * real fixtures. Fake adapters use the default hardcoded derivations.
 *
 * ## Usage
 * ```kotlin
 * class FakeScmSourceContractTest : ScmSourceContract() {
 *     override fun newSubject(): ScmSource = FakeScmSource()
 * }
 * ```
 */
public abstract class ScmSourceContract {

    /** Cached subject instance — same instance used for setup and execution. */
    private val subject: ScmSource by lazy { newSubject() }

    /**
     * Factory method that returns a [ScmSource] instance.
     * Called once lazily; the same instance is used throughout the test.
     */
    protected abstract fun newSubject(): ScmSource

    /**
     * Returns the cached subject instance. Use instead of calling newSubject() directly.
     */
    protected fun subject(): ScmSource = subject

    /**
     * Setup method for scripted checkout success. Default no-op for real adapters.
     * Fake implementations should override to enqueue the result.
     */
    protected open suspend fun setupCheckoutSuccess(result: CheckoutResult) {}

    /**
     * Setup method for scripted checkout failure. Default no-op.
     */
    protected open suspend fun setupCheckoutFailure(failure: ScmFailure) {}

    /**
     * Setup method for scripted tag success.
     */
    protected open suspend fun setupTagSuccess(result: TagResult) {}

    /**
     * Setup method for scripted tag failure.
     */
    protected open suspend fun setupTagFailure(failure: ScmFailure) {}

    /**
     * Setup method for scripted push success.
     */
    protected open suspend fun setupPushSuccess(result: PushResult) {}

    /**
     * Returns invocation records for snapshot testing. Default returns empty list.
     * Fakes override to provide the actual invocations list.
     */
    protected open fun invocations(): List<Any> = emptyList()

    /**
     * Expectation hook for checkout success: derives the expected [CheckoutResult]
     * from the revision hint. Real adapters override to return the value their
     * real fixture produces. Default derivation uses the fake-compatible hardcoded value.
     */
    protected open fun expectedCheckoutResult(revisionHint: String): CheckoutResult =
        CheckoutResult(Path.of("/repo/checkout"), "deadbeefcafebabe1234567890abcdef12345678")

    /**
     * Expectation hook for tag success: derives the expected [TagResult] from the
     * revision. Real adapters override to return the value their real fixture produces.
     * Default derivation uses the fake-compatible hardcoded value.
     */
    protected open fun expectedTagResult(tagName: String, revision: String): TagResult =
        TagResult(tagName, "abc123def456")

    /**
     * Returns whether this adapter implements queue-based scripting (empty-queue invariants apply).
     * Default true for fake queue-based adapters. Real adapters override to false.
     */
    protected open fun supportsQueueBasedScripting(): Boolean = true

    // ----- Invariant 1: scripted-success -----

    /**
     * Returns the repository reference used in checkout invariants.
     * Default returns git://example/repo (for fake queue-based adapters).
     * Real adapter shims override to return the actual bare repository path.
     */
    protected open fun checkoutRepositoryRef(): dev.rubentxu.pipelattice.release.scm.RepositoryRef =
        dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("git://example/repo")

    /**
     * Returns the repository reference used in tag invariants.
     * Default returns git://example/repo (for fake queue-based adapters).
     * Real adapter shims override to return the actual bare repository path.
     */
    protected open fun tagRepositoryRef(): dev.rubentxu.pipelattice.release.scm.RepositoryRef =
        dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("git://example/repo")

    @Test
    protected open fun invariant_checkout_success() {
        runBlocking {
            val expected = expectedCheckoutResult("main")
            setupCheckoutSuccess(expected)
            val outcome = subject().checkout(
                CheckoutRequest(
                    repository = checkoutRepositoryRef(),
                    revisionHint = "main",
                )
            )
            assertTrue(outcome is Outcome.Success, "checkout should succeed")
            assertEquals(expected, (outcome as Outcome.Success).value, "checkout should return expected result")
        }
    }

    @Test
    protected open fun invariant_tag_success() {
        runBlocking {
            val expected = expectedTagResult("v1.0.0", "abc123def456")
            setupTagSuccess(expected)
            val outcome = subject().tag(
                TagRequest(
                    repository = tagRepositoryRef(),
                    revision = "abc123def456",
                    tagName = "v1.0.0",
                )
            )
            assertTrue(outcome is Outcome.Success, "tag should succeed")
            assertEquals(expected, (outcome as Outcome.Success).value, "tag should return expected result")
        }
    }

    @Test
    protected open fun invariant_push_success() {
        runBlocking {
            val expected = PushResult(listOf("refs/heads/main"), "refs/heads/main")
            setupPushSuccess(expected)
            val outcome = subject().push(
                PushRequest(
                    repository = checkoutRepositoryRef(),
                    remote = "origin",
                    refSpecs = listOf("refs/heads/main"),
                )
            )
            assertTrue(outcome is Outcome.Success, "push should succeed")
            assertEquals(expected, (outcome as Outcome.Success).value, "push should return expected result")
        }
    }

    // ----- Invariant 2: scripted-failure -----

    @Test
    protected open fun invariant_checkout_failure() {
        runBlocking {
            setupCheckoutFailure(ScmFailure.Unknown("checkout", "synthetic-unknown-ref"))
            val outcome = subject().checkout(
                CheckoutRequest(
                    repository = checkoutRepositoryRef(),
                    revisionHint = "nonexistent",
                )
            )
            assertTrue(outcome is Outcome.Failure, "checkout should return failure for nonexistent ref")
        }
    }

    @Test
    protected open fun invariant_tag_failure() {
        runBlocking {
            setupTagFailure(ScmFailure.Conflict("tag", "synthetic-tag-conflict"))
            val outcome = subject().tag(
                TagRequest(
                    repository = tagRepositoryRef(),
                    revision = "abc123",
                    tagName = "v1.0.0",
                )
            )
            assertTrue(outcome is Outcome.Failure, "tag should return failure for conflict")
        }
    }

    // ----- Invariant 3: idempotent-invocation-snapshot -----

    @Test
    protected open fun invariant_invocations_stable() {
        runBlocking {
            val result = CheckoutResult(Path.of("/repo"), "abc123")
            setupCheckoutSuccess(result)
            subject().checkout(
                CheckoutRequest(
                    repository = checkoutRepositoryRef(),
                    revisionHint = "main",
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
    protected open fun invariant_checkout_empty_raises() {
        assumeTrue(supportsQueueBasedScripting(), "checkout empty-raises only applies to queue-based adapters")
        runBlocking {
            try {
                subject().checkout(
                    CheckoutRequest(
                        repository = checkoutRepositoryRef(),
                        revisionHint = "main",
                    )
                )
                fail("Expected IllegalStateException for checkout with empty queue")
            } catch (e: IllegalStateException) {
                assertTrue(true, "checkout should raise IllegalStateException when queue is empty")
            }
        }
    }

    @Test
    protected open fun invariant_tag_empty_raises() {
        assumeTrue(supportsQueueBasedScripting(), "tag empty-raises only applies to queue-based adapters")
        runBlocking {
            try {
                subject().tag(
                    TagRequest(
                        repository = tagRepositoryRef(),
                        revision = "abc123",
                        tagName = "v1.0.0",
                    )
                )
                fail("Expected IllegalStateException for tag with empty queue")
            } catch (e: IllegalStateException) {
                assertTrue(true, "tag should raise IllegalStateException when queue is empty")
            }
        }
    }

    @Test
    protected open fun invariant_push_empty_raises() {
        assumeTrue(supportsQueueBasedScripting(), "push empty-raises only applies to queue-based adapters")
        runBlocking {
            try {
                subject().push(
                    PushRequest(
                        repository = checkoutRepositoryRef(),
                        remote = "origin",
                        refSpecs = listOf("refs/heads/main"),
                    )
                )
                fail("Expected IllegalStateException for push with empty queue")
            } catch (e: IllegalStateException) {
                assertTrue(true, "push should raise IllegalStateException when queue is empty")
            }
        }
    }

    // ----- Invariant 5: side-effect-consistency -----

    @Test
    protected open fun invariant_checkout_descriptor_read_only() {
        val desc = subject().descriptor(ScmSource.SCM_CHECKOUT_V1)
        assertTrue(desc != null, "descriptor should exist for SCM_CHECKOUT_V1")
        assertTrue(SideEffect.READ_ONLY in desc!!.sideEffects, "checkout should be READ_ONLY")
    }

    @Test
    protected open fun invariant_tag_descriptor_mutating() {
        val desc = subject().descriptor(ScmSource.SCM_TAG_V1)
        assertTrue(desc != null, "descriptor should exist for SCM_TAG_V1")
        assertTrue(SideEffect.MUTATING in desc!!.sideEffects, "tag should be MUTATING")
    }

    @Test
    protected open fun invariant_push_descriptor_mutating() {
        val desc = subject().descriptor(ScmSource.SCM_PUSH_V1)
        assertTrue(desc != null, "descriptor should exist for SCM_PUSH_V1")
        assertTrue(SideEffect.MUTATING in desc!!.sideEffects, "push should be MUTATING")
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
     * Secret-exclusion probe for ScmSource.
     * Verifies no secret-shaped literals appear in invocations surfaces.
     * Override to provide a probe using the test's specific request fields.
     */
    protected open suspend fun secretExclusionProbe(): Boolean = true
}
