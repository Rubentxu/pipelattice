package dev.rubentxu.pipelattice.release.adapter.scm

import dev.rubentxu.pipelattice.foundation.capability.SideEffect
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.foundation.secret.SecretFailure
import dev.rubentxu.pipelattice.foundation.secret.SecretRef
import dev.rubentxu.pipelattice.foundation.secret.SecretResolver
import dev.rubentxu.pipelattice.foundation.secret.SecretValue
import dev.rubentxu.pipelattice.release.scm.ScmFailure
import dev.rubentxu.pipelattice.release.scm.ScmSource
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TCK contract tests for [JGitScmSource] as a real ScmSource adapter.
 *
 * Tests the invariants that apply to real adapters:
 * - Invariant 5: descriptor side-effects match expected values
 * - Invariant 1: checkout/tag/push returns real outcomes against real repos
 *
 * Invariants 3 and 4 (queue-based: empty-queue-raises, invocations-stability)
 * are NOT applicable to real adapters — they don't have scripted queues.
 */
class JGitScmSourceTCKTest {

    @TempDir
    lateinit var tempDir: Path

    private class FakeSecretResolver : SecretResolver {
        override suspend fun resolve(ref: SecretRef): Outcome<SecretValue, SecretFailure> =
            Outcome.Failure(SecretFailure.Unknown(ref.authority, ref.key))
    }

    /**
     * Creates a bare repository with one commit pushed to it.
     */
    private fun setupBareRepo(repoName: String, commitMessage: String = "initial commit"): Path {
        val bareDir = tempDir.resolve(repoName)
        Files.createDirectories(bareDir)

        val bareGit = Git.init()
            .setDirectory(bareDir.toFile())
            .setBare(true)
            .call()

        val config = bareGit.repository.config
        config.setString("user", null, "email", "test@example.com")
        config.setString("user", null, "name", "Test User")
        config.save()
        bareGit.close()

        // Clone and commit
        val workDir = tempDir.resolve("work-$repoName")
        val workGit = Git.cloneRepository()
            .setURI("file://$bareDir")
            .setDirectory(workDir.toFile())
            .call()

        workDir.resolve("file.txt").toFile().writeText("content-$commitMessage")
        workGit.add().addFilepattern(".").call()
        workGit.commit().setMessage(commitMessage).call()
        workGit.push().setPushAll().call()
        workGit.close()

        return bareDir
    }

    // ----- Invariant 5: side-effect consistency -----

    @Test
    fun `descriptor checkout is READ_ONLY`() {
        val adapter = JGitScmSource(FakeSecretResolver())
        val desc = adapter.descriptor(ScmSource.SCM_CHECKOUT_V1)
        assertNotNull(desc, "SCM_CHECKOUT_V1 must be advertised")
        assertTrue(SideEffect.READ_ONLY in desc.sideEffects, "checkout must be READ_ONLY")
    }

    @Test
    fun `descriptor tag is MUTATING`() {
        val adapter = JGitScmSource(FakeSecretResolver())
        val desc = adapter.descriptor(ScmSource.SCM_TAG_V1)
        assertNotNull(desc, "SCM_TAG_V1 must be advertised")
        assertTrue(SideEffect.MUTATING in desc.sideEffects, "tag must be MUTATING")
    }

    @Test
    fun `descriptor push is MUTATING`() {
        val adapter = JGitScmSource(FakeSecretResolver())
        val desc = adapter.descriptor(ScmSource.SCM_PUSH_V1)
        assertNotNull(desc, "SCM_PUSH_V1 must be advertised")
        assertTrue(SideEffect.MUTATING in desc.sideEffects, "push must be MUTATING")
    }

    // ----- Invariant 1: real success operations -----

    @Test
    fun `checkout real repo returns Success with real working directory`() = runBlocking {
        val bareDir = setupBareRepo("tck-checkout-repo")
        val adapter = JGitScmSource(FakeSecretResolver())

        val outcome = adapter.checkout(
            dev.rubentxu.pipelattice.release.scm.CheckoutRequest(
                repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("file://$bareDir"),
                revisionHint = "HEAD",
            )
        )

        assertIs<Outcome.Success<*>>(outcome)
        val result = outcome.value as dev.rubentxu.pipelattice.release.scm.CheckoutResult
        assertTrue(Files.exists(result.workingDirectory), "working directory must exist")
        assertTrue(result.revision.isNotBlank(), "revision must not be blank")
    }

    @Test
    fun `tag real repo returns Success with correct tag name`() = runBlocking {
        val bareDir = setupBareRepo("tck-tag-repo", "tag-test")
        val adapter = JGitScmSource(FakeSecretResolver())

        // Get the SHA from the bare repo
        val bareGit = Git.open(bareDir.toFile())
        val sha = bareGit.repository.resolve("refs/heads/main").name()
        bareGit.close()

        val outcome = adapter.tag(
            dev.rubentxu.pipelattice.release.scm.TagRequest(
                repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("file://$bareDir"),
                revision = sha,
                tagName = "v1.0.0",
                message = "Release v1.0.0",
            )
        )

        assertIs<Outcome.Success<*>>(outcome)
        val result = outcome.value as dev.rubentxu.pipelattice.release.scm.TagResult
        assertTrue(result.tagName == "v1.0.0", "tag name must match")
        assertTrue(result.revision == sha, "revision must match")
    }

    @Test
    fun `push real repo returns Success with ref specs`() = runBlocking {
        val bareDir = setupBareRepo("tck-push-repo")
        val adapter = JGitScmSource(FakeSecretResolver())

        val outcome = adapter.push(
            dev.rubentxu.pipelattice.release.scm.PushRequest(
                repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("file://$bareDir"),
                remote = "origin",
                refSpecs = listOf("refs/heads/main"),
            )
        )

        assertIs<Outcome.Success<*>>(outcome)
        val result = outcome.value as dev.rubentxu.pipelattice.release.scm.PushResult
        assertTrue(result.pushedRefs == listOf("refs/heads/main"), "pushed refs must match")
    }

    // ----- Invariant 2: typed failures -----

    @Test
    fun `checkout nonexistent ref returns typed Unknown failure`() = runBlocking {
        val bareDir = setupBareRepo("tck-checkout-missing-repo")
        val adapter = JGitScmSource(FakeSecretResolver())

        val outcome = adapter.checkout(
            dev.rubentxu.pipelattice.release.scm.CheckoutRequest(
                repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("file://$bareDir"),
                revisionHint = "nonexistent-ref-xyz",
            )
        )

        assertIs<Outcome.Failure<ScmFailure>>(outcome)
        val unknown = outcome.reason as ScmFailure.Unknown
        assertTrue(unknown.operation == "checkout")
    }

    @Test
    fun `tag nonexistent ref returns typed Unknown failure`() = runBlocking {
        val bareDir = setupBareRepo("tck-tag-missing-repo")
        val adapter = JGitScmSource(FakeSecretResolver())

        val outcome = adapter.tag(
            dev.rubentxu.pipelattice.release.scm.TagRequest(
                repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("file://$bareDir"),
                revision = "nonexistent-sha-xyz",
                tagName = "v99.0.0",
            )
        )

        assertIs<Outcome.Failure<ScmFailure>>(outcome)
        val unknown = outcome.reason as ScmFailure.Unknown
        assertTrue(unknown.operation == "tag")
    }
}
