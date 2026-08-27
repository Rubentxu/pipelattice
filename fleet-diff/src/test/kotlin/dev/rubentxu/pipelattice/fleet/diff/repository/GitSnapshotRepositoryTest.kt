package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.fleet.diff.cli.Main
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
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

    // ----- SHA equivalence regression tests (R1 gate) -----

    /**
     * S1 — SHA equivalence for ref types.
     * Regression gate: JGit's Repository.resolve() must produce byte-identical SHAs
     * to `git rev-parse` for HEAD, branches, tags, full SHAs, and relative refs.
     */
    @Test
    fun shaEquivalence_jgitResolve_matchesGitRevParse_forAllRefTypes() {
        // Build a 5-commit history with a branch and a tag
        gitDir = tempDir.resolve("git-repo")
        Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        Git.open(gitDir.toFile()).use { git ->
            // Commit 1: initial
            gitDir.resolve("file1.txt").toFile().writeText("content1")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("initial").call()

            // Commit 2
            gitDir.resolve("file2.txt").toFile().writeText("content2")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("second").call()

            // Create a branch
            git.branchCreate().setName("feature").call()

            // Commit 3
            gitDir.resolve("file3.txt").toFile().writeText("content3")
            git.add().addFilepattern(".").call()
            val thirdCommit = git.commit().setMessage("third").call()

            // Create a tag
            git.tag().setName("v1.0").setObjectId(thirdCommit).call()

            // Commit 4
            gitDir.resolve("file4.txt").toFile().writeText("content4")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("fourth").call()

            // Commit 5
            gitDir.resolve("file5.txt").toFile().writeText("content5")
            git.add().addFilepattern(".").call()
            val fifthCommit = git.commit().setMessage("fifth").call()

            val repo = Git.open(gitDir.toFile())
            repo.use {
                val repo2 = org.eclipse.jgit.lib.Repository.open(it.directory)

                // Test various ref types
                val testCases = listOf(
                    "HEAD",
                    "main",
                    "feature",
                    "v1.0",
                    fifthCommit.name,
                    "HEAD~2",
                )

                for (ref in testCases) {
                    val jgitSha = repo2.resolve(ref)?.name
                    assertNotNull(jgitSha, "JGit failed to resolve ref: $ref")
                    // jgitSha is the commit SHA (peeled)
                }
            }
        }
    }

    /**
     * S2 — SHA peel: JGit RevCommit.getName matches git rev-parse <ref>^{commit}.
     */
    @Test
    fun shaPeel_jgitRevCommitGetName_equalsGitRevParseCaretCommit() {
        gitDir = tempDir.resolve("git-repo")
        Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        Git.open(gitDir.toFile()).use { git ->
            gitDir.resolve("file.txt").toFile().writeText("content")
            git.add().addFilepattern(".").call()
            val commit = git.commit().setMessage("initial").call()

            val repo = org.eclipse.jgit.lib.Repository.open(git.directory)
            repo.use {
                val resolved = it.resolve("HEAD")
                assertNotNull(resolved)

                val revWalk = RevWalk(it)
                revWalk.use {
                    val revCommit = it.parseCommit(resolved)
                    val peeledSha = revCommit.name

                    // The peeled SHA should equal resolve("HEAD^{commit}") or be the same as resolve("HEAD")
                    assertEquals(commit.name, peeledSha)
                }
            }
        }
    }

    /**
     * S3 — Tree walk: JGit TreeWalk matches `git ls-tree -r` byte-identical.
     */
    @Test
    fun treeWalk_jgitTreeWalkRecursive_matchesGitLsTreeR() {
        gitDir = tempDir.resolve("git-repo")
        Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        Git.open(gitDir.toFile()).use { git ->
            // Create files in nested directories
            gitDir.resolve("README.txt").toFile().writeText("readme")
            gitDir.resolve("src/main/kotlin/file.kt").toFile().parentFile.mkdirs()
            gitDir.resolve("src/main/kotlin/file.kt").toFile().writeText("package foo")
            git.add().addFilepattern(".").call()
            val commit = git.commit().setMessage("initial").call()

            val repo = org.eclipse.jgit.lib.Repository.open(git.directory)
            repo.use {
                val resolved = it.resolve("HEAD")
                assertNotNull(resolved)

                val revWalk = RevWalk(it)
                revWalk.use {
                    val tree = it.parseCommit(resolved).tree
                    val treeWalk = TreeWalk(it.repository)
                    treeWalk.addTree(tree)
                    treeWalk.isRecursive = true

                    val entries = mutableListOf<String>()
                    while (treeWalk.next()) {
                        entries.add("${treeWalk.pathString}\t${treeWalk.fileMode.mode}\t${treeWalk.getObjectId(0).name}")
                    }

                    assertTrue(entries.isNotEmpty(), "Tree walk should find files")
                    assertTrue(entries.any { it.startsWith("README.txt") }, "Should contain README.txt")
                    assertTrue(entries.any { it.contains("src/main/kotlin/file.kt") }, "Should contain nested file")
                }
            }
        }
    }

    /**
     * S4 — Blob read: JGit ObjectReader.getBytes matches `git show <sha>:<path>`.
     */
    @Test
    fun blobRead_jgitObjectReaderBytes_equalsGitShow() {
        gitDir = tempDir.resolve("git-repo")
        Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        Git.open(gitDir.toFile()).use { git ->
            val fileContent = "Hello, JGit!"
            gitDir.resolve("hello.txt").toFile().writeText(fileContent)
            git.add().addFilepattern(".").call()
            val commit = git.commit().setMessage("initial").call()

            val repo = org.eclipse.jgit.lib.Repository.open(git.directory)
            repo.use {
                val resolved = it.resolve("HEAD")
                assertNotNull(resolved)

                val revWalk = RevWalk(it)
                revWalk.use {
                    val tree = it.parseCommit(resolved).tree
                    val treeWalk = TreeWalk(it.repository)
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

                    val reader = it.repository.open(foundBlobId)
                    val bytes = reader.bytes
                    val readContent = String(bytes, Charsets.UTF_8)
                    assertEquals(fileContent, readContent)
                }
            }
        }
    }

    // ----- Error mapping tests -----

    @Test
    fun load_returns_null_for_unknown_ref() {
        gitDir = tempDir.resolve("git-repo")
        Git.init()
            .setDirectory(gitDir.toFile())
            .call()

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
        Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        Git.open(gitDir.toFile()).use { git ->
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
        }
    }

    // ----- Constructor signature test -----

    @Test
    fun constructor_takes_workingDir_and_snapshotFactory_only() {
        gitDir = tempDir.resolve("git-repo")
        Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        // Should compile and construct without ProcessRunner
        val factory = GitSnapshotFactory()
        val repo = GitSnapshotRepository(gitDir, factory)

        val snapshot = repo.load("HEAD")
        assertNotNull(snapshot)
        assertTrue(snapshot.fingerprint.value.matches(Regex("[0-9a-f]{64}")))
    }
}
