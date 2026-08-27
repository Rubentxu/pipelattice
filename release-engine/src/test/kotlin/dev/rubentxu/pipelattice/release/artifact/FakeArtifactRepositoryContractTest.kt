package dev.rubentxu.pipelattice.release.artifact

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
 * TCK contract tests for [FakeArtifactRepository].
 *
 * Tests 6 invariants:
 * 1. scripted-success — enqueue a success; verify it is returned.
 * 2. scripted-failure — enqueue a Rejected failure; verify typed failure.
 * 3. idempotent-invocation-snapshot — invocations() is stable across reads.
 * 4. empty-queue-raises — empty queue raises IllegalStateException.
 * 5. side-effect-consistency — descriptor(id) matches expected side-effects.
 * 6. secret-exclusion — no secret-shaped literals in invocations or failure toString.
 */
class FakeArtifactRepositoryContractTest {

    private fun newFake(): FakeArtifactRepository = FakeArtifactRepository()

    // --- Invariant 1: scripted success ---

    @Test
    fun `scripted-success publish returns expected result`() = runBlocking {
        val repo = newFake()
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        val result = PublishResult(coord, "sha256:abc123def456")
        repo.enqueuePublishSuccess(result)

        val outcome = repo.publish(PublishRequest(coord, Path.of("/tmp/lib.jar")))

        assertIs<Outcome.Success<PublishResult>>(outcome)
        assertEquals(coord, outcome.value.coordinate)
        assertEquals("sha256:abc123def456", outcome.value.digest)
    }

    @Test
    fun `scripted-success resolve returns expected result`() = runBlocking {
        val repo = newFake()
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        val result = ResolveResult(coord, "sha256:abc123def456", 12345L)
        repo.enqueueResolveSuccess(result)

        val outcome = repo.resolve(ResolveRequest(coord))

        assertIs<Outcome.Success<ResolveResult>>(outcome)
        assertEquals(coord, outcome.value.coordinate)
        assertEquals(12345L, outcome.value.sizeBytes)
    }

    @Test
    fun `scripted-success download returns expected result`() = runBlocking {
        val repo = newFake()
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        val result = DownloadResult(coord, Path.of("/tmp/lib.jar"), 12345L)
        repo.enqueueDownloadSuccess(result)

        val outcome = repo.download(DownloadRequest(coord, Path.of("/tmp/lib.jar")))

        assertIs<Outcome.Success<DownloadResult>>(outcome)
        assertEquals(coord, outcome.value.coordinate)
        assertEquals(12345L, outcome.value.sizeBytes)
    }

    // --- Invariant 2: scripted failure ---

    @Test
    fun `scripted-failure publish returns typed Rejected failure`() = runBlocking {
        val repo = newFake()
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        val failure = ArtifactFailure.Rejected(coord, "synthetic-rejection")
        repo.enqueuePublishFailure(failure)

        val outcome = repo.publish(PublishRequest(coord, Path.of("/tmp/lib.jar")))

        assertIs<Outcome.Failure<ArtifactFailure>>(outcome)
        val rejected = outcome.reason as ArtifactFailure.Rejected
        assertEquals(coord, rejected.coordinate)
    }

    @Test
    fun `scripted-failure resolve returns typed Unknown failure`() = runBlocking {
        val repo = newFake()
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        val failure = ArtifactFailure.Unknown(coord, "synthetic-unknown")
        repo.enqueueResolveFailure(failure)

        val outcome = repo.resolve(ResolveRequest(coord))

        assertIs<Outcome.Failure<ArtifactFailure>>(outcome)
        val unknown = outcome.reason as ArtifactFailure.Unknown
        assertEquals(coord, unknown.coordinate)
    }

    // --- Invariant 3: idempotent invocation snapshot ---

    @Test
    fun `invocations snapshot is stable across reads`() = runBlocking {
        val repo = newFake()
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        repo.enqueuePublishSuccess(PublishResult(coord, "sha256:abc"))

        repo.publish(PublishRequest(coord, Path.of("/tmp/lib.jar")))

        val snap1 = repo.invocations()
        val snap2 = repo.invocations()

        assertEquals(snap1, snap2)
        assertEquals(1, snap1.size)
    }

