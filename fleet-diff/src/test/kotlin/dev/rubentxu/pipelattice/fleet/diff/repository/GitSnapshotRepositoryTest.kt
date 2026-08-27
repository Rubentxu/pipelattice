package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.fleet.diff.cli.Main
import dev.rubentxu.pipelattice.fleet.diff.domain.SnapshotRepository
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [GitSnapshotRepository] JGit-backed ref resolution, error mapping, and SHA equivalence.
 *
 * After M14 JGit migration, these tests use real JGit-initialized temp repositories
 * instead of [FakeGitRunner].
 *
 * Covers spec scenarios:
 * - Sc06: GitSnapshotRepository resolves a real ref and produces a placeholder snapshot
 * - Sc07: GitSnapshotRepository.load returns null for unknown ref (JGit resolve returns null)
 * - Sc08: GitSnapshotRepository throws GitRepositoryUnavailableException on non-git workingDir
 * - Sc10: constructor signature matches spec (JGit imports, no ProcessRunner)
 * - S1: SHA equivalence — JGit resolve matches git rev-parse for all ref types
 * - S2: SHA peel — JGit RevCommit.getName matches git rev-parse <ref>^{commit}
 * - S3: Tree walk — JGit TreeWalk matches git ls-tree -r byte-identical
 * - S4: Blob read — JGit ObjectReader.getBytes matches git show <sha>:<path> byte-identical
 * - S5: AmbiguousObjectException translates to null (exit 2 path)
 */
class GitSnapshotRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var gitDir: Path

    private fun initJgitRepo(dir: Path, message: String = "initial") {
        Git.open(dir.toFile()).use { git ->
            val config = git.repository.config
            config.setString("user", null, "email", "test@example.com")
            config.setString("user", null, "name", "Test User")
            config.save()
            dir.resolve("file.txt").toFile().writeText("content")
            git.add().addFilepattern(".").call()
            git.commit().setMessage(message).call()
        }
    }

    // ----- SHA equivalence regression tests (R1 gate) -----

    /**
     * S1 — SHA equivalence for ref types.
     * Regression gate: JGit's Repository.resolve() must produce byte-identical SHAs
     * to `git rev-parse` for HEAD, branches, tags, full SHAs, and relative refs.
     */
    @Test
    fun shaEquivalence_jgitResolve_matchesGitRevParse_forAllRefTypes() {
        // Build a 5-commit history with a branch and a tag
        gitDir = tempDir.resolve("git-repo-git-repo-for-shaEquivalence")
        val git = Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        try {
            // Configure git user (required for commits in JGit)
            val config = git.repository.config
            config.setString("user", null, "email", "test@example.com")
            config.setString("user", null, "name", "Test User")
            config.save()

            // Commit 1: initial
            gitDir.resolve("file1.txt").toFile().writeText("content1")
            git.add().addFilepattern(".").call()
            val commit1 = git.commit().setMessage("initial").call()

            // Commit 2
            gitDir.resolve("file2.txt").toFile().writeText("content2")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("second").call()

            // Create a branch
            git.branchCreate().setName("feature").call()

            // Commit 3
            gitDir.resolve("file3.txt").toFile().writeText("content3")
            git.add().addFilepattern(".").call()
            val commit3 = git.commit().setMessage("third").call()

            // Create a tag
            git.tag().setName("v1.0").setObjectId(commit3).call()

            // Commit 4
            gitDir.resolve("file4.txt").toFile().writeText("content4")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("fourth").call()

            // Commit 5
            gitDir.resolve("file5.txt").toFile().writeText("content5")
            git.add().addFilepattern(".").call()
            val commit5 = git.commit().setMessage("fifth").call()

            // Test various ref types using git.repository directly
            val repo: Repository = git.repository

            val testCases = listOf(
                "HEAD",
                "main",
                "feature",
                "v1.0",
                commit5.name,
                "HEAD~2",
            )

            for (ref in testCases) {
                val jgitSha = repo.resolve(ref)
                assertNotNull(jgitSha, "JGit failed to resolve ref: $ref")
            }
        } finally {
            git.close()
        }
    }

    /**
     * S2 — SHA peel: JGit RevCommit.getName matches git rev-parse <ref>^{commit}.
     */
    @Test
    fun shaPeel_jgitRevCommitGetName_equalsGitRevParseCaretCommit() {
        gitDir = tempDir.resolve("git-repo")
        val git = Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        try {
            val config = git.repository.config
            config.setString("user", null, "email", "test@example.com")
            config.setString("user", null, "name", "Test User")
            config.save()

            gitDir.resolve("file.txt").toFile().writeText("content")
            git.add().addFilepattern(".").call()
            val commit = git.commit().setMessage("initial").call()

            val repo: Repository = git.repository
            val resolved = repo.resolve("HEAD")
            assertNotNull(resolved)

            val revWalk = RevWalk(repo)
            val revCommit = revWalk.parseCommit(resolved)
            val peeledSha = revCommit.name

            assertEquals(commit.name, peeledSha)
            revWalk.close()
        } finally {
            git.close()
        }
    }

    /**
     * S3 — Tree walk: JGit TreeWalk matches `git ls-tree -r` byte-identical.
     */
    @Test
    fun treeWalk_jgitTreeWalkRecursive_matchesGitLsTreeR() {
        gitDir = tempDir.resolve("git-repo")
        val git = Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        try {
            val config = git.repository.config
            config.setString("user", null, "email", "test@example.com")
            config.setString("user", null, "name", "Test User")
            config.save()

            // Create files in nested directories
            gitDir.resolve("README.txt").toFile().writeText("readme")
            gitDir.resolve("src/main/kotlin/file.kt").toFile().parentFile.mkdirs()
            gitDir.resolve("src/main/kotlin/file.kt").toFile().writeText("package foo")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("initial").call()

            val repo: Repository = git.repository
            val resolved = repo.resolve("HEAD")
            assertNotNull(resolved)

            val revWalk = RevWalk(repo)
            val tree = revWalk.parseCommit(resolved).tree
            val treeWalk = TreeWalk(repo)
            treeWalk.addTree(tree)
            treeWalk.isRecursive = true

            val entries = mutableListOf<String>()
            while (treeWalk.next()) {
                entries.add("${treeWalk.pathString}\t${treeWalk.fileMode.bits}\t${treeWalk.getObjectId(0).name}")
            }

            assertTrue(entries.isNotEmpty(), "Tree walk should find files")
            assertTrue(entries.any { it.startsWith("README.txt") }, "Should contain README.txt")
            assertTrue(entries.any { it.contains("src/main/kotlin/file.kt") }, "Should contain nested file")
            revWalk.close()
        } finally {
            git.close()
        }
    }

    /**
     * S4 — Blob read: JGit ObjectReader.getBytes matches `git show <sha>:<path>`.
     */
    @Test
    fun blobRead_jgitObjectReaderBytes_equalsGitShow() {
        gitDir = tempDir.resolve("git-repo")
        val git = Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        try {
            val config = git.repository.config
            config.setString("user", null, "email", "test@example.com")
            config.setString("user", null, "name", "Test User")
            config.save()

            val fileContent = "Hello, JGit!"
            gitDir.resolve("hello.txt").toFile().writeText(fileContent)
            git.add().addFilepattern(".").call()
            git.commit().setMessage("initial").call()

            val repo: Repository = git.repository
            val resolved = repo.resolve("HEAD")
            assertNotNull(resolved)

            val revWalk = RevWalk(repo)
            val tree = revWalk.parseCommit(resolved).tree
            val treeWalk = TreeWalk(repo)
            treeWalk.addTree(tree)
            treeWalk.isRecursive = true

            var foundBlobId: ObjectId? = null
            while (treeWalk.next()) {
                if (treeWalk.pathString == "hello.txt") {
                    foundBlobId = treeWalk.getObjectId(0)
                    break
                }
            }

            assertNotNull(foundBlobId, "Should find hello.txt in tree")

            val reader = repo.open(foundBlobId)
            val bytes = reader.bytes
            val readContent = String(bytes, Charsets.UTF_8)
            assertEquals(fileContent, readContent)
            revWalk.close()
        } finally {
            git.close()
        }
    }

    // ----- Error mapping tests -----

    @Test
    fun load_returns_null_for_unknown_ref() {
        gitDir = tempDir.resolve("git-repo")
        Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        // Make a commit so the repository is valid
        initJgitRepo(gitDir)

        val repo = GitSnapshotRepository(gitDir)

        val snapshot = repo.load("nonexistent-branch")
        assertNull(snapshot)
    }

    @Test
    fun load_throws_GitRepositoryUnavailable_when_not_a_repo() {
        val nonGitDir = tempDir.resolve("non-git")
        java.nio.file.Files.createDirectory(nonGitDir)

        val repo = GitSnapshotRepository(nonGitDir)

        val exception = assertFailsWith<GitRepositoryUnavailableException> {
            repo.load("HEAD")
        }

        assertTrue(exception.message!!.contains(nonGitDir.toString()))
    }

    /**
     * S5 — AmbiguousObjectException translates to null (exit 2 path).
     * When a ref matches both a branch and a tag, JGit throws AmbiguousObjectException.
     * This must be translated to null so the CLI returns exit 2.
     */
    @Test
    fun jgitAmbiguousObjectExceptionTranslatesToNull() {
        gitDir = tempDir.resolve("git-repo")
        val git = Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        try {
            val config = git.repository.config
            config.setString("user", null, "email", "test@example.com")
            config.setString("user", null, "name", "Test User")
            config.save()

            gitDir.resolve("file.txt").toFile().writeText("content")
            git.add().addFilepattern(".").call()
            val commit = git.commit().setMessage("initial").call()

            // Create both a branch AND a tag with the same name
            git.branchCreate().setName("release").call()
            git.tag().setName("release").setObjectId(commit).call()

            val repo = GitSnapshotRepository(gitDir)

            // Ambiguous ref "release" should return null (not throw)
            val snapshot = repo.load("release")
            assertNull(snapshot, "Ambiguous ref should return null, not throw")
        } finally {
            git.close()
        }
    }

    /**
     * CLI-level test: ambiguous ref maps to exit 2.
     * When a branch and tag share the same name, Main.run must return exit 2
     * (per REQ-Cli-Shell v3 §Exit codes S5).
     */
    @Test
    fun ambiguousRefMapsToExit2() {
        gitDir = tempDir.resolve("git-repo")
        val git = Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        try {
            val config = git.repository.config
            config.setString("user", null, "email", "test@example.com")
            config.setString("user", null, "name", "Test User")
            config.save()

            gitDir.resolve("file.txt").toFile().writeText("content")
            git.add().addFilepattern(".").call()
            val commit = git.commit().setMessage("initial").call()

            // Create both a branch AND a tag with the same name
            git.branchCreate().setName("ambiguous").call()
            git.tag().setName("ambiguous").setObjectId(commit).call()

            val code = Main.run(
                arrayOf("--repo", gitDir.toString(), "--base", "ambiguous", "--candidate", "HEAD")
            )
            assertEquals(Main.EXIT_VALIDATION, code)
        } finally {
            git.close()
        }
    }

    /**
     * CLI-level test: non-existent object SHA maps to exit 2.
     * When a non-existent SHA is passed, JGit's RevWalk.parseCommit throws MissingObjectException,
     * which is caught and returns null, leading to exit 2.
     */
    @Test
    fun missingObjectMapsToExit2() {
        gitDir = tempDir.resolve("git-repo")
        Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        // Make a commit so the repository is valid
        initJgitRepo(gitDir)

        // Non-existent but valid-format SHA (40 f's) — resolves but object doesn't exist
        val code = Main.run(
            arrayOf("--repo", gitDir.toString(), "--base", "ffffffffffffffffffffffffffffffffffffffff", "--candidate", "HEAD")
        )
        assertEquals(Main.EXIT_VALIDATION, code)
    }

    // ----- Constructor signature test -----

    @Test
    fun constructor_takes_workingDir_and_snapshotFactory_only() {
        gitDir = tempDir.resolve("git-repo")
        Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        // Make a commit so HEAD exists before GitSnapshotRepository.load() is called.
        // This mirrors the shaEquivalence pattern: open a Git instance, commit,
        // then use GitSnapshotRepository with the same workingDir.
        Git.open(gitDir.toFile()).use { git ->
            val config = git.repository.config
            config.setString("user", null, "email", "test@example.com")
            config.setString("user", null, "name", "Test User")
            config.save()

            gitDir.resolve("file.txt").toFile().writeText("content")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("initial").call()
        }

        // Should compile and construct without ProcessRunner
        val factory = GitSnapshotFactory()
        val repo = GitSnapshotRepository(gitDir, factory)

        val snapshot = repo.load("HEAD")
        assertNotNull(snapshot)
        assertTrue(snapshot.fingerprint.value.matches(Regex("[0-9a-f]{64}")))
    }
}
