package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.build.domain.Command
import dev.rubentxu.pipelattice.build.domain.CommandResult
import dev.rubentxu.pipelattice.build.domain.Executable
import dev.rubentxu.pipelattice.fleet.diff.ports.GitRefResolution
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [GitSnapshotRepository] ref resolution, null/error mapping, and process isolation.
 *
 * Uses [FakeGitRunner] to script deterministic git responses without a real git binary.
 *
 * Covers spec scenarios:
 * - Sc06: GitSnapshotRepository resolves a real ref and produces a placeholder snapshot
 * - Sc07: GitSnapshotRepository.load returns null for unknown ref (not throws)
 * - Sc08: GitSnapshotRepository throws GitRepositoryUnavailableException on non-git workingDir
 * - Sc10: constructor signature matches spec (process API imports forbidden by FARCH-016)
 */
class GitSnapshotRepositoryTest {

    private val workingDir = Path.of("/tmp/fake-git-repo")

    @Test
    fun `load happy path returns snapshot with empty content and tag-prefixed fingerprint`() {
        val fakeRunner = FakeGitRunner()
        fakeRunner.enqueue(CommandResult.Success("abc123def456789".padEnd(40, '0') + "\n", ""))
        val factory = GitSnapshotFactory()
        val repo = GitSnapshotRepository(workingDir, fakeRunner, factory)

        val snapshot = repo.load("HEAD")

        assertNotNull(snapshot)
        assertTrue(snapshot.nodes.isEmpty())
        assertTrue(snapshot.edges.isEmpty())
        // Fingerprint is SHA-256("git-ref-only/v1:" + sha)
        assertEquals(64, snapshot.fingerprint.value.length)
        assertTrue(snapshot.fingerprint.value.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `load returns null when git exit non-zero`() {
        val fakeRunner = FakeGitRunner()
        fakeRunner.enqueue(
            CommandResult.Failed(
                exitCode = 128,
                stdout = "",
                stderr = "fatal: bad revision 'nonexistent-branch'",
            ),
        )
        val factory = GitSnapshotFactory()
        val repo = GitSnapshotRepository(workingDir, fakeRunner, factory)

        val snapshot = repo.load("nonexistent-branch")

        assertNull(snapshot)
    }

    @Test
    fun `load throws GitRepositoryUnavailable when not a repo`() {
        val fakeRunner = FakeGitRunner()
        fakeRunner.enqueue(
            CommandResult.Failed(
                exitCode = 128,
                stdout = "",
                stderr = "fatal: not a git repository",
            ),
        )
        val factory = GitSnapshotFactory()
        val repo = GitSnapshotRepository(workingDir, fakeRunner, factory)

        val exception = assertFailsWith<GitRepositoryUnavailableException> {
            repo.load("HEAD")
        }

        assertTrue(exception.message!!.contains(workingDir.toString()))
    }

    @Test
    fun `invocations record exactly one rev-parse command`() {
        val fakeRunner = FakeGitRunner()
        fakeRunner.enqueue(CommandResult.Success("abc123def456789".padEnd(40, '0') + "\n", ""))
        val factory = GitSnapshotFactory()
        val repo = GitSnapshotRepository(workingDir, fakeRunner, factory)

        repo.load("HEAD")

        assertEquals(1, fakeRunner.invocationCount())
        val invocations = fakeRunner.invocations()
        assertEquals(1, invocations.size)
        val command = invocations[0]
        assertEquals("git", command.executable.value)
        val argStrings = command.arguments.map { it.value }
        assertTrue(argStrings.contains("rev-parse"))
        assertTrue(argStrings.contains("--verify"))
        assertEquals(workingDir, command.workingDirectory)
    }
}
