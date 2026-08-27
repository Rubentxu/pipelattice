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

    private val scripts: MutableList<Any> = mutableListOf()
    private val _invocations: MutableList<Invocation> = mutableListOf()

    /**
     * Enqueues a scripted calculate success result.
     */
    public fun enqueueCalculateSuccess(result: CalculateResult) {
        scripts.add(result)
    }

    /**
     * Enqueues a scripted calculate failure result.
     */
    public fun enqueueCalculateFailure(failure: ReleaseFailure) {
        scripts.add(failure)
    }

    /**
     * Enqueues a scripted promote success result.
     */
    public fun enqueuePromoteSuccess(result: PromoteResult) {
        scripts.add(result)
    }

    /**
     * Enqueues a scripted promote failure result.
     */
    public fun enqueuePromoteFailure(failure: ReleaseFailure) {
        scripts.add(failure)
    }

    /**
     * Returns a snapshot of all invocation records.
     */
    public fun invocations(): List<Invocation> = _invocations.toList()

    /**
     * Resets the fixture.
     */
    public fun reset() {
        scripts.clear()
        _invocations.clear()
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun calculate(request: CalculateRequest): Outcome<CalculateResult, ReleaseFailure> {
        _invocations.add(Invocation("calculate", request))
        check(scripts.isNotEmpty()) {
            "FakeReleaseManager: no scripted response was enqueued for calculate: $request"
        }
        val next = scripts.removeAt(0)
        return when (next) {
            is CalculateResult -> Outcome.Success(next)
            is ReleaseFailure -> Outcome.Failure(next)
            else -> error("Unexpected type in calculate queue: ${next::class}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun promote(request: PromoteRequest): Outcome<PromoteResult, ReleaseFailure> {
        _invocations.add(Invocation("promote", request))
        check(scripts.isNotEmpty()) {
            "FakeReleaseManager: no scripted response was enqueued for promote: $request"
        }
        val next = scripts.removeAt(0)
        return when (next) {
            is PromoteResult -> Outcome.Success(next)
            is ReleaseFailure -> Outcome.Failure(next)
            else -> error("Unexpected type in promote queue: ${next::class}")
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
