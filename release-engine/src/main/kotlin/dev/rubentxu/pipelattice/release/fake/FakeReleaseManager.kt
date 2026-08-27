package dev.rubentxu.pipelattice.release.release

import dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor
import dev.rubentxu.pipelattice.foundation.capability.CapabilityId
import dev.rubentxu.pipelattice.foundation.outcome.Outcome

/**
 * A fake [ReleaseManager] that scripts deterministic responses for unit testing.
 *
 * This fixture operates as a FIFO queue of scripted responses.
 * Each call to [calculate] or [promote] records the request in [invocations]
 * and returns the next response from the queue. If the queue is exhausted,
 * the call throws [IllegalStateException].
 */
public class FakeReleaseManager : ReleaseManager {

    // Per-operation typed queues — eliminates the need for @Suppress("UNCHECKED_CAST")
    private val calculateScripts: MutableList<Scripted<CalculateResult, ReleaseFailure>> = mutableListOf()
    private val promoteScripts: MutableList<Scripted<PromoteResult, ReleaseFailure>> = mutableListOf()

    private val _invocations: MutableList<Invocation> = mutableListOf()

    /**
     * Enqueues a scripted calculate success result.
     */
    public fun enqueueCalculateSuccess(result: CalculateResult) {
        calculateScripts.add(ScriptedSuccess(result))
    }

    /**
     * Enqueues a scripted calculate failure result.
     */
    public fun enqueueCalculateFailure(failure: ReleaseFailure) {
        calculateScripts.add(ScriptedFailure(failure))
    }

    /**
     * Enqueues a scripted promote success result.
     */
    public fun enqueuePromoteSuccess(result: PromoteResult) {
        promoteScripts.add(ScriptedSuccess(result))
    }

    /**
     * Enqueues a scripted promote failure result.
     */
    public fun enqueuePromoteFailure(failure: ReleaseFailure) {
        promoteScripts.add(ScriptedFailure(failure))
    }

    /**
     * Returns a snapshot of all invocation records.
     */
    public fun invocations(): List<Invocation> = _invocations.toList()

    /**
     * Resets the fixture.
     */
    public fun reset() {
        calculateScripts.clear()
        promoteScripts.clear()
        _invocations.clear()
    }

    override suspend fun calculate(request: CalculateRequest): Outcome<CalculateResult, ReleaseFailure> {
        _invocations.add(Invocation("calculate", SanitizedRequest(request)))
        check(calculateScripts.isNotEmpty()) {
            "FakeReleaseManager: no scripted response was enqueued for calculate: $request"
        }
        val next = calculateScripts.removeAt(0)
        return when (next) {
            is ScriptedSuccess<CalculateResult, ReleaseFailure> -> Outcome.Success(next.result)
            is ScriptedFailure<CalculateResult, ReleaseFailure> -> Outcome.Failure(next.failure)
        }
    }

    override suspend fun promote(request: PromoteRequest): Outcome<PromoteResult, ReleaseFailure> {
        _invocations.add(Invocation("promote", SanitizedRequest(request)))
        check(promoteScripts.isNotEmpty()) {
            "FakeReleaseManager: no scripted response was enqueued for promote: $request"
        }
        val next = promoteScripts.removeAt(0)
        return when (next) {
            is ScriptedSuccess<PromoteResult, ReleaseFailure> -> Outcome.Success(next.result)
            is ScriptedFailure<PromoteResult, ReleaseFailure> -> Outcome.Failure(next.failure)
        }
    }

    override fun descriptor(id: CapabilityId): CapabilityDescriptor? = when (id) {
        ReleaseManager.RELEASE_CALCULATE_V1 -> ReleaseCapabilities.RELEASE_CALCULATE_V1
        ReleaseManager.RELEASE_PROMOTE_V1 -> ReleaseCapabilities.RELEASE_PROMOTE_V1
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
 * Typed scripted response — success or failure — used by [FakeReleaseManager] queues.
 */
private sealed interface Scripted<out S, out F>
private data class ScriptedSuccess<S, F>(val result: S) : Scripted<S, F>
private data class ScriptedFailure<S, F>(val failure: F) : Scripted<S, F>

/**
 * Wraps a request object and sanitizes its string representation to prevent
 * credential-shaped literals from leaking through [FakeReleaseManager.invocations].
 */
private class SanitizedRequest(private val request: Any) {
    private val CREDENTIAL_PATTERNS = listOf(
        Regex("AKIA[0-9A-Z]{16}"),
        Regex("ghp_[A-Za-z0-9]{36}"),
        Regex("[A-Za-z0-9+/]{40,}="),
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
