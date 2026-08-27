package dev.rubentxu.pipelattice.fleet.diff.cli

import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M11 plumbing-only integration tests, migrated to JGit in M14.
 *
 * These tests create real git repositories via `@TempDir` and exercise the CLI end-to-end.
 * They run in the default `:fleet-diff:test` task (no `@Tag("slow")`, no `@EnabledIf`).
 * After M14 JGit migration, no external `git` binary is required.
 *
 * Production behavior when user runs `fleet-diff --repo .`:
 * - If `.` is a git repo → JGit resolves refs → exit 0 or 2 depending on ref validity
 * - If `.` is NOT a git repo → `GitRepositoryUnavailableException` → exit 10
 */
class CliGitIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var gitDir: Path

    @BeforeEach
    fun setUp() {
        gitDir = tempDir.resolve("git-repo")
        // Initialize a git repo using JGit and create an initial commit
        Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        Git.open(gitDir.toFile()).use { git ->
            // Configure git user (required for commits in JGit)
            val config = git.repository.config
            config.setString("user", null, "email", "test@example.com")
            config.setString("user", null, "name", "Test User")
            config.save()

            // Create a dummy file so there's something to commit
            gitDir.resolve("dummy.txt").toFile().createNewFile()
            git.add()
                .addFilepattern(".")
                .call()
            git.commit()
                .setMessage("initial")
                .call()
        }
    }

    @Test
    fun `--repo repo-dir --base HEAD --candidate HEAD~1 exits 0 with JSON`() {
        // Create a second commit so HEAD~1 exists
        val file = gitDir.resolve("file.txt")
        file.toFile().createNewFile()

        Git.open(gitDir.toFile()).use { git ->
            git.add()
                .addFilepattern(".")
                .call()
            git.commit()
                .setMessage("second")
                .call()
        }

        val code = Main.run(arrayOf("--repo", gitDir.toString(), "--base", "HEAD", "--candidate", "HEAD~1"))
        assertEquals(Main.EXIT_SUCCESS, code)
    }

    @Test
    fun `--repo repo-dir --base nonexistent-branch --candidate HEAD exits 2 with ref in stderr`() {
        // Sc15 test hardening: capture stderr to verify the ref name appears in the error message
        val originalErr = System.err
        val capturedErr = ByteArrayOutputStream()
        try {
            System.setErr(PrintStream(capturedErr))
            val code = Main.run(arrayOf("--repo", gitDir.toString(), "--base", "nonexistent-branch", "--candidate", "HEAD"))
            assertEquals(Main.EXIT_VALIDATION, code)
            assertTrue(capturedErr.toString().contains("nonexistent-branch"),
                "Expected stderr to contain 'nonexistent-branch' but got: ${capturedErr}")
        } finally {
            System.setErr(originalErr)
        }
    }

    @Test
    fun `--repo non-git-dir --base HEAD --candidate HEAD exits 10`() {
        val nonGitDir = tempDir.resolve("non-git")
        java.nio.file.Files.createDirectory(nonGitDir)

        val code = Main.run(arrayOf("--repo", nonGitDir.toString(), "--base", "HEAD", "--candidate", "HEAD"))
        assertEquals(Main.EXIT_INTERNAL, code)
    }

    @Test
    fun `--repo dot --base HEAD missing --candidate exits 64`() {
        val code = Main.run(arrayOf("--repo", gitDir.toString(), "--base", "HEAD"))
        assertEquals(Main.EXIT_USAGE, code)
    }
}
