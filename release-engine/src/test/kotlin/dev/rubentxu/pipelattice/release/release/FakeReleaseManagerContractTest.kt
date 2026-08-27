package dev.rubentxu.pipelattice.release.release

import dev.rubentxu.pipelattice.foundation.capability.SideEffect
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TCK contract tests for [FakeReleaseManager].
 *
 * Tests 6 invariants:
 * 1. scripted-success — enqueue a success; verify it is returned.
 * 2. scripted-failure — enqueue a PromotionRejected failure; verify typed failure.
 * 3. idempotent-invocation-snapshot — invocations() is stable across reads.
 * 4. empty-queue-raises — empty queue raises IllegalStateException.
 * 5. side-effect-consistency — descriptor(id) matches expected side-effects.
 * 6. secret-exclusion — no secret-shaped literals in invocations or failure toString.
 */
class FakeReleaseManagerContractTest {

    private fun newFake(): FakeReleaseManager = FakeReleaseManager()

    // --- Invariant 1: scripted success ---

    @Test
    fun `scripted-success calculate returns expected result`() = runBlocking {
        val manager = newFake()
        val version = SemanticVersion.parse("1.2.3")
        val result = CalculateResult(version, "main")
        manager.enqueueCalculateSuccess(result)

        val outcome = manager.calculate(
            CalculateRequest(
                sourceRevision = "main",
                previousTag = "v1.2.2",
                bumpPolicy = BumpPolicy.MINOR,
            )
        )

        assertIs<Outcome.Success<CalculateResult>>(outcome)
        assertEquals("1.2.3", outcome.value.version.toString())
        assertEquals("main", outcome.value.sourceRevision)
    }

    // --- Invariant 2: scripted failure ---

    @Test
    fun `scripted-failure promote returns typed PromotionRejected`() = runBlocking {
        val manager = newFake()
        val version = SemanticVersion.parse("1.2.3")
        val failure = ReleaseFailure.PromotionRejected(
            version = version,
            reason = "synthetic-policy-requires-approval",
            requiresApproval = true,
        )
        manager.enqueuePromoteFailure(failure)

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

    // --- Invariant 3: idempotent invocation snapshot ---

    @Test
    fun `invocations snapshot is stable across reads`() = runBlocking {
        val manager = newFake()
        val version = SemanticVersion.parse("1.2.3")
        manager.enqueueCalculateSuccess(CalculateResult(version, "main"))

        manager.calculate(
            CalculateRequest(
                sourceRevision = "main",
                previousTag = "v1.2.2",
                bumpPolicy = BumpPolicy.MINOR,
            )
        )

        val snap1 = manager.invocations()
        val snap2 = manager.invocations()

        assertEquals(snap1, snap2)
        assertEquals(1, snap1.size)
    }

    // --- Invariant 4: empty queue raises ---

    @Test
    fun `empty queue raises IllegalStateException on calculate`() = runBlocking {
        val manager = newFake()

        assertFailsWith<IllegalStateException> {
            manager.calculate(
                CalculateRequest(
                    sourceRevision = "main",
                    previousTag = "v1.2.2",
                    bumpPolicy = BumpPolicy.MINOR,
                )
            )
        }
    }

    @Test
    fun `empty queue raises IllegalStateException on promote`() = runBlocking {
        val manager = newFake()
        val version = SemanticVersion.parse("1.2.3")

        assertFailsWith<IllegalStateException> {
            manager.promote(
                PromoteRequest(
                    targetEnvironment = EnvironmentRef("prod"),
                    version = version,
                )
            )
        }
    }

    // --- Invariant 5: side-effect consistency ---

    @Test
    fun `descriptor for calculate is READ_ONLY`() {
        val manager = newFake()
        val desc = manager.descriptor(ReleaseManager.RELEASE_CALCULATE_V1)
        assertNotNull(desc)
        assertTrue(SideEffect.READ_ONLY in desc.sideEffects)
    }

    @Test
    fun `descriptor for promote is MUTATING`() {
        val manager = newFake()
        val desc = manager.descriptor(ReleaseManager.RELEASE_PROMOTE_V1)
        assertNotNull(desc)
        assertTrue(SideEffect.MUTATING in desc.sideEffects)
    }

    // --- Invariant 6: secret exclusion ---

    @Test
    fun `invocations do not contain secret-shaped literals`() = runBlocking {
        val manager = newFake()
        val version = SemanticVersion.parse("1.2.3")
        manager.enqueueCalculateSuccess(CalculateResult(version, "main"))

        manager.calculate(
            CalculateRequest(
                sourceRevision = "main",
                previousTag = "v1.2.2",
                bumpPolicy = BumpPolicy.MINOR,
            )
        )

        val invocations = manager.invocations()
        val serialized = invocations.toString()

        assertTrue(serialized.indexOf("AKIA") < 0, "Should not contain AWS key pattern")
        assertTrue(serialized.indexOf("ghp_") < 0, "Should not contain GitHub PAT pattern")
        assertTrue(serialized.indexOf("synthetic") < 0, "Should not contain synthetic markers")
    }

    @Test
    fun `failure toString does not contain secret-shaped literals`() = runBlocking {
        val manager = newFake()
        val version = SemanticVersion.parse("1.2.3")
        val failure = ReleaseFailure.PromotionRejected(
            version = version,
            reason = "synthetic-policy-requires-approval",
            requiresApproval = true,
        )
        manager.enqueuePromoteFailure(failure)

        manager.promote(
            PromoteRequest(
                targetEnvironment = EnvironmentRef("prod"),
                version = version,
            )
        )

        val failureStr = failure.toString()
        assertTrue(failureStr.indexOf("AKIA") < 0, "Should not contain AWS key pattern")
        assertTrue(failureStr.indexOf("ghp_") < 0, "Should not contain GitHub PAT pattern")
    }
}
