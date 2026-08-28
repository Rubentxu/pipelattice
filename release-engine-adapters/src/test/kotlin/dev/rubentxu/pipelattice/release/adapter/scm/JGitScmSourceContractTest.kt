package dev.rubentxu.pipelattice.release.adapter.scm

import dev.rubentxu.pipelattice.foundation.capability.CapabilityId
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.foundation.secret.SecretFailure
import dev.rubentxu.pipelattice.foundation.secret.SecretRef
import dev.rubentxu.pipelattice.foundation.secret.SecretResolver
import dev.rubentxu.pipelattice.foundation.secret.SecretValue
import dev.rubentxu.pipelattice.release.contract.ScmSourceContract
import dev.rubentxu.pipelattice.release.scm.CheckoutResult
import dev.rubentxu.pipelattice.release.scm.PushResult
import dev.rubentxu.pipelattice.release.scm.ScmSource
import dev.rubentxu.pipelattice.release.scm.TagResult
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Real adapter TCK shim for JGitScmSource.
 *
 * Extends ScmSourceContract overriding newSubject() and only the fake-only
 * invariant (invariant_invocations_stable).
 *
 * Behavioral invariants are inherited from ScmSourceContract and execute
 * against real JGit fixtures via contract fixture hooks.
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

    // --- Contract fixture hooks (real JGit setup) ---

    /**
     * Creates a real JGit bare repository with an initial commit.
     * The contract's invariant_checkout_success uses this to set up the real fixture.
     */
    override suspend fun setupCheckoutSuccess(result: CheckoutResult) {
        val bareDir = tempDir.resolve("checkout-fixture")
        Files.createDirectories(bareDir)
        val bareGit = Git.init().setDirectory(bareDir.toFile()).setBare(true).call()
        bareGit.repository.config.setString("user", null, "email", "test@example.com")
        bareGit.repository.config.setString("user", null, "name", "Test User")
        bareGit.repository.config.save()
        bareGit.close()

        // Clone and commit to create initial state
        val workDir = tempDir.resolve("work-checkout")
        val workGit = Git.cloneRepository()
            .setURI("file://$bareDir")
            .setDirectory(workDir.toFile())
            .call()
        workDir.resolve("file.txt").toFile().writeText("initial content")
        workGit.add().addFilepattern(".").call()
        workGit.commit().setMessage("initial commit").call()
        workGit.push().setPushAll().call()
        workGit.close()
    }

    /**
     * Sets up checkout failure scenario using nonexistent repository.
     */
    override suspend fun setupCheckoutFailure(failure: dev.rubentxu.pipelattice.release.scm.ScmFailure) {
        // The failure is triggered by using a nonexistent path in the request.
        // No additional setup needed; the contract's request uses a nonexistent ref.
    }

    /**
     * Creates a real JGit bare repository with an initial commit for tag tests.
     */
    override suspend fun setupTagSuccess(result: TagResult) {
        val bareDir = tempDir.resolve("tag-fixture")
        Files.createDirectories(bareDir)
        val bareGit = Git.init().setDirectory(bareDir.toFile()).setBare(true).call()
        bareGit.repository.config.setString("user", null, "email", "test@example.com")
        bareGit.repository.config.setString("user", null, "name", "Test User")
        bareGit.repository.config.save()
        bareGit.close()

        val workDir = tempDir.resolve("work-tag")
        val workGit = Git.cloneRepository()
            .setURI("file://$bareDir")
            .setDirectory(workDir.toFile())
            .call()
        workDir.resolve("file.txt").toFile().writeText("tag content")
        workGit.add().addFilepattern(".").call()
        workGit.commit().setMessage("initial commit for tag").call()
        workGit.push().setPushAll().call()
        workGit.close()
    }

    /**
     * Sets up tag failure scenario using nonexistent revision.
     */
    override suspend fun setupTagFailure(failure: dev.rubentxu.pipelattice.release.scm.ScmFailure) {
        // Failure triggered by nonexistent revision in the request.
    }

    /**
     * Creates a real JGit bare repository for push tests.
     */
    override suspend fun setupPushSuccess(result: PushResult) {
        val bareDir = tempDir.resolve("push-fixture")
        Files.createDirectories(bareDir)
        val bareGit = Git.init().setDirectory(bareDir.toFile()).setBare(true).call()
        bareGit.repository.config.setString("user", null, "email", "test@example.com")
        bareGit.repository.config.setString("user", null, "name", "Test User")
        bareGit.repository.config.save()
        bareGit.close()

        val workDir = tempDir.resolve("work-push")
        val workGit = Git.cloneRepository()
            .setURI("file://$bareDir")
            .setDirectory(workDir.toFile())
            .call()
        workDir.resolve("file.txt").toFile().writeText("push content")
        workGit.add().addFilepattern(".").call()
        workGit.commit().setMessage("initial commit for push").call()
        workGit.push().setPushAll().call()
        workGit.close()
    }

    // --- Only fake-only invariant override allowed per spec v5 matrix ---
    override fun invariant_invocations_stable() {
        // Real JGit adapters keep no invocation log; skip this invariant.
        // Override with no-op (designated hook per contract's fake-only classification).
        assertTrue(true, "real adapters don't use queue-based scripting")
    }
}
