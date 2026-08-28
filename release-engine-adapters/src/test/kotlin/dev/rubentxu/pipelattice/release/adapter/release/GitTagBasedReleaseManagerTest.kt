package dev.rubentxu.pipelattice.release.adapter.release

import dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor
import dev.rubentxu.pipelattice.foundation.capability.CapabilityId
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.foundation.secret.SecretFailure
import dev.rubentxu.pipelattice.foundation.secret.SecretRef
import dev.rubentxu.pipelattice.foundation.secret.SecretResolver
import dev.rubentxu.pipelattice.foundation.secret.SecretValue
import dev.rubentxu.pipelattice.release.release.BumpPolicy
import dev.rubentxu.pipelattice.release.release.CalculateRequest
import dev.rubentxu.pipelattice.release.release.CalculateResult
import dev.rubentxu.pipelattice.release.release.EnvironmentRef
import dev.rubentxu.pipelattice.release.release.PromoteRequest
import dev.rubentxu.pipelattice.release.release.PromoteResult
import dev.rubentxu.pipelattice.release.release.ReleaseFailure
import dev.rubentxu.pipelattice.release.release.SemanticVersion
import dev.rubentxu.pipelattice.release.scm.CheckoutRequest
import dev.rubentxu.pipelattice.release.scm.CheckoutResult
import dev.rubentxu.pipelattice.release.scm.PushRequest
import dev.rubentxu.pipelattice.release.scm.PushResult
import dev.rubentxu.pipelattice.release.scm.ScmFailure
import dev.rubentxu.pipelattice.release.scm.ScmSource
import dev.rubentxu.pipelattice.release.scm.TagRequest
import dev.rubentxu.pipelattice.release.scm.TagResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [GitTagBasedReleaseManager].
 */
class GitTagBasedReleaseManagerTest {

    /**
     * A scripted fake ScmSource for testing the ReleaseManager without real Git operations.
     */
    private class FakeScmSource(
        private val tagResult: Outcome<TagResult, ScmFailure>,
    ) : ScmSource {
        override suspend fun checkout(request: CheckoutRequest): Outcome<CheckoutResult, ScmFailure> =
            Outcome.Failure(ScmFailure.Unknown("checkout", "not-used-in-release-manager"))
        override suspend fun tag(request: TagRequest): Outcome<TagResult, ScmFailure> = tagResult
        override suspend fun push(request: PushRequest): Outcome<PushResult, ScmFailure> =
            Outcome.Failure(ScmFailure.Unknown("push", "not-used-in-release-manager"))
        override fun descriptor(id: CapabilityId): CapabilityDescriptor? = null
    }

    private class FakeSecretResolver : SecretResolver {
        override suspend fun resolve(ref: SecretRef): Outcome<SecretValue, SecretFailure> =
            Outcome.Failure(SecretFailure.Unknown(ref.authority, ref.key))
    }

    // ----- calculate -----

    @Test
    fun `calculate null previousTag bumps from zero`() = runBlocking {
        val manager = GitTagBasedReleaseManager(FakeScmSource(Outcome.Success(TagResult("v1.0.0", "sha"))), FakeSecretResolver())

        val outcome = manager.calculate(
            CalculateRequest(
                sourceRevision = "main",
                previousTag = null,
                bumpPolicy = BumpPolicy.MAJOR,
            )
        )

        assertIs<Outcome.Success<CalculateResult>>(outcome)
        assertEquals(SemanticVersion(1, 0, 0), outcome.value.version)
        assertEquals("main", outcome.value.sourceRevision)
    }

    @Test
    fun `calculate blank previousTag bumps from zero`() = runBlocking {
        val manager = GitTagBasedReleaseManager(FakeScmSource(Outcome.Success(TagResult("v1.0.0", "sha"))), FakeSecretResolver())

        val outcome = manager.calculate(
            CalculateRequest(
                sourceRevision = "main",
                previousTag = "  ",
                bumpPolicy = BumpPolicy.MINOR,
            )
        )

        assertIs<Outcome.Success<CalculateResult>>(outcome)
        assertEquals(SemanticVersion(0, 1, 0), outcome.value.version)
    }

    @Test
    fun `calculate major bump from v1_2_3`() = runBlocking {
        val manager = GitTagBasedReleaseManager(FakeScmSource(Outcome.Success(TagResult("v1.0.0", "sha"))), FakeSecretResolver())

        val outcome = manager.calculate(
            CalculateRequest(
                sourceRevision = "main",
                previousTag = "v1.2.3",
                bumpPolicy = BumpPolicy.MAJOR,
            )
        )

        assertIs<Outcome.Success<CalculateResult>>(outcome)
        assertEquals(SemanticVersion(2, 0, 0), outcome.value.version)
    }

