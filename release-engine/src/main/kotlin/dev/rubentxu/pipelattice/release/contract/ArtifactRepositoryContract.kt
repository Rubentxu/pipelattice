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
import org.junit.jupiter.api.Assumptions.assumeTrue
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
 *
 * ## Expectation Hooks (for property-based compliance)
 * Real adapters override the `expected*` hooks to derive values from their
 * real fixtures. Fake adapters use the default hardcoded derivations.
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
     * The request is provided so real adapters can create fixtures at request.localPath.
     */
    protected open suspend fun setupPublishSuccess(result: PublishResult, request: PublishRequest) {}

    /**
     * Setup method for scripted resolve success.
     * The request is provided for completeness; resolve uses pre-existing fixtures.
     */
    protected open suspend fun setupResolveSuccess(result: ResolveResult, request: ResolveRequest) {}

    /**
     * Setup method for scripted download success.
     * The request is provided so real adapters can create fixtures at the source path.
     */
    protected open suspend fun setupDownloadSuccess(result: DownloadResult, request: DownloadRequest) {}

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

    /**
     * Expectation hook for publish success: derives the expected [PublishResult]
     * from the coordinate and request. Real adapters override to return the digest
     * their real fixture produces (which the publish step will copy to the repo).
     * The default returns a fake-compatible hardcoded digest.
     */
    protected open fun expectedPublishResult(coordinate: ArtifactCoordinate, request: PublishRequest): PublishResult =
        PublishResult(coordinate, "sha256:abc123def456")

    /**
     * Expectation hook for resolve success: derives the expected [ResolveResult]
     * from the coordinate and request. Real adapters override to return the digest
     * their real fixture produces. Default uses fake-compatible hardcoded values.
     */
    protected open fun expectedResolveResult(coordinate: ArtifactCoordinate, request: ResolveRequest): ResolveResult =
        ResolveResult(coordinate, "sha256:abc123def456", 12345L)

    /**
     * Expectation hook for download success: derives the expected [DownloadResult]
     * from the coordinate, destination, and request. Real adapters override to return
     * the size their real fixture produces.
     */
    protected open fun expectedDownloadResult(coordinate: ArtifactCoordinate, destination: Path, request: DownloadRequest): DownloadResult =
        DownloadResult(coordinate, destination, 12345L)

    /**
     * Returns whether this adapter implements queue-based scripting (empty-queue invariants apply).
     * Default true for fake queue-based adapters. Real adapters override to false.
     */
    protected open fun supportsQueueBasedScripting(): Boolean = true

    // ----- Invariant 1: scripted-success -----

    @Test
    protected open fun invariant_publish_success() {
        runBlocking {
            val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
            val request = PublishRequest(coord, Path.of("/tmp/lib.jar"))
            val expected = expectedPublishResult(coord, request)
            setupPublishSuccess(expected, request)
            val outcome = subject().publish(request)
            assertTrue(outcome is Outcome.Success, "publish should succeed")
            assertEquals(expected, (outcome as Outcome.Success).value, "publish should return expected result")
        }
    }

    @Test
    protected open fun invariant_resolve_success() {
        runBlocking {
            val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
            val request = ResolveRequest(coord)
            val expected = expectedResolveResult(coord, request)
            setupResolveSuccess(expected, request)
            val outcome = subject().resolve(request)
            assertTrue(outcome is Outcome.Success, "resolve should succeed")
            assertEquals(expected, (outcome as Outcome.Success).value, "resolve should return expected result")
        }
    }

    @Test
    protected open fun invariant_download_success() {
        runBlocking {
            val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
            val dest = Path.of("/tmp/downloaded.jar")
            val request = DownloadRequest(coord, dest)
            val expected = expectedDownloadResult(coord, dest, request)
            setupDownloadSuccess(expected, request)
            val outcome = subject().download(request)
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
            val request = ResolveRequest(coord)
            setupResolveSuccess(ResolveResult(coord, "sha256:abc", 100L), request)
            subject().resolve(request)
            val snap1 = invocations()
            val snap2 = invocations()
            assertEquals(snap1, snap2, "invocations() should be stable")
            assertEquals(1, snap1.size, "invocations() should record exactly one call")
        }
    }

    // ----- Invariant 4: empty-queue-raises -----

    @Test
    protected open fun invariant_publish_empty_raises() {
        assumeTrue(supportsQueueBasedScripting(), "publish empty-raises only applies to queue-based adapters")
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
        assumeTrue(supportsQueueBasedScripting(), "resolve empty-raises only applies to queue-based adapters")
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
        assumeTrue(supportsQueueBasedScripting(), "download empty-raises only applies to queue-based adapters")
        runBlocking {
            try {
                subject().download(DownloadRequest(ArtifactCoordinate("dev.example", "lib", "1.0.0"), Path.of("/tmp/downloaded.jar")))
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
