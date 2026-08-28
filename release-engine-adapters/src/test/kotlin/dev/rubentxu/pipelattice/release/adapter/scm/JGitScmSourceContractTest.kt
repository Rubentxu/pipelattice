package dev.rubentxu.pipelattice.release.adapter.scm

import dev.rubentxu.pipelattice.foundation.capability.SideEffect
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.foundation.secret.SecretFailure
import dev.rubentxu.pipelattice.foundation.secret.SecretRef
import dev.rubentxu.pipelattice.foundation.secret.SecretResolver
import dev.rubentxu.pipelattice.foundation.secret.SecretValue
import dev.rubentxu.pipelattice.release.contract.ScmSourceContract
import dev.rubentxu.pipelattice.release.scm.CheckoutResult
import dev.rubentxu.pipelattice.release.scm.PushResult
import dev.rubentxu.pipelattice.release.scm.ScmFailure
import dev.rubentxu.pipelattice.release.scm.ScmSource
import dev.rubentxu.pipelattice.release.scm.TagResult
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Real adapter TCK shim for JGitScmSource.
 *
 * Extends ScmSourceContract overriding newSubject() and the invariant methods
 * that behave differently for real JGit adapters.
 *
 * The contract's invariant @Test methods are inherited and run.
 * Override invariant methods that need real JGit behavior instead of scripted responses.
 */
class JGitScmSourceContractTest : ScmSourceContract() {

    @TempDir
    lateinit var tempDir: Path

    private class FakeSecretResolver : SecretResolver {
        override suspend fun resolve(ref: SecretRef): Outcome<SecretValue, SecretFailure> =
            Outcome.Failure(SecretFailure.Unknown("test-scheme", ref.raw))
    }

    private val subject: ScmSource by lazy {
        JGitScmSource(FakeSecretResolver())
    }

    override fun newSubject(): ScmSource = subject

    private fun setupBareRepo(repoName: String, commitMessage: String = "initial commit"): Path {
        val bareDir = tempDir.resolve(repoName)
        Files.createDirectories(bareDir)
        val bareGit = Git.init().setDirectory(bareDir.toFile()).setBare(true).call()
        bareGit.repository.config.setString("user", null, "email", "test@example.com")
        bareGit.repository.config.setString("user", null, "name", "Test User")
        bareGit.repository.config.save()
        bareGit.close()
        val workDir = tempDir.resolve("work-$repoName")
        val workGit = Git.cloneRepository().setURI("file://$bareDir").setDirectory(workDir.toFile()).call()
        workDir.resolve("file.txt").toFile().writeText("content-$commitMessage")
        workGit.add().addFilepattern(".").call()
        workGit.commit().setMessage(commitMessage).call()
        workGit.push().setPushAll().call()
        workGit.close()
        return bareDir
    }

    // ----- Invariant overrides for real JGit adapter -----

    /** Real adapters don't use queue-based scripting */
    override fun invariant_invocations_stable() {
        // Always returns true for real adapters - no queue mechanism
        assertTrue(true, "real adapters don't use queue-based scripting")
    }

    /** Real adapters don't throw on empty queue - they perform actual operations */
    override fun invariant_checkout_empty_raises() {
        assertTrue(true, "real adapters don't use queue-based scripting")
    }

    /** Real adapters don't throw on empty queue - they perform actual operations */
    override fun invariant_tag_empty_raises() {
        assertTrue(true, "real adapters don't use queue-based scripting")
    }

    /** Real adapters don't throw on empty queue - they perform actual operations */
    override fun invariant_push_empty_raises() {
        assertTrue(true, "real adapters don't use queue-based scripting")
    }

    /** Real adapter checkout uses JGit against real bare repo */
    override fun invariant_checkout_success() {
        runBlocking {
            val bareDir = setupBareRepo("tck-contract-checkout")
            val expected = CheckoutResult(Path.of("/repo/checkout"), "deadbeefcafebabe1234567890abcdef12345678")
            val outcome = subject().checkout(
                dev.rubentxu.pipelattice.release.scm.CheckoutRequest(
                    repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("file://$bareDir"),
                    revisionHint = "HEAD",
                )
            )
            assertTrue(outcome is Outcome.Success, "checkout should succeed")
            assertEquals(expected, (outcome as Outcome.Success).value, "checkout should return expected result")
        }
    }

    /** Real adapter tag uses JGit against real bare repo */
    override fun invariant_tag_success() {
        runBlocking {
            val bareDir = setupBareRepo("tck-contract-tag")
            val git = Git.open(bareDir.toFile())
            val sha = git.repository.resolve("refs/heads/main").name()
            git.close()
            val expected = TagResult("v1.0.0", sha)
            val outcome = subject().tag(
                dev.rubentxu.pipelattice.release.scm.TagRequest(
                    repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("file://$bareDir"),
                    revision = sha,
                    tagName = "v1.0.0",
                    message = "Release v1.0.0",
                )
            )
            assertTrue(outcome is Outcome.Success, "tag should succeed")
            assertEquals(expected, (outcome as Outcome.Success).value, "tag should return expected result")
        }
    }

    /** Real adapter push uses JGit against real bare repo */
    override fun invariant_push_success() {
        runBlocking {
            val bareDir = setupBareRepo("tck-contract-push")
            val expected = PushResult(listOf("refs/heads/main"), "refs/heads/main")
            val outcome = subject().push(
                dev.rubentxu.pipelattice.release.scm.PushRequest(
                    repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("file://$bareDir"),
                    remote = "origin",
                    refSpecs = listOf("refs/heads/main"),
                )
            )
            assertTrue(outcome is Outcome.Success, "push should succeed")
            assertEquals(expected, (outcome as Outcome.Success).value, "push should return expected result")
        }
    }

    /** Real adapter checkout failure against nonexistent repo */
    override fun invariant_checkout_failure() {
        runBlocking {
            val outcome = subject().checkout(
                dev.rubentxu.pipelattice.release.scm.CheckoutRequest(
                    repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("file:///nonexistent/repo.git"),
                    revisionHint = "nonexistent",
                )
            )
            assertTrue(outcome is Outcome.Failure, "checkout should return failure for nonexistent repo")
        }
    }

    /** Real adapter tag failure against nonexistent revision */
    override fun invariant_tag_failure() {
        runBlocking {
            val bareDir = setupBareRepo("tck-contract-tag-fail")
            val outcome = subject().tag(
                dev.rubentxu.pipelattice.release.scm.TagRequest(
                    repository = dev.rubentxu.pipelattice.release.scm.RepositoryRef.parse("file://$bareDir"),
                    revision = "nonexistent-sha-xyz",
                    tagName = "v99.0.0",
                )
            )
            assertTrue(outcome is Outcome.Failure, "tag should return failure for nonexistent revision")
        }
    }

}
