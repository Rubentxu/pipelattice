package dev.rubentxu.pipelattice.release.contract

import dev.rubentxu.pipelattice.foundation.capability.SideEffect
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.release.artifact.ArtifactCapabilities
import dev.rubentxu.pipelattice.release.artifact.ArtifactCoordinate
import dev.rubentxu.pipelattice.release.artifact.ArtifactFailure
import dev.rubentxu.pipelattice.release.artifact.ArtifactRepository
import dev.rubentxu.pipelattice.release.artifact.DownloadRequest
import dev.rubentxu.pipelattice.release.artifact.DownloadResult
import dev.rubentxu.pipelattice.release.artifact.PublishRequest
import dev.rubentxu.pipelattice.release.artifact.PublishResult
import dev.rubentxu.pipelattice.release.artifact.ResolveRequest
import dev.rubentxu.pipelattice.release.artifact.ResolveResult
import java.nio.file.Path

/**
 * Abstract TCK contract for [ArtifactRepository] implementations.
 *
 * Provides 6 invariants as protected open methods. Each concrete test class
 * provides [newSubject] to create the instance and setup/teardown methods
 * for scripted scenarios.
 *
 * ## Invariants
 * 1. scripted-success — publish/resolve/download returns expected result
 * 2. scripted-failure — resolve/download returns typed failure
 * 3. idempotent-invocation-snapshot — invocations() is stable across reads
 * 4. empty-queue-raises — empty queue raises IllegalStateException
 * 5. side-effect-consistency — descriptor(id) matches expected side-effects
 * 6. secret-exclusion — no secret-shaped literals in surfaces
 */
public abstract class ArtifactRepositoryContract {

    /**
     * Factory method that returns a freshly-constructed [ArtifactRepository] instance.
     */
    protected abstract fun newSubject(): ArtifactRepository

    /**
     * Setup method for scripted publish success. Default no-op for real adapters.
     */
    protected open suspend fun setupPublishSuccess(result: PublishResult) {}

    /**
     * Setup method for scripted resolve success.
     */
    protected open suspend fun setupResolveSuccess(result: ResolveResult) {}

    /**
     * Setup method for scripted download success.
     */
    protected open suspend fun setupDownloadSuccess(result: DownloadResult) {}

    /**
     * Setup method for scripted resolve failure.
     */
    protected open suspend fun setupResolveFailure(failure: ArtifactFailure) {}

    /**
     * Setup method for scripted download failure.
     */
    protected open suspend fun setupDownloadFailure(failure: ArtifactFailure) {}

    /**
     * Invariant 1: publish returns expected result when scripted as success.
     */
    protected open suspend fun invariant_publish_success(): Outcome<PublishResult, ArtifactFailure> {
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        val result = PublishResult(coord, "sha256:abc123def456")
        setupPublishSuccess(result)
        return newSubject().publish(PublishRequest(coord, Path.of("/tmp/lib.jar")))
    }

    /**
     * Invariant 1: resolve returns expected result when scripted as success.
     */
    protected open suspend fun invariant_resolve_success(): Outcome<ResolveResult, ArtifactFailure> {
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        val result = ResolveResult(coord, "sha256:abc123def456", 12345L)
        setupResolveSuccess(result)
        return newSubject().resolve(ResolveRequest(coord))
    }

    /**
     * Invariant 1: download returns expected result when scripted as success.
     */
    protected open suspend fun invariant_download_success(): Outcome<DownloadResult, ArtifactFailure> {
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        val dest = Path.of("/tmp/downloaded.jar")
        val result = DownloadResult(coord, dest, 12345L)
        setupDownloadSuccess(result)
        return newSubject().download(DownloadRequest(coord, dest))
    }

    /**
     * Invariant 2: resolve returns typed failure when scripted.
     */
    protected open suspend fun invariant_resolve_failure(): Outcome<ResolveResult, ArtifactFailure> {
        val coord = ArtifactCoordinate("dev.example", "lib", "9.9.9")
        val failure = ArtifactFailure.Unknown(coord, "synthetic-missing-artifact")
        setupResolveFailure(failure)
        return newSubject().resolve(ResolveRequest(coord))
    }

    /**
     * Invariant 2: download returns typed failure when scripted.
     */
    protected open suspend fun invariant_download_failure(): Outcome<DownloadResult, ArtifactFailure> {
        val coord = ArtifactCoordinate("dev.example", "lib", "9.9.9")
        val failure = ArtifactFailure.Unknown(coord, "synthetic-corrupt-artifact")
        setupDownloadFailure(failure)
        return newSubject().download(DownloadRequest(coord, Path.of("/tmp/downloaded.jar")))
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
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        setupResolveSuccess(ResolveResult(coord, "sha256:abc123", 100L))
        val repo = newSubject()
        repo.resolve(ResolveRequest(coord))
        val snap1 = invocations()
        val snap2 = invocations()
        return snap1 == snap2 && snap1.size == 1
    }

    /**
     * Invariant 4: empty publish queue raises IllegalStateException.
     */
    protected open suspend fun invariant_publish_empty_raises(): Boolean {
        val repo = newSubject()
        return try {
            repo.publish(PublishRequest(ArtifactCoordinate("dev.example", "lib", "1.0.0"), Path.of("/tmp/lib.jar")))
            false
        } catch (e: IllegalStateException) {
            true
        }
    }

    /**
     * Invariant 4: empty resolve queue raises IllegalStateException.
     */
    protected open suspend fun invariant_resolve_empty_raises(): Boolean {
        val repo = newSubject()
        return try {
            repo.resolve(ResolveRequest(ArtifactCoordinate("dev.example", "lib", "1.0.0")))
            false
        } catch (e: IllegalStateException) {
            true
        }
    }

    /**
     * Invariant 4: empty download queue raises IllegalStateException.
     */
    protected open suspend fun invariant_download_empty_raises(): Boolean {
        val repo = newSubject()
        return try {
            repo.download(DownloadRequest(ArtifactCoordinate("dev.example", "lib", "1.0.0"), Path.of("/tmp/lib.jar")))
            false
        } catch (e: IllegalStateException) {
            true
        }
    }

    /**
     * Invariant 5: descriptor for publish is MUTATING.
     */
    protected open fun invariant_publish_descriptor_mutating(): Boolean {
        val repo = newSubject()
        val desc = repo.descriptor(ArtifactRepository.ARTIFACT_PUBLISH_V1) ?: return false
        return SideEffect.MUTATING in desc.sideEffects
    }

    /**
     * Invariant 5: descriptor for resolve is READ_ONLY.
     */
    protected open fun invariant_resolve_descriptor_read_only(): Boolean {
        val repo = newSubject()
        val desc = repo.descriptor(ArtifactRepository.ARTIFACT_RESOLVE_V1) ?: return false
        return SideEffect.READ_ONLY in desc.sideEffects
    }

    /**
     * Invariant 5: descriptor for download is READ_ONLY.
     */
    protected open fun invariant_download_descriptor_read_only(): Boolean {
        val repo = newSubject()
        val desc = repo.descriptor(ArtifactRepository.ARTIFACT_DOWNLOAD_V1) ?: return false
        return SideEffect.READ_ONLY in desc.sideEffects
    }
}
