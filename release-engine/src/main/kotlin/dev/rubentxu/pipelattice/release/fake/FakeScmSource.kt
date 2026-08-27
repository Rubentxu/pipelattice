package dev.rubentxu.pipelattice.release.scm

import dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor
import dev.rubentxu.pipelattice.foundation.capability.CapabilityId
import dev.rubentxu.pipelattice.foundation.outcome.Outcome

/**
 * A fake [ScmSource] that scripts deterministic responses for unit testing.
 *
 * This fixture operates as a FIFO queue of scripted responses.
 * Each call to [checkout], [tag], or [push] records the request in [invocations]
 * and returns the next response from the queue. If the queue is exhausted,
 * the call throws [IllegalStateException].
 *
 * ## Usage
 * ```kotlin
 * val scm = FakeScmSource()
 * scm.enqueueCheckoutSuccess(CheckoutResult(workingDirectory = Path.of("/repo"), revision = "abc123"))
 * scm.enqueueCheckoutFailure(ScmFailure.Unknown("checkout", "synthetic-unknown-ref"))
 *
 * val result1 = runBlocking { scm.checkout(CheckoutRequest(...)) }  // Success
 * val result2 = runBlocking { scm.checkout(CheckoutRequest(...)) }  // Failure
 * assertEquals(2, scm.invocations().size)
 * ```
 *
 * ## Thread safety
 * A-min assumes a single-threaded caller. The fixture is **not** safe for
 * concurrent use from multiple coroutines.
 *
 * ## Note
 * This fixture lives in `:release-engine` (module-local) per the m5 precedent.
 */
public class FakeScmSource : ScmSource {

    private val scripts: MutableList<Any> = mutableListOf()
    private val _invocations: MutableList<Invocation> = mutableListOf()

    /**
     * Enqueues a scripted checkout success result.
     */
    public fun enqueueCheckoutSuccess(result: CheckoutResult) {
        scripts.add(result)
    }

    /**
     * Enqueues a scripted checkout failure result.
     */
    public fun enqueueCheckoutFailure(failure: ScmFailure) {
        scripts.add(failure)
    }

    /**
     * Enqueues a scripted tag success result.
     */
    public fun enqueueTagSuccess(result: TagResult) {
        scripts.add(result)
    }

    /**
     * Enqueues a scripted tag failure result.
     */
    public fun enqueueTagFailure(failure: ScmFailure) {
        scripts.add(failure)
    }

    /**
     * Enqueues a scripted push success result.
     */
    public fun enqueuePushSuccess(result: PushResult) {
        scripts.add(result)
    }

    /**
     * Enqueues a scripted push failure result.
     */
    public fun enqueuePushFailure(failure: ScmFailure) {
        scripts.add(failure)
    }

    /**
     * Returns a snapshot of all invocation records.
     */
    public fun invocations(): List<Invocation> = _invocations.toList()

    /**
     * Resets the fixture, clearing both the scripted-response queue
     * and the invocation history.
     */
    public fun reset() {
        scripts.clear()
        _invocations.clear()
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun checkout(request: CheckoutRequest): Outcome<CheckoutResult, ScmFailure> {
        _invocations.add(Invocation("checkout", request))
        check(scripts.isNotEmpty()) {
            "FakeScmSource: no scripted response was enqueued for checkout: $request"
        }
        val next = scripts.removeAt(0)
        return when (next) {
            is CheckoutResult -> Outcome.Success(next)
            is ScmFailure -> Outcome.Failure(next)
            else -> error("Unexpected type in checkout queue: ${next::class}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun tag(request: TagRequest): Outcome<TagResult, ScmFailure> {
        _invocations.add(Invocation("tag", request))
        check(scripts.isNotEmpty()) {
            "FakeScmSource: no scripted response was enqueued for tag: $request"
        }
        val next = scripts.removeAt(0)
        return when (next) {
            is TagResult -> Outcome.Success(next)
            is ScmFailure -> Outcome.Failure(next)
            else -> error("Unexpected type in tag queue: ${next::class}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun push(request: PushRequest): Outcome<PushResult, ScmFailure> {
        _invocations.add(Invocation("push", request))
        check(scripts.isNotEmpty()) {
            "FakeScmSource: no scripted response was enqueued for push: $request"
        }
        val next = scripts.removeAt(0)
        return when (next) {
            is PushResult -> Outcome.Success(next)
            is ScmFailure -> Outcome.Failure(next)
            else -> error("Unexpected type in push queue: ${next::class}")
        }
    }

    override fun descriptor(id: CapabilityId): CapabilityDescriptor? = when (id) {
        ScmSource.SCM_CHECKOUT_V1 -> ScmSourceCapabilities.SCM_CHECKOUT_V1
        ScmSource.SCM_TAG_V1 -> ScmSourceCapabilities.SCM_TAG_V1
        ScmSource.SCM_PUSH_V1 -> ScmSourceCapabilities.SCM_PUSH_V1
        else -> null
    }

    /**
     * Record of an invocation for snapshot testing.
     */
    public data class Invocation(
        public val operation: String,
        public val request: Any,
    )
}
