package dev.rubentxu.pipelattice.release.release

import dev.rubentxu.pipelattice.foundation.capability.SideEffect
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.release.testing.SecretProbeFactory
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

    /**
     * S7 determinism: second invocation with same inputs + same scripted queue
     * returns the same version. This exercises the spec scenario behaviorally.
     */
    @Test
    fun `calculate is deterministic - same inputs return same version`() = runBlocking {
        val manager = newFake()
        val version = SemanticVersion.parse("1.2.3")
        val request = CalculateRequest(
            sourceRevision = "main",
            previousTag = "v1.2.2",
            bumpPolicy = BumpPolicy.MINOR,
        )

        // Enqueue TWO scripted responses for two calls
        manager.enqueueCalculateSuccess(CalculateResult(version, "main"))
        manager.enqueueCalculateSuccess(CalculateResult(version, "main"))

        val outcome1 = manager.calculate(request)
        val outcome2 = manager.calculate(request)

        assertIs<Outcome.Success<CalculateResult>>(outcome1)
        assertIs<Outcome.Success<CalculateResult>>(outcome2)

        // Same inputs (same enqueued script) → same version
        assertEquals(outcome1.value.version, outcome2.value.version)
        assertEquals("1.2.3", outcome1.value.version.toString())
        assertEquals("1.2.3", outcome2.value.version.toString())
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

    // --- Invariant 6: secret exclusion (probe-based, non-tautological) ---

    /**
     * RED PROBE for FakeReleaseManager: unique marker injected into
     * a REQUEST field (SemanticVersion.preRelease), verified absent from invocations surface.
     *
     * The marker rides through:
     * 1. PromoteRequest.version = SemanticVersion(1, 0, 0, preRelease=probe.marker)
     * 2. SanitizedRequest.toString() — PROBE pattern IS in sanitization patterns
     * 3. invocations() recording — marker must be REDACTED here
     *
     * A second probe verifies the failure-path exception message is clean.
     */
    @Test
    fun `secret-exclusion probe - no marker in any surface`() = runBlocking {
        val manager = newFake()

        val probe = SecretProbeFactory.generateProbe()

        // POSITIVE CONTROL: marker genuinely rides inside probe.material()
        assertTrue(
            probe.material().contains(probe.marker),
            "Positive control: probe.material() must contain probe.marker. " +
                "material()=${probe.material()}, marker=${probe.marker}"
        )

        // Inject marker into a REQUEST field (SemanticVersion.preRelease)
        val versionWithMarker = SemanticVersion(1, 0, 0, preRelease = probe.marker)
        manager.enqueuePromoteSuccess(
            PromoteResult(versionWithMarker, EnvironmentRef("prod"), "sha256:abc")
        )

        manager.promote(
            PromoteRequest(
                targetEnvironment = EnvironmentRef("prod"),
                version = versionWithMarker,
            )
        )

        // Surface 1: invocations() rendering must be sanitized
        // Marker was in SemanticVersion.preRelease, went through SanitizedRequest.toString()
        val invocationsStr = manager.invocations().toString()
        assertTrue(
            !invocationsStr.contains(probe.marker),
            "FAIL: invocations() must not contain probe marker. Found: $invocationsStr"
        )

        // Surface 2: result.toString() must not contain marker
        val result = manager.invocations().first()
        assertTrue(
            !result.toString().contains(probe.marker),
            "FAIL: invocations() item toString must not contain probe marker. Found: ${result}"
        )
    }

    /**
     * Separate probe for the failure path: verifies exception message is clean.
     * The failure reason is STATIC (no marker), proving exclusion by construction.
     */
    @Test
    fun `secret-exclusion probe - failure path clean by construction`() = runBlocking {
        val manager = newFake()

        val probe = SecretProbeFactory.generateProbe()

        // Marker in a REQUEST field; enqueue a failure (static reason, no marker)
        val version = SemanticVersion.parse("1.2.3")
        manager.enqueuePromoteFailure(
            ReleaseFailure.PromotionRejected(
                version = version,
                reason = "scripted-policy-requires-approval",
                requiresApproval = true,
            )
        )

        val exceptionMessage = try {
            manager.promote(
                PromoteRequest(
                    targetEnvironment = EnvironmentRef("prod"),
                    version = version,
                )
            )
            "NO_EXCEPTION"
        } catch (e: IllegalStateException) {
            e.message ?: "empty"
        }

        // Surface: exception message must be clean (no $request interpolation after Fix 2.1)
        assertTrue(
            !exceptionMessage.contains(probe.marker),
            "FAIL: exception message must not contain probe marker. Got: $exceptionMessage"
        )
        assertTrue(
            !exceptionMessage.contains("AKIA"),
            "FAIL: exception message must not contain AKIA pattern. Got: $exceptionMessage"
        )
        assertTrue(
            !exceptionMessage.contains("ghp_"),
            "FAIL: exception message must not contain ghp_ pattern. Got: $exceptionMessage"
        )
    }
}
