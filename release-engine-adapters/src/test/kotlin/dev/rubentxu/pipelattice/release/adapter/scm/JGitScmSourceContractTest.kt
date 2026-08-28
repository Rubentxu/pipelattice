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
import dev.rubentxu.pipelattice.release.scm.RepositoryRef
import dev.rubentxu.pipelattice.release.scm.ScmSource
import dev.rubentxu.pipelattice.release.scm.TagResult
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Real adapter TCK shim for JGitScmSource.
 *
 * Extends ScmSourceContract overriding newSubject(), expectation hooks,
 * and only the fake-only invariant (invariant_invocations_stable).
 *
 * Behavioral invariants are inherited from ScmSourceContract and execute
 * against real JGit fixtures via the expected* hooks.
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

    // --- Shared fixture state (recreated per-method since @TempDir is method-scoped) ---
    private var checkoutFixture: CheckoutFixture? = null
    private var tagFixture: TagFixture? = null

    private data class CheckoutFixture(val bareDir: Path, val workDir: Path, val headSha: String)
    private data class TagFixture(val bareDir: Path, val headSha: String)

    private fun ensureCheckoutFixture(): CheckoutFixture {
        // Always recreate: @TempDir is method-scoped, cached path would be stale
        checkoutFixture = null
        val bareDir = tempDir.resolve("checkout-fixture")
        Files.createDirectories(bareDir)
        Git.init().setDirectory(bareDir.toFile()).setBare(true).call().use { bareGit ->
            bareGit.repository.config.setString("user", null, "email", "test@example.com")
            bareGit.repository.config.setString("user", null, "name", "Test User")
            bareGit.repository.config.save()
        }
        val workDir = tempDir.resolve("work-checkout-${System.nanoTime()}")
        Git.cloneRepository()
            .setURI(bareDir.toUri().toString())
            .setDirectory(workDir.toFile())
            .call()
            .use { workGit ->
                workDir.resolve("file.txt").toFile().writeText("checkout content")
                workGit.add().addFilepattern(".").call()
                workGit.commit().setMessage("checkout commit").call()
                workGit.push().setPushAll().call()
            }
        val git = Git.open(bareDir.toFile())
        val sha = git.repository.resolve("refs/heads/main").name()
        git.close()
        checkoutFixture = CheckoutFixture(bareDir, workDir, sha)
        return checkoutFixture!!
    }

    private fun ensureTagFixture(): TagFixture {
        // Always recreate: @TempDir is method-scoped, cached path would be stale
        tagFixture = null
        val bareDir = tempDir.resolve("tag-fixture")
        Files.createDirectories(bareDir)
        Git.init().setDirectory(bareDir.toFile()).setBare(true).call().use { bareGit ->
            bareGit.repository.config.setString("user", null, "email", "test@example.com")
            bareGit.repository.config.setString("user", null, "name", "Test User")
            bareGit.repository.config.save()
        }
        val workDir = tempDir.resolve("work-tag-${System.nanoTime()}")
        Git.cloneRepository()
            .setURI(bareDir.toUri().toString())
            .setDirectory(workDir.toFile())
            .call()
            .use { workGit ->
                workDir.resolve("file.txt").toFile().writeText("tag content")
                workGit.add().addFilepattern(".").call()
                workGit.commit().setMessage("tag commit").call()
                workGit.push().setPushAll().call()
            }
        val git = Git.open(bareDir.toFile())
        val sha = git.repository.resolve("refs/heads/main").name()
        git.close()
        tagFixture = TagFixture(bareDir, sha)
        return tagFixture!!
    }

    // --- Contract expectation hooks (property-based compliance) ---

    /**
     * Real JGit checkout returns the actual SHA from the bare repository fixture.
     */
    override fun expectedCheckoutResult(revisionHint: String): CheckoutResult {
        // Use cached fixture if available; ensureCheckoutFixture() always recreates
        val fix = checkoutFixture ?: ensureCheckoutFixture()
        return CheckoutResult(fix.workDir, fix.headSha)
    }

    /**
     * Override assertion hook: compare only revision (commit SHA) since workDir
     * differs between the fixture's clone path and the actual JGit checkout path.
     */
    override fun assertCheckoutSuccess(expectedRef: String, result: CheckoutResult) {
        assertEquals(expectedRef, result.revision, "checkout revision should match")
    }

    /**
     * Returns the repository reference for checkout tests — points to the WORKING repository.
     * Checkout requires a working tree, so we use workDir (cloned from bare), not bareDir.
     */
    override fun checkoutRepositoryRef(): RepositoryRef {
        // Use cached fixture if available; ensureCheckoutFixture() always recreates
        val fix = checkoutFixture ?: ensureCheckoutFixture()
        return RepositoryRef.parse(fix.workDir.toUri().toString())
    }

    /**
     * Real JGit tag returns the actual revision SHA from the bare repository fixture.
     */
    override fun expectedTagResult(tagName: String, revision: String): TagResult {
        // Use cached fixture if available; ensureTagFixture() always recreates
        val fix = tagFixture ?: ensureTagFixture()
        return TagResult(tagName, fix.headSha)
    }

    /**
     * Override fixture revision hook: resolve refs/heads/main from the real bare repo
     * instead of using the fake's hardcoded "abc123def456" which doesn't exist in the fixture.
     */
    override fun fixtureTagRevision(): String {
        // Use cached fixture if available; ensureTagFixture() always recreates
        val fix = tagFixture ?: ensureTagFixture()
        return fix.headSha
    }

    /**
     * Returns the repository reference for tag tests — points to the real bare repository.
     */
    override fun tagRepositoryRef(): RepositoryRef {
        // Use cached fixture - don't recreate! ensureTagFixture() was already called
        // by expectedTagResult() and created the bare repo with refs
        val fix = tagFixture ?: ensureTagFixture()
        return RepositoryRef.parse(fix.bareDir.toUri().toString())
    }

    /**
     * Real adapters don't use queue-based scripting.
     */
    override fun supportsQueueBasedScripting(): Boolean = false

    // --- Contract setup hooks ---

    override suspend fun setupCheckoutSuccess(result: CheckoutResult) {
        // Fixture created on-demand by expectedCheckoutResult/checkoutRepositoryRef
    }

    override suspend fun setupTagSuccess(result: TagResult) {
        // Fixture created on-demand by expectedTagResult/tagRepositoryRef
    }

    override suspend fun setupPushSuccess(result: PushResult) {
        // Push to file:// bare repo is a no-op; no fixture needed
    }

    override suspend fun setupCheckoutFailure(failure: dev.rubentxu.pipelattice.release.scm.ScmFailure) {
        // Failure triggered by nonexistent path in the request
    }

    override suspend fun setupTagFailure(failure: dev.rubentxu.pipelattice.release.scm.ScmFailure) {
        // Failure triggered by nonexistent revision in the request
    }

    // --- Only fake-only invariant override allowed per spec v5 matrix ---
    override fun invariant_invocations_stable() {
        // Real JGit adapters keep no invocation log; skip this invariant.
        assertTrue(true, "real adapters don't use queue-based scripting")
    }
}
