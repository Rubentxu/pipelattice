package dev.rubentxu.pipelattice.release.adapter.scm

import org.eclipse.jgit.api.Git
import java.nio.file.Files
import java.nio.file.Path

/**
 * Shared fixture helper for creating bare Git repositories with working clones.
 * Used by JGitScmSource contract tests.
 */
object BareRepoFixtureHelper {

    /**
     * Creates a bare repository with a working clone that has made a commit.
     * Returns Triple of (bareDir, workDir, headSha).
     */
    fun createWithClone(
        tempDir: Path,
        prefix: String,
        commitMessage: String = "test commit",
    ): Triple<Path, Path, String> {
        val bareDir = tempDir.resolve("$prefix-bare")
        Files.createDirectories(bareDir)

        Git.init()
            .setDirectory(bareDir.toFile())
            .setBare(true)
            .call()
            .use { bareGit ->
                bareGit.repository.config.setString("user", null, "email", "test@example.com")
                bareGit.repository.config.setString("user", null, "name", "Test User")
                bareGit.repository.config.save()
            }

        val workDir = tempDir.resolve("$prefix-work-${System.nanoTime()}")
        Git.cloneRepository()
            .setURI(bareDir.toUri().toString())
            .setDirectory(workDir.toFile())
            .call()
            .use { workGit ->
                workDir.resolve("file.txt").toFile().writeText("content for $prefix")
                workGit.add().addFilepattern(".").call()
                workGit.commit().setMessage(commitMessage).call()
                workGit.push().setPushAll().call()
            }

        val git = Git.open(bareDir.toFile())
        val sha = git.repository.resolve("refs/heads/main").name()
        git.close()

        return Triple(bareDir, workDir, sha)
    }
}
