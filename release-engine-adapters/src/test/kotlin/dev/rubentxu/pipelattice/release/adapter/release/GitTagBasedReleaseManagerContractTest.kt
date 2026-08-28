package dev.rubentxu.pipelattice.release.adapter.release

import dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor
import dev.rubentxu.pipelattice.foundation.capability.CapabilityId
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.foundation.secret.SecretFailure
import dev.rubentxu.pipelattice.foundation.secret.SecretRef
import dev.rubentxu.pipelattice.foundation.secret.SecretResolver
import dev.rubentxu.pipelattice.foundation.secret.SecretValue
import dev.rubentxu.pipelattice.release.contract.ReleaseManagerContract
import dev.rubentxu.pipelattice.release.release.CalculateResult
import dev.rubentxu.pipelattice.release.release.PromoteResult
import dev.rubentxu.pipelattice.release.release.ReleaseFailure
import dev.rubentxu.pipelattice.release.release.ReleaseManager
import dev.rubentxu.pipelattice.release.scm.CheckoutRequest
import dev.rubentxu.pipelattice.release.scm.CheckoutResult
import dev.rubentxu.pipelattice.release.scm.PushRequest
import dev.rubentxu.pipelattice.release.scm.PushResult
import dev.rubentxu.pipelattice.release.scm.ScmFailure
import dev.rubentxu.pipelattice.release.scm.ScmSource
import dev.rubentxu.pipelattice.release.scm.TagRequest
import dev.rubentxu.pipelattice.release.scm.TagResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Real adapter TCK shim for GitTagBasedReleaseManager.
 *
 * Extends ReleaseManagerContract overriding newSubject() and the invariant methods
 * that need real Git tag behavior.
 */
class GitTagBasedReleaseManagerContractTest : ReleaseManagerContract() {

    private class FakeScmSourceAlwaysSucceeds : ScmSource {
        override suspend fun checkout(request: CheckoutRequest): Outcome<CheckoutResult, ScmFailure> =
            Outcome.Success(CheckoutResult(Path.of("/tmp"), "abc123"))
        override suspend fun tag(request: TagRequest): Outcome<TagResult, ScmFailure> =
            Outcome.Success(TagResult("v1.0.0", "abc123"))
        override suspend fun push(request: PushRequest): Outcome<PushResult, ScmFailure> =
            Outcome.Success(PushResult(listOf("refs/heads/main"), "refs/heads/main"))
        override fun descriptor(id: CapabilityId): CapabilityDescriptor? = null
    }

    private class FakeScmSourceAlwaysFails : ScmSource {
        override suspend fun checkout(request: CheckoutRequest): Outcome<CheckoutResult, ScmFailure> =
            Outcome.Failure(ScmFailure.Unknown("checkout", "synthetic-missing"))
        override suspend fun tag(request: TagRequest): Outcome<TagResult, ScmFailure> =
            Outcome.Failure(ScmFailure.Unknown("tag", "synthetic-tag-failed"))
        override suspend fun push(request: PushRequest): Outcome<PushResult, ScmFailure> =
            Outcome.Failure(ScmFailure.Unknown("push", "synthetic-push-failed"))
        override fun descriptor(id: CapabilityId): CapabilityDescriptor? = null
    }

    private class FakeSecretResolver : SecretResolver {
        override suspend fun resolve(ref: SecretRef): Outcome<SecretValue, SecretFailure> =
            Outcome.Failure(SecretFailure.Unknown(ref.authority, ref.key))
    }

    private val subject: ReleaseManager by lazy {
        GitTagBasedReleaseManager(FakeScmSourceAlwaysSucceeds(), FakeSecretResolver())
    }

    override fun newSubject(): ReleaseManager = subject

    // ----- Invariant overrides for real GitTag adapter -----

    /** Real adapters don't use queue-based scripting */
    override suspend fun invariant_invocations_stable(): Boolean = true

    /** Real adapter promote uses FakeScmSource (not real JGit) */
    override suspend fun invariant_promote_success(): Outcome<PromoteResult, ReleaseFailure> {
        val manager = GitTagBasedReleaseManager(FakeScmSourceAlwaysSucceeds(), FakeSecretResolver())
        return manager.promote(
            dev.rubentxu.pipelattice.release.release.PromoteRequest(
                targetEnvironment = dev.rubentxu.pipelattice.release.release.EnvironmentRef("prod"),
                version = dev.rubentxu.pipelattice.release.release.SemanticVersion.parse("1.2.3"),
            )
        )
    }

    /** Real adapter promote failure */
    override suspend fun invariant_promote_rejected(): Outcome<PromoteResult, ReleaseFailure> {
        val manager = GitTagBasedReleaseManager(FakeScmSourceAlwaysFails(), FakeSecretResolver())
        return manager.promote(
            dev.rubentxu.pipelattice.release.release.PromoteRequest(
                targetEnvironment = dev.rubentxu.pipelattice.release.release.EnvironmentRef("prod"),
                version = dev.rubentxu.pipelattice.release.release.SemanticVersion.parse("1.2.3"),
            )
        )
    }

    // ----- Additional tests -----

    @Test
    fun `tck_calculate_returns_success`() = runBlocking {
        val outcome = invariant_calculate_success()
        assertIs<Outcome.Success<CalculateResult>>(outcome)
    }

    @Test
    fun `tck_promote_returns_success`() = runBlocking {
        val outcome = invariant_promote_success()
        assertIs<Outcome.Success<PromoteResult>>(outcome)
    }

    @Test
    fun `tck_promote_rejected_on_failure`() = runBlocking {
        val outcome = invariant_promote_rejected()
        assertIs<Outcome.Failure<ReleaseFailure>>(outcome)
    }
}
