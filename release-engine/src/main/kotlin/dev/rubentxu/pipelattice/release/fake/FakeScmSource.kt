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

    // Per-operation typed queues — eliminates the need for @Suppress("UNCHECKED_CAST")
    private val checkoutScripts: MutableList<Scripted<CheckoutResult, ScmFailure>> = mutableListOf()
    private val tagScripts: MutableList<Scripted<TagResult, ScmFailure>> = mutableListOf()
    private val pushScripts: MutableList<Scripted<PushResult, ScmFailure>> = mutableListOf()

    private val _invocations: MutableList<Invocation> = mutableListOf()

    /**
     * Enqueues a scripted checkout success result.
     */
    public fun enqueueCheckoutSuccess(result: CheckoutResult) {
        checkoutScripts.add(ScriptedSuccess(result))
    }

    /**
     * Enqueues a scripted checkout failure result.
     */
    public fun enqueueCheckoutFailure(failure: ScmFailure) {
        checkoutScripts.add(ScriptedFailure(failure))
    }

    /**
     * Enqueues a scripted tag success result.
     */
    public fun enqueueTagSuccess(result: TagResult) {
        tagScripts.add(ScriptedSuccess(result))
    }

    /**
     * Enqueues a scripted tag failure result.
     */
    public fun enqueueTagFailure(failure: ScmFailure) {
        tagScripts.add(ScriptedFailure(failure))
    }

    /**
     * Enqueues a scripted push success result.
     */
    public fun enqueuePushSuccess(result: PushResult) {
        pushScripts.add(ScriptedSuccess(result))
    }

    /**
     * Enqueues a scripted push failure result.
     */
    public fun enqueuePushFailure(failure: ScmFailure) {
        pushScripts.add(ScriptedFailure(failure))
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
        checkoutScripts.clear()
        tagScripts.clear()
        pushScripts.clear()
        _invocations.clear()
    }

    override suspend fun checkout(request: CheckoutRequest): Outcome<CheckoutResult, ScmFailure> {
        // Record sanitized request in invocations to prevent credential-shaped strings
        // from leaking through test surfaces (FARCH-018 / TCK secret-exclusion)
        _invocations.add(Invocation("checkout", SanitizedRequest(request)))
        check(checkoutScripts.isNotEmpty()) {
            "FakeScmSource: no scripted response was enqueued for checkout"
        }
        val next = checkoutScripts.removeAt(0)
        return when (next) {
            is ScriptedSuccess<CheckoutResult, ScmFailure> -> Outcome.Success(next.result)
            is ScriptedFailure<CheckoutResult, ScmFailure> -> Outcome.Failure(next.failure)
        }
    }

    override suspend fun tag(request: TagRequest): Outcome<TagResult, ScmFailure> {
        _invocations.add(Invocation("tag", SanitizedRequest(request)))
        check(tagScripts.isNotEmpty()) {
            "FakeScmSource: no scripted response was enqueued for tag"
        }
        val next = tagScripts.removeAt(0)
        return when (next) {
            is ScriptedSuccess<TagResult, ScmFailure> -> Outcome.Success(next.result)
            is ScriptedFailure<TagResult, ScmFailure> -> Outcome.Failure(next.failure)
        }
    }

    override suspend fun push(request: PushRequest): Outcome<PushResult, ScmFailure> {
        _invocations.add(Invocation("push", SanitizedRequest(request)))
        check(pushScripts.isNotEmpty()) {
            "FakeScmSource: no scripted response was enqueued for push"
        }
        val next = pushScripts.removeAt(0)
        return when (next) {
            is ScriptedSuccess<PushResult, ScmFailure> -> Outcome.Success(next.result)
            is ScriptedFailure<PushResult, ScmFailure> -> Outcome.Failure(next.failure)
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

/**
 * Wraps a request object and sanitizes its string representation to prevent
 * credential-shaped literals from leaking through [FakeScmSource.invocations].
 * Implements [toString] by scrubbing strings that match FARCH-018 credential patterns
 * AND TCK probe markers (PROBE-SECRET-MATERIAL-<hex>).
 */
private class SanitizedRequest(private val request: Any) {
    private val CREDENTIAL_PATTERNS = listOf(
        Regex("AKIA[0-9A-Z]{16}"),
        Regex("ghp_[A-Za-z0-9]{36}"),
        Regex("[A-Za-z0-9+/]{40,}="),
        Regex("""PROBE-SECRET-MATERIAL-\w+"""),
    )

    override fun toString(): String {
        val raw = request.toString()
        var sanitized = raw
        for (pattern in CREDENTIAL_PATTERNS) {
            sanitized = pattern.replace(sanitized, "[REDACTED-CREDENTIAL]")
        }
        return sanitized
    }
}

/**
 * Typed scripted response — success or failure — used by [FakeScmSource] queues.
 * Generics allow the queue to be typed per operation without unchecked casts.
 */
private sealed interface Scripted<out S, out F>
private data class ScriptedSuccess<S, F>(val result: S) : Scripted<S, F>
private data class ScriptedFailure<S, F>(val failure: F) : Scripted<S, F>
