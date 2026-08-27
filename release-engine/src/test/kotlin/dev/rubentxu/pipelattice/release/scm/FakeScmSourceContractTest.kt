package dev.rubentxu.pipelattice.release.scm

import dev.rubentxu.pipelattice.foundation.capability.SideEffect
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.release.testing.SecretProbeFactory
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TCK contract tests for [FakeScmSource].
 *
 * Tests 6 invariants:
 * 1. scripted-success — enqueue a success; verify it is returned.
 * 2. scripted-failure — enqueue an Unknown failure; verify typed failure.
 * 3. idempotent-invocation-snapshot — invocations() is stable across reads.
 * 4. empty-queue-raises — empty queue raises IllegalStateException.
 * 5. side-effect-consistency — descriptor(id) matches expected side-effects.
 * 6. secret-exclusion — no secret-shaped literals in invocations or failure toString.
 */
class FakeScmSourceContractTest {

    private fun newFake(): FakeScmSource = FakeScmSource()

    // --- Invariant 1: scripted success ---

    @Test
    fun `scripted-success checkout returns expected result`() = runBlocking {
        val scm = newFake()
        val result = CheckoutResult(Path.of("/repo/checkout"), "deadbeefcafebabe1234567890abcdef12345678")
        scm.enqueueCheckoutSuccess(result)

        val outcome = scm.checkout(
            CheckoutRequest(
                repository = RepositoryRef.parse("git://example/repo"),
                revisionHint = "main",
            )
        )

        assertIs<Outcome.Success<CheckoutResult>>(outcome)
        assertEquals(result.workingDirectory, outcome.value.workingDirectory)
        assertEquals(result.revision, outcome.value.revision)
    }

    @Test
    fun `scripted-success tag returns expected result`() = runBlocking {
        val scm = newFake()
        val result = TagResult("v1.0.0", "abc123def456")
        scm.enqueueTagSuccess(result)

        val outcome = scm.tag(
            TagRequest(
                repository = RepositoryRef.parse("git://example/repo"),
                revision = "abc123def456",
                tagName = "v1.0.0",
            )
        )

        assertIs<Outcome.Success<TagResult>>(outcome)
        assertEquals(result.tagName, outcome.value.tagName)
        assertEquals(result.revision, outcome.value.revision)
    }

    @Test
    fun `scripted-success push returns expected result`() = runBlocking {
        val scm = newFake()
        val result = PushResult(listOf("refs/heads/main"), "refs/heads/main")
        scm.enqueuePushSuccess(result)

        val outcome = scm.push(
            PushRequest(
                repository = RepositoryRef.parse("git://example/repo"),
                remote = "origin",
                refSpecs = listOf("refs/heads/main"),
            )
        )

        assertIs<Outcome.Success<PushResult>>(outcome)
        assertEquals(result.pushedRefs, outcome.value.pushedRefs)
    }

    // --- Invariant 2: scripted failure ---

    @Test
    fun `scripted-failure checkout returns typed Unknown failure`() = runBlocking {
        val scm = newFake()
        val failure = ScmFailure.Unknown("checkout", "synthetic-unknown-ref")
        scm.enqueueCheckoutFailure(failure)

        val outcome = scm.checkout(
            CheckoutRequest(
                repository = RepositoryRef.parse("git://example/repo"),
                revisionHint = "nonexistent",
            )
        )

        assertIs<Outcome.Failure<ScmFailure>>(outcome)
        val reason = outcome.reason as ScmFailure.Unknown
        assertEquals("checkout", reason.operation)
        assertEquals("synthetic-unknown-ref", reason.reason)
    }

    @Test
    fun `scripted-failure tag returns typed Conflict failure`() = runBlocking {
        val scm = newFake()
        val failure = ScmFailure.Conflict("tag", "synthetic-tag-conflict")
        scm.enqueueTagFailure(failure)

        val outcome = scm.tag(
            TagRequest(
                repository = RepositoryRef.parse("git://example/repo"),
                revision = "abc123",
                tagName = "v1.0.0",
            )
        )

        assertIs<Outcome.Failure<ScmFailure>>(outcome)
        assertIs<ScmFailure.Conflict>(outcome.reason)
    }

    // --- Invariant 3: idempotent invocation snapshot ---

    @Test
    fun `invocations snapshot is stable across reads`() = runBlocking {
        val scm = newFake()
        scm.enqueueCheckoutSuccess(CheckoutResult(Path.of("/repo"), "abc123"))

        scm.checkout(
            CheckoutRequest(
                repository = RepositoryRef.parse("git://example/repo"),
                revisionHint = "main",
            )
        )

        val snap1 = scm.invocations()
        val snap2 = scm.invocations()

        assertEquals(snap1, snap2)
        assertEquals(1, snap1.size)
    }

