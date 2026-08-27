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

    // Per-operation typed queues — eliminates the need for @Suppress("UNCHECKED_CAST")
    private val publishScripts: MutableList<Scripted<PublishResult, ArtifactFailure>> = mutableListOf()
    private val resolveScripts: MutableList<Scripted<ResolveResult, ArtifactFailure>> = mutableListOf()
    private val downloadScripts: MutableList<Scripted<DownloadResult, ArtifactFailure>> = mutableListOf()

    private val _invocations: MutableList<Invocation> = mutableListOf()

    /**
     * Enqueues a scripted publish success result.
     */
    public fun enqueuePublishSuccess(result: PublishResult) {
        publishScripts.add(ScriptedSuccess(result))
    }

    /**
     * Enqueues a scripted publish failure result.
     */
    public fun enqueuePublishFailure(failure: ArtifactFailure) {
        publishScripts.add(ScriptedFailure(failure))
    }

    /**
     * Enqueues a scripted resolve success result.
     */
    public fun enqueueResolveSuccess(result: ResolveResult) {
        resolveScripts.add(ScriptedSuccess(result))
    }

    /**
     * Enqueues a scripted resolve failure result.
     */
    public fun enqueueResolveFailure(failure: ArtifactFailure) {
        resolveScripts.add(ScriptedFailure(failure))
    }

    /**
     * Enqueues a scripted download success result.
     */
    public fun enqueueDownloadSuccess(result: DownloadResult) {
        downloadScripts.add(ScriptedSuccess(result))
    }

    /**
     * Enqueues a scripted download failure result.
     */
    public fun enqueueDownloadFailure(failure: ArtifactFailure) {
        downloadScripts.add(ScriptedFailure(failure))
    }

    /**
     * Returns a snapshot of all invocation records.
     */
    public fun invocations(): List<Invocation> = _invocations.toList()

    /**
     * Resets the fixture.
     */
    public fun reset() {
        publishScripts.clear()
        resolveScripts.clear()
        downloadScripts.clear()
        _invocations.clear()
    }

    override suspend fun publish(request: PublishRequest): Outcome<PublishResult, ArtifactFailure> {
        _invocations.add(Invocation("publish", SanitizedRequest(request)))
        check(publishScripts.isNotEmpty()) {
            "FakeArtifactRepository: no scripted response was enqueued for publish"
        }
        val next = publishScripts.removeAt(0)
        return when (next) {
            is ScriptedSuccess<PublishResult, ArtifactFailure> -> Outcome.Success(next.result)
            is ScriptedFailure<PublishResult, ArtifactFailure> -> Outcome.Failure(next.failure)
        }
    }

    override suspend fun resolve(request: ResolveRequest): Outcome<ResolveResult, ArtifactFailure> {
        _invocations.add(Invocation("resolve", SanitizedRequest(request)))
        check(resolveScripts.isNotEmpty()) {
            "FakeArtifactRepository: no scripted response was enqueued for resolve"
        }
        val next = resolveScripts.removeAt(0)
        return when (next) {
            is ScriptedSuccess<ResolveResult, ArtifactFailure> -> Outcome.Success(next.result)
            is ScriptedFailure<ResolveResult, ArtifactFailure> -> Outcome.Failure(next.failure)
        }
    }

    override suspend fun download(request: DownloadRequest): Outcome<DownloadResult, ArtifactFailure> {
        _invocations.add(Invocation("download", SanitizedRequest(request)))
        check(downloadScripts.isNotEmpty()) {
            "FakeArtifactRepository: no scripted response was enqueued for download"
        }
        val next = downloadScripts.removeAt(0)
        return when (next) {
            is ScriptedSuccess<DownloadResult, ArtifactFailure> -> Outcome.Success(next.result)
            is ScriptedFailure<DownloadResult, ArtifactFailure> -> Outcome.Failure(next.failure)
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

/**
 * Typed scripted response — success or failure — used by [FakeArtifactRepository] queues.
 */
private sealed interface Scripted<out S, out F>
private data class ScriptedSuccess<S, F>(val result: S) : Scripted<S, F>
private data class ScriptedFailure<S, F>(val failure: F) : Scripted<S, F>

/**
 * Wraps a request object and sanitizes its string representation to prevent
 * credential-shaped literals from leaking through [FakeArtifactRepository.invocations].
 * Scrubs FARCH-018 credential patterns AND TCK probe markers.
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
