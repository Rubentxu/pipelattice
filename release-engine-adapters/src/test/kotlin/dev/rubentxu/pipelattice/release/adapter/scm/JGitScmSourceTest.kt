package dev.rubentxu.pipelattice.release.adapter.scm

import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.foundation.secret.SecretFailure
import dev.rubentxu.pipelattice.foundation.secret.SecretRef
import dev.rubentxu.pipelattice.foundation.secret.SecretResolver
import dev.rubentxu.pipelattice.foundation.secret.SecretValue
import dev.rubentxu.pipelattice.release.scm.CheckoutRequest
import dev.rubentxu.pipelattice.release.scm.PushRequest
import dev.rubentxu.pipelattice.release.scm.PushResult
import dev.rubentxu.pipelattice.release.scm.RepositoryRef
import dev.rubentxu.pipelattice.release.scm.ScmFailure
import dev.rubentxu.pipelattice.release.scm.TagRequest
import dev.rubentxu.pipelattice.release.scm.TagResult
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevWalk
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for [JGitScmSource] using real JGit bare temp repositories.
 *
 * JGitScmSource targets bare repositories (file:// scheme).
 * These tests verify checkout, tag, and push operations.
 */
class JGitScmSourceTest {

    @TempDir
    lateinit var tempDir: Path

    /**
     * A fake SecretResolver that always returns failure — sufficient for push tests
     * since push to file:// repos is a no-op that doesn't use the resolver.
     */
    private class FakeSecretResolver : SecretResolver {
        override suspend fun resolve(ref: SecretRef): Outcome<SecretValue, SecretFailure> =
            Outcome.Failure(SecretFailure.Unknown(ref.authority, ref.key))
    }

    /**
     * Converts ObjectId to RevObject using RevWalk (required for TagCommand.setObjectId).
     */
    private fun Git.toRevObject(objectId: ObjectId): org.eclipse.jgit.revwalk.RevObject {
        val revWalk = RevWalk(repository)
        return revWalk.use { it.parseAny(objectId) }
    }

    /**
     * Creates a bare repository with one commit pushed to it.
     * Returns Pair of (bareDir, workDir).
     */
    private fun setupBareRepoWithClone(repoName: String, commitMessage: String = "initial commit"): Pair<Path, Path> {
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

        // Clone the bare repo to get a working tree
        val workDir = tempDir.resolve("work-$repoName")
        val workGit = Git.cloneRepository()
            .setURI("file://$bareDir")
            .setDirectory(workDir.toFile())
            .call()

        // Make initial commit and push
        workDir.resolve("file.txt").toFile().writeText("content-$commitMessage")
        workGit.add().addFilepattern(".").call()
        workGit.commit().setMessage(commitMessage).call()
        workGit.push().setPushAll().call()

        workGit.close()
        return Pair(bareDir, workDir)
    }

    // ----- checkout -----

    @Test
    fun `checkout missing ref returns failure`() = runBlocking {
        val (bareDir, _) = setupBareRepoWithClone("checkout-missing-repo")

        val repo = JGitScmSource(FakeSecretResolver())
        val outcome = repo.checkout(
            CheckoutRequest(
                repository = RepositoryRef.parse("file://$bareDir"),
                revisionHint = "nonexistent-branch-xyz",
            )
        )

        assertIs<Outcome.Failure<ScmFailure>>(outcome)
        val unknown = outcome.reason as ScmFailure.Unknown
        assertEquals("checkout", unknown.operation)
        assertEquals("synthetic-missing-ref", unknown.reason)
    }

    // ----- tag -----

    @Test
    fun `tag missing ref returns failure`() = runBlocking {
        val (bareDir, _) = setupBareRepoWithClone("tag-missing-repo")

        val repo = JGitScmSource(FakeSecretResolver())
        val outcome = repo.tag(
            TagRequest(
                repository = RepositoryRef.parse("file://$bareDir"),
                revision = "nonexistent-sha-xyz",
                tagName = "v99.0.0",
            )
        )

        assertIs<Outcome.Failure<ScmFailure>>(outcome)
        val unknown = outcome.reason as ScmFailure.Unknown
        assertEquals("tag", unknown.operation)
        assertEquals("synthetic-missing-ref", unknown.reason)
    }

    // ----- push -----

    @Test
    fun `push file repo returns success with ref specs`() = runBlocking {
        val (_, workDir) = setupBareRepoWithClone("push-repo")

        val repo = JGitScmSource(FakeSecretResolver())
        val outcome = repo.push(
            PushRequest(
                repository = RepositoryRef.parse("file://$workDir"),
                remote = "origin",
                refSpecs = listOf("refs/heads/main"),
            )
        )

        assertIs<Outcome.Success<PushResult>>(outcome)
        assertEquals(listOf("refs/heads/main"), outcome.value.pushedRefs)
        assertEquals("refs/heads/main", outcome.value.updatedRef)
    }

    @Test
    fun `push missing repo returns failure`() = runBlocking {
        val nonexistent = tempDir.resolve("nonexistent-repo")

        val repo = JGitScmSource(FakeSecretResolver())
        val outcome = repo.push(
            PushRequest(
                repository = RepositoryRef.parse("file://$nonexistent"),
                remote = "origin",
                refSpecs = listOf("refs/heads/main"),
            )
        )

        assertIs<Outcome.Failure<ScmFailure>>(outcome)
    }

    // ----- capabilities -----

    @Test
    fun `descriptor advertises checkout tag push capabilities`() {
        val repo = JGitScmSource(FakeSecretResolver())

        val checkout = repo.descriptor(dev.rubentxu.pipelattice.release.scm.ScmSource.SCM_CHECKOUT_V1)
        val tag = repo.descriptor(dev.rubentxu.pipelattice.release.scm.ScmSource.SCM_TAG_V1)
        val push = repo.descriptor(dev.rubentxu.pipelattice.release.scm.ScmSource.SCM_PUSH_V1)
        val unknown = repo.descriptor(dev.rubentxu.pipelattice.foundation.capability.CapabilityId("unknown"))

        assertNotNull(checkout, "should advertise SCM_CHECKOUT_V1")
        assertNotNull(tag, "should advertise SCM_TAG_V1")
        assertNotNull(push, "should advertise SCM_PUSH_V1")
        assertNull(unknown, "should not advertise unknown capability")
    }
}
