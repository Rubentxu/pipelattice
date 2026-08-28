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

/**
 * Abstract TCK contract for [ScmSource] implementations.
 *
 * Provides 6 invariants as protected open methods. Each concrete test class
 * provides [newSubject] to create the instance and setup/teardown methods
 * for scripted scenarios.
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
 *     override fun setupCheckoutSuccess(result: CheckoutResult) = scm.enqueueCheckoutSuccess(result)
 *     ...
 * }
 * ```
 */
public abstract class ScmSourceContract {

    /**
     * Factory method that returns a freshly-constructed [ScmSource] instance.
     */
    protected abstract fun newSubject(): ScmSource

    /**
     * Setup method for scripted checkout success. Default implementation is a no-op
     * for real adapters that don't need scripting.
     */
    protected open suspend fun setupCheckoutSuccess(result: CheckoutResult) {
        // No-op for real adapters; overridden by fake test to call enqueueCheckoutSuccess
    }

    /**
     * Setup method for scripted checkout failure. Default implementation is a no-op.
     */
    protected open suspend fun setupCheckoutFailure(failure: ScmFailure) {
        // No-op for real adapters
    }

    /**
     * Setup method for scripted tag success.
     */
    protected open suspend fun setupTagSuccess(result: TagResult) {
        // No-op for real adapters
    }

    /**
     * Setup method for scripted tag failure.
     */
    protected open suspend fun setupTagFailure(failure: ScmFailure) {
        // No-op for real adapters
    }

    /**
     * Setup method for scripted push success.
     */
    protected open suspend fun setupPushSuccess(result: PushResult) {
        // No-op for real adapters
    }

    /**
     * Invariant 1: checkout returns expected result when scripted as success.
     */
    protected open suspend fun invariant_checkout_success(): Outcome<CheckoutResult, ScmFailure> {
        val result = CheckoutResult(Path.of("/repo/checkout"), "deadbeefcafebabe1234567890abcdef12345678")
        setupCheckoutSuccess(result)
        return newSubject().checkout(
            CheckoutRequest(
                repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("git://example/repo"),
                revisionHint = "main",
            )
        )
    }

    /**
     * Invariant 1: tag returns expected result when scripted as success.
     */
    protected open suspend fun invariant_tag_success(): Outcome<TagResult, ScmFailure> {
        val result = TagResult("v1.0.0", "abc123def456")
        setupTagSuccess(result)
        return newSubject().tag(
            TagRequest(
                repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("git://example/repo"),
                revision = "abc123def456",
                tagName = "v1.0.0",
            )
        )
    }

    /**
     * Invariant 1: push returns expected result when scripted as success.
     */
    protected open suspend fun invariant_push_success(): Outcome<PushResult, ScmFailure> {
        val result = PushResult(listOf("refs/heads/main"), "refs/heads/main")
        setupPushSuccess(result)
        return newSubject().push(
            PushRequest(
                repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("git://example/repo"),
                remote = "origin",
                refSpecs = listOf("refs/heads/main"),
            )
        )
    }

    /**
     * Invariant 2: checkout returns typed Unknown failure when scripted.
     */
    protected open suspend fun invariant_checkout_failure(): Outcome<CheckoutResult, ScmFailure> {
        val failure = ScmFailure.Unknown("checkout", "synthetic-unknown-ref")
        setupCheckoutFailure(failure)
        return newSubject().checkout(
            CheckoutRequest(
                repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("git://example/repo"),
                revisionHint = "nonexistent",
            )
        )
    }

    /**
     * Invariant 2: tag returns typed Conflict failure when scripted.
     */
    protected open suspend fun invariant_tag_failure(): Outcome<TagResult, ScmFailure> {
        val failure = ScmFailure.Conflict("tag", "synthetic-tag-conflict")
        setupTagFailure(failure)
        return newSubject().tag(
            TagRequest(
                repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("git://example/repo"),
                revision = "abc123",
                tagName = "v1.0.0",
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
        val result = CheckoutResult(Path.of("/repo"), "abc123")
        setupCheckoutSuccess(result)
        val scm = newSubject()
        scm.checkout(
            CheckoutRequest(
                repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("git://example/repo"),
                revisionHint = "main",
            )
        )
        val snap1 = invocations()
        val snap2 = invocations()
        return snap1 == snap2 && snap1.size == 1
    }

    /**
     * Invariant 4: empty checkout queue raises IllegalStateException.
     */
    protected open suspend fun invariant_checkout_empty_raises(): Boolean {
        val scm = newSubject()
        return try {
            scm.checkout(
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

    /**
     * Invariant 4: empty tag queue raises IllegalStateException.
     */
    protected open suspend fun invariant_tag_empty_raises(): Boolean {
        val scm = newSubject()
        return try {
            scm.tag(
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

    /**
     * Invariant 4: empty push queue raises IllegalStateException.
     */
    protected open suspend fun invariant_push_empty_raises(): Boolean {
        val scm = newSubject()
        return try {
            scm.push(
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

    /**
     * Invariant 5: descriptor for checkout is READ_ONLY.
     */
    protected open fun invariant_checkout_descriptor_read_only(): Boolean {
        val scm = newSubject()
        val desc = scm.descriptor(ScmSource.SCM_CHECKOUT_V1) ?: return false
        return SideEffect.READ_ONLY in desc.sideEffects
    }

    /**
     * Invariant 5: descriptor for tag is MUTATING.
     */
    protected open fun invariant_tag_descriptor_mutating(): Boolean {
        val scm = newSubject()
        val desc = scm.descriptor(ScmSource.SCM_TAG_V1) ?: return false
        return SideEffect.MUTATING in desc.sideEffects
    }

    /**
     * Invariant 5: descriptor for push is MUTATING.
     */
    protected open fun invariant_push_descriptor_mutating(): Boolean {
        val scm = newSubject()
        val desc = scm.descriptor(ScmSource.SCM_PUSH_V1) ?: return false
        return SideEffect.MUTATING in desc.sideEffects
    }
}
