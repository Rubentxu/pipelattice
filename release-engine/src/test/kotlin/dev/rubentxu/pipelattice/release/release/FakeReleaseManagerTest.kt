package dev.rubentxu.pipelattice.release.release

import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Integration-lite tests for [FakeReleaseManager].
 */
class FakeReleaseManagerTest {

    @Test
    fun `calculate roundtrip returns scripted version`() = runBlocking {
        val manager = FakeReleaseManager()
        val version = SemanticVersion.parse("1.2.3")
        manager.enqueueCalculateSuccess(CalculateResult(version, "main"))

        val outcome = manager.calculate(
            CalculateRequest(
                sourceRevision = "main",
                previousTag = "v1.2.2",
                bumpPolicy = BumpPolicy.MINOR,
            )
        )

        assertIs<Outcome.Success<CalculateResult>>(outcome)
        assertEquals("1.2.3", outcome.value.version.toString())
        assertEquals(1, manager.invocations().size)
    }

    @Test
    fun `promote returns typed PromotionRejected with requiresApproval`() = runBlocking {
        val manager = FakeReleaseManager()
        val version = SemanticVersion.parse("1.2.3")
        manager.enqueuePromoteFailure(
            ReleaseFailure.PromotionRejected(
                version = version,
                reason = "synthetic-policy-requires-approval",
                requiresApproval = true,
            )
        )

        val outcome = manager.promote(
            PromoteRequest(
                targetEnvironment = EnvironmentRef("prod"),
                version = version,
            )
        )

        assertIs<Outcome.Failure<ReleaseFailure>>(outcome)
        val reason = outcome.reason as ReleaseFailure.PromotionRejected
        assertTrue(reason.requiresApproval)
        assertEquals(version, reason.version)
    }

    @Test
    fun `calculate then promote via fake`() = runBlocking {
        val manager = FakeReleaseManager()
        val version = SemanticVersion.parse("1.2.3")
        manager.enqueueCalculateSuccess(CalculateResult(version, "main"))
        manager.enqueuePromoteFailure(
            ReleaseFailure.PromotionRejected(
                version,
                "synthetic-policy-requires-approval",
                requiresApproval = true,
            )
        )

        val r1 = manager.calculate(CalculateRequest("main", "v1.2.2", BumpPolicy.MINOR))
        val r2 = manager.promote(
            PromoteRequest(EnvironmentRef("prod"), version)
        )

        assertIs<Outcome.Success<CalculateResult>>(r1)
        assertIs<Outcome.Failure<ReleaseFailure>>(r2)
        assertEquals(2, manager.invocations().size)
    }

    @Test
    fun `reset clears queue and invocations`() = runBlocking {
        val manager = FakeReleaseManager()
        val version = SemanticVersion.parse("1.2.3")
        manager.enqueueCalculateSuccess(CalculateResult(version, "main"))

        manager.calculate(CalculateRequest("main", "v1.2.2", BumpPolicy.MINOR))
        manager.reset()

        assertTrue(manager.invocations().isEmpty())
    }
}
