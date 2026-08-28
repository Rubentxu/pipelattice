package dev.rubentxu.pipelattice.release.adapter.scm

import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.foundation.secret.SecretFailure
import dev.rubentxu.pipelattice.foundation.secret.SecretRef
import dev.rubentxu.pipelattice.foundation.secret.SecretResolver
import dev.rubentxu.pipelattice.foundation.secret.SecretValue
import dev.rubentxu.pipelattice.release.scm.PushRequest
import dev.rubentxu.pipelattice.release.scm.PushResult
import dev.rubentxu.pipelattice.release.scm.RepositoryRef
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Push arrival test for [JGitScmSource].
 *
 * Verifies that after a successful push, the ref actually exists on the bare
 * remote repository — not just that the push returned success.
 *
 * This test would fail against a no-op mutant that returns success without
 * actually pushing.
 */
class JGitScmSourcePushArrivalTest {

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
     * Creates a bare repository with a working clone that has made a commit.
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

    @Test
    fun `push arrives at bare remote and ref resolves`() {
        runBlocking {
            val (bareDir, workDir) = setupBareRepoWithClone("arrival-test")

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

            // Verify the ref actually arrived on the bare remote
            Git.open(bareDir.toFile()).use { git ->
                val resolved = git.repository.resolve("refs/heads/main")
                assertNotNull(resolved, "refs/heads/main should exist on bare remote after push")
            }
        }
    }

    @Test
    fun `push with new branch arrives at bare remote`() {
        runBlocking {
            val (bareDir, workDir) = setupBareRepoWithClone("arrival-branch-test")

            // Create a new branch with a commit
            Git.open(workDir.toFile()).use { workGit ->
                workGit.branchCreate().setName("feature/test").call()
                workDir.resolve("feature.txt").toFile().writeText("feature content")
                workGit.add().addFilepattern(".").call()
                workGit.commit().setMessage("feature commit").call()
                workGit.push()
                    .setRemote("origin")
                    .setRefSpecs(org.eclipse.jgit.transport.RefSpec("refs/heads/feature/test:refs/heads/feature/test"))
                    .call()
            }

            val repo = JGitScmSource(FakeSecretResolver())
            val outcome = repo.push(
                PushRequest(
                    repository = RepositoryRef.parse("file://$workDir"),
                    remote = "origin",
                    refSpecs = listOf("refs/heads/feature/test"),
                )
            )

            assertIs<Outcome.Success<PushResult>>(outcome)

            // Verify the new branch ref arrived on the bare remote
            Git.open(bareDir.toFile()).use { git ->
                val resolved = git.repository.resolve("refs/heads/feature/test")
                assertNotNull(resolved, "refs/heads/feature/test should exist on bare remote after push")
            }
        }
    }
}
