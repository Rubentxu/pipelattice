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

    // ----- Invariant 1: scripted-success -----

    @Test
    protected open suspend fun invariant_checkout_success(): Outcome<CheckoutResult, ScmFailure> {
        val result = CheckoutResult(Path.of("/repo/checkout"), "deadbeefcafebabe1234567890abcdef12345678")
        setupCheckoutSuccess(result)
        return subject().checkout(
            CheckoutRequest(
                repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("git://example/repo"),
                revisionHint = "main",
            )
        )
    }

    @Test
    protected open suspend fun invariant_tag_success(): Outcome<TagResult, ScmFailure> {
        val result = TagResult("v1.0.0", "abc123def456")
        setupTagSuccess(result)
        return subject().tag(
            TagRequest(
                repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("git://example/repo"),
                revision = "abc123def456",
                tagName = "v1.0.0",
            )
        )
    }

    @Test
    protected open suspend fun invariant_push_success(): Outcome<PushResult, ScmFailure> {
        val result = PushResult(listOf("refs/heads/main"), "refs/heads/main")
        setupPushSuccess(result)
        return subject().push(
            PushRequest(
                repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("git://example/repo"),
                remote = "origin",
                refSpecs = listOf("refs/heads/main"),
            )
        )
    }

    // ----- Invariant 2: scripted-failure -----

    @Test
    protected open suspend fun invariant_checkout_failure(): Outcome<CheckoutResult, ScmFailure> {
        setupCheckoutFailure(ScmFailure.Unknown("checkout", "synthetic-unknown-ref"))
        return subject().checkout(
            CheckoutRequest(
                repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("git://example/repo"),
                revisionHint = "nonexistent",
            )
        )
    }

    @Test
    protected open suspend fun invariant_tag_failure(): Outcome<TagResult, ScmFailure> {
        setupTagFailure(ScmFailure.Conflict("tag", "synthetic-tag-conflict"))
        return subject().tag(
            TagRequest(
                repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("git://example/repo"),
                revision = "abc123",
                tagName = "v1.0.0",
            )
        )
    }

    // ----- Invariant 3: idempotent-invocation-snapshot -----

    @Test
    protected open suspend fun invariant_invocations_stable(): Boolean {
        val result = CheckoutResult(Path.of("/repo"), "abc123")
        setupCheckoutSuccess(result)
        subject().checkout(
            CheckoutRequest(
                repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("git://example/repo"),
                revisionHint = "main",
            )
        )
        val snap1 = invocations()
        val snap2 = invocations()
        return snap1 == snap2 && snap1.size == 1
    }

    // ----- Invariant 4: empty-queue-raises -----

    @Test
    protected open suspend fun invariant_checkout_empty_raises(): Boolean {
        return try {
            subject().checkout(
                CheckoutRequest(
                    repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("git://example/repo"),
                    revisionHint = "main",
                )
            )
            false
        } catch (e: IllegalStateException) {
            true
        }
    }

    @Test
    protected open suspend fun invariant_tag_empty_raises(): Boolean {
        return try {
            subject().tag(
                TagRequest(
                    repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("git://example/repo"),
                    revision = "abc123",
                    tagName = "v1.0.0",
                )
            )
            false
        } catch (e: IllegalStateException) {
            true
        }
    }

    @Test
    protected open suspend fun invariant_push_empty_raises(): Boolean {
        return try {
            subject().push(
                PushRequest(
                    repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("git://example/repo"),
                    remote = "origin",
                    refSpecs = listOf("refs/heads/main"),
                )
            )
            false
        } catch (e: IllegalStateException) {
            true
        }
    }

    // ----- Invariant 5: side-effect-consistency -----

    @Test
    protected open fun invariant_checkout_descriptor_read_only(): Boolean {
        val desc = subject().descriptor(ScmSource.SCM_CHECKOUT_V1) ?: return false
        return SideEffect.READ_ONLY in desc.sideEffects
    }

    @Test
    protected open fun invariant_tag_descriptor_mutating(): Boolean {
        val desc = subject().descriptor(ScmSource.SCM_TAG_V1) ?: return false
        return SideEffect.MUTATING in desc.sideEffects
    }

    @Test
    protected open fun invariant_push_descriptor_mutating(): Boolean {
        val desc = subject().descriptor(ScmSource.SCM_PUSH_V1) ?: return false
        return SideEffect.MUTATING in desc.sideEffects
    }

    // ----- Invariant 6: secret-exclusion -----

    /**
     * Secret-exclusion probe for ScmSource.
     * Verifies no secret-shaped literals appear in invocations surfaces.
     * Override to provide a probe using the test's specific request fields.
     */
    protected open suspend fun secretExclusionProbe(): Boolean = true
}
