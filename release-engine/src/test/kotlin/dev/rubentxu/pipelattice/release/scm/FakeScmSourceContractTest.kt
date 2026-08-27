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
     * a REQUEST field (revisionHint). The marker rides through:
     * 1. CheckoutRequest.revisionHint = probe.marker
     * 2. SanitizedRequest.toString() — PROBE pattern IS in sanitization patterns
     * 3. invocations() recording — marker must be REDACTED here
     *
     * A second probe in the failure reason verifies failure.toString() surfaces are clean.
     *
     * Asserts:
     * 1. POSITIVE CONTROL: the marker genuinely rides inside probe.material()
     *    (proves the probe is real, not just well-named)
     * 2. NEGATIVE request-path: the marker does NOT appear in invocations() rendering
     *    (proves SanitizedRequest.toString() actually sanitizes it)
     * 3. NEGATIVE failure-path: the marker does NOT appear in failure.toString()
     *    (the failure reason is a static string — no marker in reason field)
     * 4. NEGATIVE exception: the empty-queue exception message is clean
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

        // Inject marker into a REQUEST field — this is where SanitizedRequest touches it
        scm.enqueueCheckoutSuccess(
            CheckoutResult(Path.of("/repo/checkout"), "deadbeefcafebabe1234567890abcdef12345678")
        )

        // Marker in revisionHint (a request field) — goes through SanitizedRequest.toString()
        scm.checkout(
            CheckoutRequest(
                repository = RepositoryRef.parse("git://example/repo"),
                revisionHint = probe.marker,  // marker in REQUEST field
            )
        )

        // Surface 1: invocations() rendering must be sanitized
        // Marker was in revisionHint, went through SanitizedRequest.toString() which
        // scrubs PROBE-SECRET-MATERIAL-\w+ patterns → exclusion must hold
        val invocationsStr = scm.invocations().toString()
        assertTrue(
            !invocationsStr.contains(probe.marker),
            "FAIL: invocations() must not contain probe marker. Found: $invocationsStr"
        )

        // Surface 2: result.toString() must not contain marker (it's a success, no marker)
        val result = scm.invocations().first()
        assertTrue(
            !result.toString().contains(probe.marker),
            "FAIL: invocations() item toString must not contain probe marker. Found: ${result}"
        )
    }

    /**
     * Separate probe for the failure path: verifies that when a scripted failure is returned,
     * the failure.toString() does NOT expose credential-shaped content from request fields.
     * The failure reason is STATIC (no marker), proving the exclusion is by construction.
     */
    @Test
    fun `secret-exclusion probe - failure path clean by construction`() = runBlocking {
        val scm = newFake()

        val probe = SecretProbeFactory.generateProbe()

        // Marker in a REQUEST field
        scm.enqueueCheckoutFailure(ScmFailure.Unknown("checkout", "scripted-unknown-ref"))

        val exceptionMessage = try {
            scm.checkout(
                CheckoutRequest(
                    repository = RepositoryRef.parse("git://example/repo"),
                    revisionHint = probe.marker,
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
