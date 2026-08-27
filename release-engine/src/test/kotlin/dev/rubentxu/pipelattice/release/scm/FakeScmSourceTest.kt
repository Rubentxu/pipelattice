package dev.rubentxu.pipelattice.release.scm

import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Integration-lite tests for [FakeScmSource].
 */
class FakeScmSourceTest {

    @Test
    fun `scripted roundtrip preserves identity`() = runBlocking {
        val scm = FakeScmSource()
        val result = CheckoutResult(
            workingDirectory = Path.of("/repo/checkout"),
            revision = "deadbeefcafebabe1234567890abcdef12345678",
        )
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
        assertEquals(1, scm.invocations().size)
    }

    @Test
    fun `scripted success and failure via FIFO`() = runBlocking {
        val scm = FakeScmSource()
        scm.enqueueCheckoutSuccess(CheckoutResult(Path.of("/repo"), "abc123"))
        scm.enqueueCheckoutFailure(ScmFailure.Unknown("checkout", "synthetic-unknown"))

        val outcome1 = scm.checkout(
            CheckoutRequest(
                repository = RepositoryRef.parse("git://example/repo"),
                revisionHint = "main",
            )
        )
        val outcome2 = scm.checkout(
            CheckoutRequest(
                repository = RepositoryRef.parse("git://example/repo"),
                revisionHint = "nonexistent",
            )
        )

        assertIs<Outcome.Success<CheckoutResult>>(outcome1)
        assertIs<Outcome.Failure<ScmFailure>>(outcome2)
        assertIs<ScmFailure.Unknown>(outcome2.reason)
        assertEquals(2, scm.invocations().size)
    }

    @Test
    fun `tag roundtrip preserves identity`() = runBlocking {
        val scm = FakeScmSource()
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
    fun `push roundtrip preserves identity`() = runBlocking {
        val scm = FakeScmSource()
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

    @Test
    fun `reset clears queue and invocations`() = runBlocking {
        val scm = FakeScmSource()
        scm.enqueueCheckoutSuccess(CheckoutResult(Path.of("/repo"), "abc123"))
        scm.checkout(
            CheckoutRequest(
                repository = RepositoryRef.parse("git://example/repo"),
                revisionHint = "main",
            )
        )

        scm.reset()

        assertTrue(scm.invocations().isEmpty())
    }
}
