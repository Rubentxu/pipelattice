package dev.rubentxu.pipelattice.release.artifact

import dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor
import dev.rubentxu.pipelattice.foundation.capability.CapabilityId
import dev.rubentxu.pipelattice.foundation.outcome.Outcome

/**
 * A fake [ArtifactRepository] that scripts deterministic responses for unit testing.
 *
 * This fixture operates as a FIFO queue of scripted responses.
 * Each call to [publish], [resolve], or [download] records the request in [invocations]
 * and returns the next response from the queue. If the queue is exhausted,
 * the call throws [IllegalStateException].
 */
public class FakeArtifactRepository : ArtifactRepository {

    private val scripts: MutableList<Any> = mutableListOf()
    private val _invocations: MutableList<Invocation> = mutableListOf()

    /**
     * Enqueues a scripted publish success result.
     */
    public fun enqueuePublishSuccess(result: PublishResult) {
        scripts.add(result)
    }

    /**
     * Enqueues a scripted publish failure result.
     */
    public fun enqueuePublishFailure(failure: ArtifactFailure) {
        scripts.add(failure)
    }

    /**
     * Enqueues a scripted resolve success result.
     */
    public fun enqueueResolveSuccess(result: ResolveResult) {
        scripts.add(result)
    }

    /**
     * Enqueues a scripted resolve failure result.
     */
    public fun enqueueResolveFailure(failure: ArtifactFailure) {
        scripts.add(failure)
    }

    /**
     * Enqueues a scripted download success result.
     */
    public fun enqueueDownloadSuccess(result: DownloadResult) {
        scripts.add(result)
    }

    /**
     * Enqueues a scripted download failure result.
     */
    public fun enqueueDownloadFailure(failure: ArtifactFailure) {
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
    override suspend fun publish(request: PublishRequest): Outcome<PublishResult, ArtifactFailure> {
        _invocations.add(Invocation("publish", request))
        check(scripts.isNotEmpty()) {
            "FakeArtifactRepository: no scripted response was enqueued for publish: $request"
        }
        val next = scripts.removeAt(0)
        return when (next) {
            is PublishResult -> Outcome.Success(next)
            is ArtifactFailure -> Outcome.Failure(next)
            else -> error("Unexpected type in publish queue: ${next::class}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun resolve(request: ResolveRequest): Outcome<ResolveResult, ArtifactFailure> {
        _invocations.add(Invocation("resolve", request))
        check(scripts.isNotEmpty()) {
            "FakeArtifactRepository: no scripted response was enqueued for resolve: $request"
        }
        val next = scripts.removeAt(0)
        return when (next) {
            is ResolveResult -> Outcome.Success(next)
            is ArtifactFailure -> Outcome.Failure(next)
            else -> error("Unexpected type in resolve queue: ${next::class}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun download(request: DownloadRequest): Outcome<DownloadResult, ArtifactFailure> {
        _invocations.add(Invocation("download", request))
        check(scripts.isNotEmpty()) {
            "FakeArtifactRepository: no scripted response was enqueued for download: $request"
        }
        val next = scripts.removeAt(0)
        return when (next) {
            is DownloadResult -> Outcome.Success(next)
            is ArtifactFailure -> Outcome.Failure(next)
            else -> error("Unexpected type in download queue: ${next::class}")
        }
    }

    override fun descriptor(id: CapabilityId): CapabilityDescriptor? = when (id) {
        ArtifactRepository.ARTIFACT_PUBLISH_V1 -> ArtifactCapabilities.ARTIFACT_PUBLISH_V1
        ArtifactRepository.ARTIFACT_RESOLVE_V1 -> ArtifactCapabilities.ARTIFACT_RESOLVE_V1
        ArtifactRepository.ARTIFACT_DOWNLOAD_V1 -> ArtifactCapabilities.ARTIFACT_DOWNLOAD_V1
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
