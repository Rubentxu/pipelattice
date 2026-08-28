package dev.rubentxu.pipelattice.release.adapter.release

import dev.rubentxu.pipelattice.foundation.capability.SideEffect
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.foundation.secret.SecretFailure
import dev.rubentxu.pipelattice.foundation.secret.SecretRef
import dev.rubentxu.pipelattice.foundation.secret.SecretResolver
import dev.rubentxu.pipelattice.foundation.secret.SecretValue
import dev.rubentxu.pipelattice.release.release.ReleaseManager
import dev.rubentxu.pipelattice.release.release.SemanticVersion
import dev.rubentxu.pipelattice.release.scm.ScmFailure
import dev.rubentxu.pipelattice.release.scm.ScmSource
import dev.rubentxu.pipelattice.release.scm.TagRequest
import dev.rubentxu.pipelattice.release.scm.TagResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TCK contract tests for [GitTagBasedReleaseManager] as a real ReleaseManager adapter.
 *
 * Tests the invariants that apply to real adapters:
 * - Invariant 5: descriptor side-effects match expected values
 * - Invariant 1: calculate/promote returns real outcomes
 *
 * Invariants 3 and 4 (queue-based: empty-queue-raises, invocations-stability)
 * are NOT applicable to real adapters — they don't have scripted queues.
 */
class GitTagBasedReleaseManagerTCKTest {

    /**
     * A fake ScmSource that always succeeds with a predetermined result.
     */
    private class FakeScmSourceAlwaysSucceeds : ScmSource {
        override suspend fun checkout(request: dev.rubentxu.pipelattice.release.scm.CheckoutRequest):
                Outcome<dev.rubentxu.pipelattice.release.scm.CheckoutResult, ScmFailure> =
            Outcome.Success(dev.rubentxu.pipelattice.release.scm.CheckoutResult(java.nio.file.Path.of("/tmp"), "abc123"))

        override suspend fun tag(request: TagRequest): Outcome<TagResult, ScmFailure> =
            Outcome.Success(TagResult("v1.0.0", "abc123"))

        override suspend fun push(request: dev.rubentxu.pipelattice.release.scm.PushRequest):
                Outcome<dev.rubentxu.pipelattice.release.scm.PushResult, ScmFailure> =
            Outcome.Success(dev.rubentxu.pipelattice.release.scm.PushResult(listOf("refs/heads/main"), "refs/heads/main"))

        override fun descriptor(id: dev.rubentxu.pipelattice.foundation.capability.CapabilityId):
                dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor? = null
    }

    private class FakeSecretResolver : SecretResolver {
        override suspend fun resolve(ref: SecretRef): Outcome<SecretValue, SecretFailure> =
            Outcome.Failure(SecretFailure.Unknown(ref.authority, ref.key))
    }

    private fun newManager(): GitTagBasedReleaseManager =
        GitTagBasedReleaseManager(FakeScmSourceAlwaysSucceeds(), FakeSecretResolver())

    // ----- Invariant 5: side-effect consistency -----

    @Test
    fun `descriptor calculate is READ_ONLY`() {
        val manager = newManager()
        val desc = manager.descriptor(ReleaseManager.RELEASE_CALCULATE_V1)
        assertNotNull(desc, "RELEASE_CALCULATE_V1 must be advertised")
        assertTrue(SideEffect.READ_ONLY in desc.sideEffects, "calculate must be READ_ONLY")
    }

    @Test
    fun `descriptor promote is MUTATING`() {
        val manager = newManager()
        val desc = manager.descriptor(ReleaseManager.RELEASE_PROMOTE_V1)
        assertNotNull(desc, "RELEASE_PROMOTE_V1 must be advertised")
        assertTrue(SideEffect.MUTATING in desc.sideEffects, "promote must be MUTATING")
    }

    // ----- Invariant 1: real success operations -----

    @Test
    fun `calculate bump patch returns correct next version`() = runBlocking {
        val manager = newManager()

        val outcome = manager.calculate(
            dev.rubentxu.pipelattice.release.release.CalculateRequest(
                sourceRevision = "main",
                previousTag = "v1.2.3",
                bumpPolicy = dev.rubentxu.pipelattice.release.release.BumpPolicy.PATCH,
            )
        )

        assertIs<Outcome.Success<*>>(outcome)
        val result = outcome.value as dev.rubentxu.pipelattice.release.release.CalculateResult
        assertTrue(result.version == SemanticVersion(1, 2, 4), "version must be 1.2.4")
        assertTrue(result.sourceRevision == "main", "source revision must match")
    }

