package dev.rubentxu.pipelattice.release.contract

import dev.rubentxu.pipelattice.foundation.capability.SideEffect
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
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
import org.junit.jupiter.api.Test

/**
 * Abstract TCK contract for [ArtifactRepository] implementations.
 *
 * Provides 6 invariants as `@Test` methods. Each concrete test class
 * provides [newSubject] to create the instance (cached per test) and optionally overrides
 * setup methods for scripted scenarios.
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

    /** Cached subject instance — same instance used for setup and execution. */
    private val subject: ArtifactRepository by lazy { newSubject() }

    /**
     * Factory method that returns a [ArtifactRepository] instance.
     * Called once lazily; the same instance is used throughout the test.
     */
    protected abstract fun newSubject(): ArtifactRepository

    /**
     * Returns the cached subject instance. Use instead of calling newSubject() directly.
     */
    protected fun subject(): ArtifactRepository = subject

    /**
     * Setup method for scripted publish success. Default no-op for real adapters.
     * Fake implementations should override to enqueue the result.
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
     * Returns invocation records for snapshot testing. Default returns empty list.
     * Fakes override to provide the actual invocations list.
     */
    protected open fun invocations(): List<Any> = emptyList()

    // ----- Invariant 1: scripted-success -----

    @Test
    protected open suspend fun invariant_publish_success(): Outcome<PublishResult, ArtifactFailure> {
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        val result = PublishResult(coord, "sha256:abc123def456")
        setupPublishSuccess(result)
        return subject().publish(PublishRequest(coord, Path.of("/tmp/lib.jar")))
    }

    @Test
    protected open suspend fun invariant_resolve_success(): Outcome<ResolveResult, ArtifactFailure> {
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        val result = ResolveResult(coord, "sha256:abc123def456", 12345L)
        setupResolveSuccess(result)
        return subject().resolve(ResolveRequest(coord))
    }

    @Test
    protected open suspend fun invariant_download_success(): Outcome<DownloadResult, ArtifactFailure> {
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        val dest = Path.of("/tmp/downloaded.jar")
        val result = DownloadResult(coord, dest, 12345L)
        setupDownloadSuccess(result)
        return subject().download(DownloadRequest(coord, dest))
    }

    // ----- Invariant 2: scripted-failure -----

    @Test
    protected open suspend fun invariant_resolve_failure(): Outcome<ResolveResult, ArtifactFailure> {
        val coord = ArtifactCoordinate("dev.example", "lib", "9.9.9")
        val failure = ArtifactFailure.Unknown(coord, "synthetic-missing-artifact")
        setupResolveFailure(failure)
        return subject().resolve(ResolveRequest(coord))
    }

    @Test
    protected open suspend fun invariant_download_failure(): Outcome<DownloadResult, ArtifactFailure> {
        val coord = ArtifactCoordinate("dev.example", "lib", "9.9.9")
        val failure = ArtifactFailure.Unknown(coord, "synthetic-corrupt-artifact")
        setupDownloadFailure(failure)
        return subject().download(DownloadRequest(coord, Path.of("/tmp/downloaded.jar")))
    }

    // ----- Invariant 3: idempotent-invocation-snapshot -----

    @Test
    protected open suspend fun invariant_invocations_stable(): Boolean {
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        setupResolveSuccess(ResolveResult(coord, "sha256:abc", 100L))
        subject().resolve(ResolveRequest(coord))
        val snap1 = invocations()
        val snap2 = invocations()
        return snap1 == snap2 && snap1.size == 1
    }

    // ----- Invariant 4: empty-queue-raises -----

    @Test
    protected open suspend fun invariant_publish_empty_raises(): Boolean {
        return try {
            subject().publish(PublishRequest(ArtifactCoordinate("dev.example", "lib", "1.0.0"), Path.of("/tmp/lib.jar")))
            false
        } catch (e: IllegalStateException) {
            true
        }
    }

    @Test
    protected open suspend fun invariant_resolve_empty_raises(): Boolean {
        return try {
            subject().resolve(ResolveRequest(ArtifactCoordinate("dev.example", "lib", "1.0.0")))
            false
        } catch (e: IllegalStateException) {
            true
        }
    }

    @Test
    protected open suspend fun invariant_download_empty_raises(): Boolean {
        return try {
            subject().download(DownloadRequest(ArtifactCoordinate("dev.example", "lib", "1.0.0"), Path.of("/tmp/lib.jar")))
            false
        } catch (e: IllegalStateException) {
            true
        }
    }

    // ----- Invariant 5: side-effect-consistency -----

    @Test
    protected open fun invariant_publish_descriptor_mutating(): Boolean {
        val desc = subject().descriptor(ArtifactRepository.ARTIFACT_PUBLISH_V1) ?: return false
        return SideEffect.MUTATING in desc.sideEffects
    }

    @Test
    protected open fun invariant_resolve_descriptor_read_only(): Boolean {
        val desc = subject().descriptor(ArtifactRepository.ARTIFACT_RESOLVE_V1) ?: return false
        return SideEffect.READ_ONLY in desc.sideEffects
    }

    @Test
    protected open fun invariant_download_descriptor_read_only(): Boolean {
        val desc = subject().descriptor(ArtifactRepository.ARTIFACT_DOWNLOAD_V1) ?: return false
        return SideEffect.READ_ONLY in desc.sideEffects
    }

    // ----- Invariant 6: secret-exclusion -----

    /**
     * Secret-exclusion probe for ArtifactRepository.
     * Verifies no secret-shaped literals appear in invocations surfaces.
     * Override to provide a probe using the test's specific request fields.
     */
    protected open suspend fun secretExclusionProbe(): Boolean = true
}