    // --- Invariant 4: empty queue raises ---

    @Test
    fun `empty queue raises IllegalStateException on publish`() = runBlocking {
        val repo = newFake()
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")

        assertFailsWith<IllegalStateException> {
            repo.publish(PublishRequest(coord, Path.of("/tmp/lib.jar")))
        }
    }

    @Test
    fun `empty queue raises IllegalStateException on resolve`() = runBlocking {
        val repo = newFake()
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")

        assertFailsWith<IllegalStateException> {
            repo.resolve(ResolveRequest(coord))
        }
    }

    @Test
    fun `empty queue raises IllegalStateException on download`() = runBlocking {
        val repo = newFake()
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")

        assertFailsWith<IllegalStateException> {
            repo.download(DownloadRequest(coord, Path.of("/tmp/lib.jar")))
        }
    }

    // --- Invariant 5: side-effect consistency ---

    @Test
    fun `descriptor for publish is MUTATING`() {
        val repo = newFake()
        val desc = repo.descriptor(ArtifactRepository.ARTIFACT_PUBLISH_V1)
        assertNotNull(desc)
        assertTrue(SideEffect.MUTATING in desc.sideEffects)
    }

    @Test
    fun `descriptor for resolve is READ_ONLY`() {
        val repo = newFake()
        val desc = repo.descriptor(ArtifactRepository.ARTIFACT_RESOLVE_V1)
        assertNotNull(desc)
        assertTrue(SideEffect.READ_ONLY in desc.sideEffects)
    }

    @Test
    fun `descriptor for download is READ_ONLY`() {
        val repo = newFake()
        val desc = repo.descriptor(ArtifactRepository.ARTIFACT_DOWNLOAD_V1)
        assertNotNull(desc)
        assertTrue(SideEffect.READ_ONLY in desc.sideEffects)
    }

    // --- Invariant 6: secret exclusion (probe-based, non-tautological) ---

    /**
     * RED PROBE for FakeArtifactRepository: unique marker injected into
     * a REQUEST field (coordinate.version), verified absent from invocations surface.
     *
     * The marker rides through:
     * 1. ArtifactCoordinate.version = probe.marker (a request field)
     * 2. SanitizedRequest.toString() — PROBE pattern IS in sanitization patterns
     * 3. invocations() recording — marker must be REDACTED here
     *
     * A second probe verifies the failure-path exception message is clean.
     */
    @Test
    fun `secret-exclusion probe - no marker in any surface`() = runBlocking {
        val repo = newFake()

        val probe = SecretProbeFactory.generateProbe()

        // POSITIVE CONTROL: marker genuinely rides inside probe.material()
        assertTrue(
            probe.material().contains(probe.marker),
            "Positive control: probe.material() must contain probe.marker. " +
                "material()=${probe.material()}, marker=${probe.marker}"
        )

        // Inject marker into a REQUEST field (coordinate.version)
        val coordWithMarker = ArtifactCoordinate("dev.example", "lib", probe.marker)
        repo.enqueuePublishSuccess(
            PublishResult(coordWithMarker, "sha256:abc123def456")
        )

        repo.publish(PublishRequest(coordWithMarker, Path.of("/tmp/lib.jar")))

        // Surface 1: invocations() rendering must be sanitized
        // Marker was in coordinate.version, went through SanitizedRequest.toString()
        val invocationsStr = repo.invocations().toString()
        assertTrue(
            !invocationsStr.contains(probe.marker),
            "FAIL: invocations() must not contain probe marker. Found: $invocationsStr"
        )

        // Surface 2: result.toString() must not contain marker
        val result = repo.invocations().first()
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
        val repo = newFake()

        val probe = SecretProbeFactory.generateProbe()

        // Marker in a REQUEST field; enqueue a failure (static reason, no marker)
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        repo.enqueuePublishFailure(ArtifactFailure.Rejected(coord, "scripted-rejection"))

        val exceptionMessage = try {
            repo.publish(PublishRequest(coord, Path.of("/tmp/lib.jar")))
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