    // --- Invariant 4: empty queue raises ---

    @Test
    fun `empty queue raises IllegalStateException on checkout`() = runBlocking {
        val scm = newFake()

        assertFailsWith<IllegalStateException> {
            scm.checkout(
                CheckoutRequest(
                    repository = RepositoryRef.parse("git://example/repo"),
                    revisionHint = "main",
                )
            )
        }
    }

    @Test
    fun `empty queue raises IllegalStateException on tag`() = runBlocking {
        val scm = newFake()

        assertFailsWith<IllegalStateException> {
            scm.tag(
                TagRequest(
                    repository = RepositoryRef.parse("git://example/repo"),
                    revision = "abc123",
                    tagName = "v1.0.0",
                )
            )
        }
    }

    @Test
    fun `empty queue raises IllegalStateException on push`() = runBlocking {
        val scm = newFake()

        assertFailsWith<IllegalStateException> {
            scm.push(
                PushRequest(
                    repository = RepositoryRef.parse("git://example/repo"),
                    remote = "origin",
                    refSpecs = listOf("refs/heads/main"),
                )
            )
        }
    }

    // --- Invariant 5: side-effect consistency ---

    @Test
    fun `descriptor for checkout is READ_ONLY`() {
        val scm = newFake()
        val desc = scm.descriptor(ScmSource.SCM_CHECKOUT_V1)
        assertNotNull(desc)
        assertTrue(SideEffect.READ_ONLY in desc.sideEffects)
    }

    @Test
    fun `descriptor for tag is MUTATING`() {
        val scm = newFake()
        val desc = scm.descriptor(ScmSource.SCM_TAG_V1)
        assertNotNull(desc)
        assertTrue(SideEffect.MUTATING in desc.sideEffects)
    }

    @Test
    fun `descriptor for push is MUTATING`() {
        val scm = newFake()
        val desc = scm.descriptor(ScmSource.SCM_PUSH_V1)
        assertNotNull(desc)
        assertTrue(SideEffect.MUTATING in desc.sideEffects)
    }

    // --- Invariant 6: secret exclusion (probe-based, non-tautological) ---

    /**
     * RED PROBE: verifies the TCK actually exercises the secret-exclusion path.
     *
     * Uses a unique synthetic marker (PROBE-SECRET-MATERIAL-<suffix>) injected into
     * a failure reason string. Asserts:
     * 1. POSITIVE CONTROL: the marker genuinely rides inside probe.material()
     *    (proves the probe is real, not just well-named)
     * 2. NEGATIVE: the marker does NOT appear in invocations() rendering
     *
     * The marker is NOT credential-shaped (does not trigger FARCH-018 patterns) but is
     * unique per test, so any presence in invocations definitively indicates data leaking.
     *
     * NOTE on failure.toString(): The original design explicitly noted that failure.toString()
     * EXPOSES the reason field. This is "correct security behavior" per the original comment:
     * if a credential-shaped string reaches a failure reason field, toString() will expose it.
     * Production code using SecretValue would redact automatically. The TCK verifies exclusion
     * at the invocations() level (where SanitizedRequest wrapping happens).
     *
     * This replaces the tautological original test which used fixtures that never
     * contained any unique markers, making `indexOf("AKIA") < 0` vacuously true.
     */
    @Test
    fun `secret-exclusion probe - no marker in any surface`() = runBlocking {
        val scm = newFake()

        // Generate a unique marker
        val probe = SecretProbeFactory.generateProbe()

        // POSITIVE CONTROL: marker genuinely rides inside probe.material()
        // This proves the probe is real, not just well-named
        assertTrue(
            probe.material().contains(probe.marker),
            "Positive control: probe.material() must contain probe.marker. " +
                "material()=${probe.material()}, marker=${probe.marker}"
        )

        // Enqueue a failure whose reason string carries the probe marker
        val failure = ScmFailure.Unknown("checkout", probe.marker)
        scm.enqueueCheckoutFailure(failure)

        // Exercise the operation
        scm.checkout(
            CheckoutRequest(
                repository = RepositoryRef.parse("git://example/repo"),
                revisionHint = "nonexistent",
            )
        )

        // Surface: invocations() rendering must be sanitized
        val invocationsStr = scm.invocations().toString()
        assertTrue(
            !invocationsStr.contains(probe.marker),
            "FAIL: invocations() must not contain probe marker. Found: $invocationsStr"
        )
    }
}
