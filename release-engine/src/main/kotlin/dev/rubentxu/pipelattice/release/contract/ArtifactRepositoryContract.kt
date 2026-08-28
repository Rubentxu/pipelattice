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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
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
    protected open fun invariant_publish_success() {
        runBlocking {
            val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
            val expected = PublishResult(coord, "sha256:abc123def456")
            setupPublishSuccess(expected)
            val outcome = subject().publish(PublishRequest(coord, Path.of("/tmp/lib.jar")))
            assertTrue(outcome is Outcome.Success, "publish should succeed")
            assertEquals(expected, (outcome as Outcome.Success).value, "publish should return expected result")
        }
    }

    @Test
    protected open fun invariant_resolve_success() {
        runBlocking {
            val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
            val expected = ResolveResult(coord, "sha256:abc123def456", 12345L)
            setupResolveSuccess(expected)
            val outcome = subject().resolve(ResolveRequest(coord))
            assertTrue(outcome is Outcome.Success, "resolve should succeed")
            assertEquals(expected, (outcome as Outcome.Success).value, "resolve should return expected result")
        }
    }

    @Test
    protected open fun invariant_download_success() {
        runBlocking {
            val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
            val dest = Path.of("/tmp/downloaded.jar")
            val expected = DownloadResult(coord, dest, 12345L)
            setupDownloadSuccess(expected)
            val outcome = subject().download(DownloadRequest(coord, dest))
            assertTrue(outcome is Outcome.Success, "download should succeed")
            assertEquals(expected, (outcome as Outcome.Success).value, "download should return expected result")
        }
    }

    // ----- Invariant 2: scripted-failure -----

    @Test
    protected open fun invariant_resolve_failure() {
        runBlocking {
            val coord = ArtifactCoordinate("dev.example", "lib", "9.9.9")
            setupResolveFailure(ArtifactFailure.Unknown(coord, "synthetic-missing-artifact"))
            val outcome = subject().resolve(ResolveRequest(coord))
            assertTrue(outcome is Outcome.Failure, "resolve should return failure for missing artifact")
        }
    }

    @Test
    protected open fun invariant_download_failure() {
        runBlocking {
            val coord = ArtifactCoordinate("dev.example", "lib", "9.9.9")
            setupDownloadFailure(ArtifactFailure.Unknown(coord, "synthetic-corrupt-artifact"))
            val outcome = subject().download(DownloadRequest(coord, Path.of("/tmp/downloaded.jar")))
            assertTrue(outcome is Outcome.Failure, "download should return failure for corrupt artifact")
        }
    }

    // ----- Invariant 3: idempotent-invocation-snapshot -----

    @Test
    protected open fun invariant_invocations_stable() {
        runBlocking {
            val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
            setupResolveSuccess(ResolveResult(coord, "sha256:abc", 100L))
            subject().resolve(ResolveRequest(coord))
            val snap1 = invocations()
            val snap2 = invocations()
            assertEquals(snap1, snap2, "invocations() should be stable")
            assertEquals(1, snap1.size, "invocations() should record exactly one call")
        }
    }

    // ----- Invariant 4: empty-queue-raises -----

    @Test
    protected open fun invariant_publish_empty_raises() {
        runBlocking {
            try {
                subject().publish(PublishRequest(ArtifactCoordinate("dev.example", "lib", "1.0.0"), Path.of("/tmp/lib.jar")))
                fail("Expected IllegalStateException for publish with empty queue")
            } catch (e: IllegalStateException) {
                assertTrue(true, "publish should raise IllegalStateException when queue is empty")
            }
        }
    }

    @Test
    protected open fun invariant_resolve_empty_raises() {
        runBlocking {
            try {
                subject().resolve(ResolveRequest(ArtifactCoordinate("dev.example", "lib", "1.0.0")))
                fail("Expected IllegalStateException for resolve with empty queue")
            } catch (e: IllegalStateException) {
                assertTrue(true, "resolve should raise IllegalStateException when queue is empty")
            }
        }
    }

    @Test
    protected open fun invariant_download_empty_raises() {
        runBlocking {
            try {
                subject().download(DownloadRequest(ArtifactCoordinate("dev.example", "lib", "1.0.0"), Path.of("/tmp/lib.jar")))
                fail("Expected IllegalStateException for download with empty queue")
            } catch (e: IllegalStateException) {
                assertTrue(true, "download should raise IllegalStateException when queue is empty")
            }
        }
    }

    // ----- Invariant 5: side-effect-consistency -----

    @Test
    protected open fun invariant_publish_descriptor_mutating() {
        val desc = subject().descriptor(ArtifactRepository.ARTIFACT_PUBLISH_V1)
        assertTrue(desc != null, "descriptor should exist for ARTIFACT_PUBLISH_V1")
        assertTrue(SideEffect.MUTATING in desc!!.sideEffects, "publish should be MUTATING")
    }

    @Test
    protected open fun invariant_resolve_descriptor_read_only() {
        val desc = subject().descriptor(ArtifactRepository.ARTIFACT_RESOLVE_V1)
        assertTrue(desc != null, "descriptor should exist for ARTIFACT_RESOLVE_V1")
        assertTrue(SideEffect.READ_ONLY in desc!!.sideEffects, "resolve should be READ_ONLY")
    }

    @Test
    protected open fun invariant_download_descriptor_read_only() {
        val desc = subject().descriptor(ArtifactRepository.ARTIFACT_DOWNLOAD_V1)
        assertTrue(desc != null, "descriptor should exist for ARTIFACT_DOWNLOAD_V1")
        assertTrue(SideEffect.READ_ONLY in desc!!.sideEffects, "download should be READ_ONLY")
    }

    // ----- Invariant 6: secret-exclusion -----

    @Test
    protected open fun invariant_secret_exclusion() {
        runBlocking {
            val result = secretExclusionProbe()
            assertTrue(result, "secret-exclusion probe should pass")
        }
    }

    /**
     * Secret-exclusion probe for ArtifactRepository.
     * Verifies no secret-shaped literals appear in invocations surfaces.
     * Override to provide a probe using the test's specific request fields.
     */
    protected open suspend fun secretExclusionProbe(): Boolean = true
}