    @Test
    fun `calculate bump minor returns correct next version`() = runBlocking {
        val manager = newManager()

        val outcome = manager.calculate(
            dev.rubentxu.pipelattice.release.release.CalculateRequest(
                sourceRevision = "main",
                previousTag = "v2.3.4",
                bumpPolicy = dev.rubentxu.pipelattice.release.release.BumpPolicy.MINOR,
            )
        )

        assertIs<Outcome.Success<*>>(outcome)
        val result = outcome.value as dev.rubentxu.pipelattice.release.release.CalculateResult
        assertTrue(result.version == SemanticVersion(2, 4, 0), "version must be 2.4.0")
    }

    @Test
    fun `calculate bump major returns correct next version`() = runBlocking {
        val manager = newManager()

        val outcome = manager.calculate(
            dev.rubentxu.pipelattice.release.release.CalculateRequest(
                sourceRevision = "main",
                previousTag = "v3.0.0",
                bumpPolicy = dev.rubentxu.pipelattice.release.release.BumpPolicy.MAJOR,
            )
        )

        assertIs<Outcome.Success<*>>(outcome)
        val result = outcome.value as dev.rubentxu.pipelattice.release.release.CalculateResult
        assertTrue(result.version == SemanticVersion(4, 0, 0), "version must be 4.0.0")
    }

    @Test
    fun `calculate null previousTag bumps from zero`() = runBlocking {
        val manager = newManager()

        val outcome = manager.calculate(
            dev.rubentxu.pipelattice.release.release.CalculateRequest(
                sourceRevision = "main",
                previousTag = null,
                bumpPolicy = dev.rubentxu.pipelattice.release.release.BumpPolicy.MAJOR,
            )
        )

        assertIs<Outcome.Success<*>>(outcome)
        val result = outcome.value as dev.rubentxu.pipelattice.release.release.CalculateResult
        assertTrue(result.version == SemanticVersion(1, 0, 0), "version must be 1.0.0 from zero")
    }

    @Test
    fun `promote success returns PromoteResult with correct version`() = runBlocking {
        val manager = newManager()

        val outcome = manager.promote(
            dev.rubentxu.pipelattice.release.release.PromoteRequest(
                targetEnvironment = dev.rubentxu.pipelattice.release.release.EnvironmentRef("production"),
                version = SemanticVersion(1, 0, 0),
            )
        )

        assertIs<Outcome.Success<*>>(outcome)
        val result = outcome.value as dev.rubentxu.pipelattice.release.release.PromoteResult
        assertTrue(result.version == SemanticVersion(1, 0, 0), "version must match")
        assertTrue(result.targetEnvironment.value == "production", "environment must match")
    }

    // ----- Invariant 2: promote rejection -----

    @Test
    fun `promote scm failure propagates as PromotionRejected`() = runBlocking {
        val rejectingScm = object : ScmSource {
            override suspend fun checkout(request: dev.rubentxu.pipelattice.release.scm.CheckoutRequest):
                    Outcome<dev.rubentxu.pipelattice.release.scm.CheckoutResult, ScmFailure> =
                Outcome.Failure(ScmFailure.Unknown("checkout", "synthetic-missing"))

            override suspend fun tag(request: TagRequest): Outcome<TagResult, ScmFailure> =
                Outcome.Failure(ScmFailure.Unknown("tag", "synthetic-missing"))

            override suspend fun push(request: dev.rubentxu.pipelattice.release.scm.PushRequest):
                    Outcome<dev.rubentxu.pipelattice.release.scm.PushResult, ScmFailure> =
                Outcome.Failure(ScmFailure.Unknown("push", "synthetic-missing"))

            override fun descriptor(id: dev.rubentxu.pipelattice.foundation.capability.CapabilityId):
                    dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor? = null
        }

        val manager = GitTagBasedReleaseManager(rejectingScm, FakeSecretResolver())

        val outcome = manager.promote(
            dev.rubentxu.pipelattice.release.release.PromoteRequest(
                targetEnvironment = dev.rubentxu.pipelattice.release.release.EnvironmentRef("staging"),
                version = SemanticVersion(2, 0, 0),
            )
        )

        assertIs<Outcome.Failure<*>>(outcome)
        val failure = outcome.reason as dev.rubentxu.pipelattice.release.release.ReleaseFailure
        assertIs<dev.rubentxu.pipelattice.release.release.ReleaseFailure.PromotionRejected>(failure)
        assertTrue(failure.version == SemanticVersion(2, 0, 0))
        assertTrue(failure.reason == "synthetic-tag-failed")
    }
}
