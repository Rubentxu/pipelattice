package dev.rubentxu.pipelattice.fleet.diff.cli

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M11 plumbing-only slow tier integration tests.
 *
 * These tests create real git repositories via `@TempDir` and exercise the CLI end-to-end.
 * They are tagged `@Tag("slow")` and excluded from the default `:fleet-diff:test` task
 * (per `pipelattice.kotlin-jvm.gradle.kts` convention: `excludeTags("slow")`).
 * They run under `./gradlew :fleet-diff:slowTest`.
 *
 * **Requires `git` binary on `PATH`.** The `@EnabledIf("gitAvailableOnPath")` annotation
 * skips the test class cleanly when git is absent (CI environments without git).
 * The probe walks System.getenv("PATH") split by `:` and checks File(dir, "git").canExecute()
 * for each directory, returning true on first hit, false otherwise.
 *
 * Production behavior when user runs `fleet-diff --repo .`:
 * - If `.` is a git repo → git resolves refs → exit 0 or 2 depending on ref validity
 * - If `.` is NOT a git repo → `GitRepositoryUnavailableException` → exit 10
 *
 * This mirrors the design: process execution is in production code (FARCH-016 guards the surface),
 * while test setup uses ProcessBuilder in test source set only (outside the FARCH-016 guard).
 */
@Tag("slow")
@EnabledIf("gitAvailableOnPath")
class CliGitIntegrationTest {

    companion object {
        @JvmStatic
        fun gitAvailableOnPath(): Boolean {
            val pathEnv = System.getenv("PATH") ?: return false
            return pathEnv.split(":").any { dir ->
                File(dir, "git").canExecute()
            }
        }
    }

    @TempDir
    lateinit var tempDir: Path

    private lateinit var gitDir: Path

    @BeforeEach
    fun setUp() {
        gitDir = tempDir.resolve("git-repo")
        // git init via processbuilder - this is TEST code, not production (FARCH-016 exempts test)
        val pb = ProcessBuilder("git", "init")
            .directory(gitDir.toFile())
            .inheritIO()
        pb.start().waitFor()

        // Configure git user email/name (required for commits)
        listOf("git", "config", "user.email", "test@example.com").let { cmd ->
            ProcessBuilder(cmd).directory(gitDir.toFile()).inheritIO().start().waitFor()
        }
        listOf("git", "config", "user.name", "Test User").let { cmd ->
            ProcessBuilder(cmd).directory(gitDir.toFile()).inheritIO().start().waitFor()
        }

        // Create initial commit (required for HEAD to exist)
        val addPb = ProcessBuilder("git", "add", ".")
            .directory(gitDir.toFile())
            .inheritIO()
        addPb.start().waitFor()
        val commitPb = ProcessBuilder("git", "commit", "-m", "initial")
            .directory(gitDir.toFile())
            .inheritIO()
        commitPb.start().waitFor()
    }

    @Test
    fun `--repo repo-dir --base HEAD --candidate HEAD~1 exits 0 with JSON`() {
        // Create a second commit so HEAD~1 exists
        listOf("touch", "file.txt").let { cmd ->
            ProcessBuilder(cmd).directory(gitDir.toFile()).inheritIO().start().waitFor()
        }
        listOf("git", "add", ".").let { cmd ->
            ProcessBuilder(cmd).directory(gitDir.toFile()).inheritIO().start().waitFor()
        }
        listOf("git", "commit", "-m", "second").let { cmd ->
            ProcessBuilder(cmd).directory(gitDir.toFile()).inheritIO().start().waitFor()
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