    @Test
    fun `calculate minor bump from v1_2_3`() = runBlocking {
        val manager = GitTagBasedReleaseManager(FakeScmSource(Outcome.Success(TagResult("v1.0.0", "sha"))), FakeSecretResolver())

        val outcome = manager.calculate(
            CalculateRequest(
                sourceRevision = "main",
                previousTag = "v1.2.3",
                bumpPolicy = BumpPolicy.MINOR,
            )
        )

        assertIs<Outcome.Success<CalculateResult>>(outcome)
        assertEquals(SemanticVersion(1, 3, 0), outcome.value.version)
    }

    @Test
    fun `calculate patch bump from v1_2_3`() = runBlocking {
        val manager = GitTagBasedReleaseManager(FakeScmSource(Outcome.Success(TagResult("v1.0.0", "sha"))), FakeSecretResolver())

        val outcome = manager.calculate(
            CalculateRequest(
                sourceRevision = "main",
                previousTag = "v1.2.3",
                bumpPolicy = BumpPolicy.PATCH,
            )
        )

        assertIs<Outcome.Success<CalculateResult>>(outcome)
        assertEquals(SemanticVersion(1, 2, 4), outcome.value.version)
    }

    @Test
    fun `calculate strips leading v prefix`() = runBlocking {
        val manager = GitTagBasedReleaseManager(FakeScmSource(Outcome.Success(TagResult("v1.0.0", "sha"))), FakeSecretResolver())

        val outcome = manager.calculate(
            CalculateRequest(
                sourceRevision = "main",
                previousTag = "V2.3.4",  // uppercase V
                bumpPolicy = BumpPolicy.PATCH,
            )
        )

        assertIs<Outcome.Success<CalculateResult>>(outcome)
        assertEquals(SemanticVersion(2, 3, 5), outcome.value.version)
    }

    @Test
    fun `calculate invalid tag falls back to zero version`() = runBlocking {
        val manager = GitTagBasedReleaseManager(FakeScmSource(Outcome.Success(TagResult("v1.0.0", "sha"))), FakeSecretResolver())

        val outcome = manager.calculate(
            CalculateRequest(
                sourceRevision = "main",
                previousTag = "not-a-version",
                bumpPolicy = BumpPolicy.PATCH,
            )
        )

        // parsePreviousTag catches the parse failure and returns (0,0,0)
        // bumpVersion with PATCH then gives (0,0,1)
        assertIs<Outcome.Success<CalculateResult>>(outcome)
        assertEquals(SemanticVersion(0, 0, 1), outcome.value.version)
    }

    // ----- promote -----

    @Test
    fun `promote success propagates to result`() = runBlocking {
        val manager = GitTagBasedReleaseManager(
            FakeScmSource(Outcome.Success(TagResult("v1.0.0", "abc123"))),
            FakeSecretResolver()
        )

        val outcome = manager.promote(
            PromoteRequest(
                targetEnvironment = EnvironmentRef("production"),
                version = SemanticVersion(1, 0, 0),
            )
        )

        assertIs<Outcome.Success<PromoteResult>>(outcome)
        val result = outcome.value
        assertEquals(SemanticVersion(1, 0, 0), result.version)
        assertEquals(EnvironmentRef("production"), result.targetEnvironment)
        assertTrue(result.promotedAt.isNotBlank())
    }

    @Test
    fun `promote scm failure propagates as PromotionRejected`() = runBlocking {
        val manager = GitTagBasedReleaseManager(
            FakeScmSource(Outcome.Failure(ScmFailure.Unknown("tag", "synthetic-missing-ref"))),
            FakeSecretResolver()
        )

        val outcome = manager.promote(
            PromoteRequest(
                targetEnvironment = EnvironmentRef("staging"),
                version = SemanticVersion(2, 0, 0),
            )
        )

        assertIs<Outcome.Failure<ReleaseFailure>>(outcome)
        val rejected = outcome.reason as ReleaseFailure.PromotionRejected
        assertEquals(SemanticVersion(2, 0, 0), rejected.version)
        assertEquals("synthetic-tag-failed", rejected.reason)
        assertEquals(false, rejected.requiresApproval)
    }

    // ----- capabilities -----

    @Test
    fun `descriptor advertises calculate and promote capabilities`() {
        val manager = GitTagBasedReleaseManager(FakeScmSource(Outcome.Success(TagResult("v1.0.0", "sha"))), FakeSecretResolver())

        val calculate = manager.descriptor(dev.rubentxu.pipelattice.release.release.ReleaseManager.RELEASE_CALCULATE_V1)
        val promote = manager.descriptor(dev.rubentxu.pipelattice.release.release.ReleaseManager.RELEASE_PROMOTE_V1)
        val unknown = manager.descriptor(dev.rubentxu.pipelattice.foundation.capability.CapabilityId("unknown"))

        assertTrue(calculate != null, "should advertise RELEASE_CALCULATE_V1")
        assertTrue(promote != null, "should advertise RELEASE_PROMOTE_V1")
        assertTrue(unknown == null, "should not advertise unknown capability")
    }
}
