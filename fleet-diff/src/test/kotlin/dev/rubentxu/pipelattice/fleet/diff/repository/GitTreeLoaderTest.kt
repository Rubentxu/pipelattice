package dev.rubentxu.pipelattice.fleet.diff.repository

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevWalk
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [GitTreeLoader] JGit tree walk + YAML filtering.
 *
 * Covers spec scenarios:
 * - S5 (B7): tree walk filters YAML only
 * - S6 (B8): blob read returns UTF-8 of YAML content
 * - S3: tree walk returns sorted-by-path list
 */
class GitTreeLoaderTest {

    @TempDir
    lateinit var tempDir: Path

    private fun initJgitRepo(dir: Path, configure: (Path) -> Unit = {}): Git {
        Files.createDirectories(dir)
        val git = Git.init()
            .setDirectory(dir.toFile())
            .call()
        val config = git.repository.config
        config.setString("user", null, "email", "test@example.com")
        config.setString("user", null, "name", "Test User")
        config.save()
        configure(dir)
        git.add().addFilepattern(".").call()
        git.commit().setMessage("initial").call()
        return git
    }

    private fun Path.writeFile(relativePath: String, content: String) {
        val file = this.resolve(relativePath)
        file.parent.toFile().mkdirs()
        file.toFile().writeText(content)
    }

    @Test
    fun `tree walk filters YAML only`() {
        val gitDir = tempDir.resolve("git-repo")
        initJgitRepo(gitDir) {
            it.writeFile("pipelines/build.yaml", "apiVersion: v1\nkind: PipelineDefinition")
            it.writeFile("docs/README.md", "# Readme")
            it.writeFile("data/binary.bin", String(byteArrayOf(0, 1, 2, 3)))
            it.writeFile(".gitignore", "*.class")
        }

        Git.open(gitDir.toFile()).use { git ->
            val repo = git.repository
            val resolved = repo.resolve("HEAD")
            RevWalk(repo).use { revWalk ->
                val commit = revWalk.parseCommit(resolved!!)
                val loader = GitTreeLoader(repo)
                val sources = loader.loadSources(commit)

                assertEquals(1, sources.size, "Only .yaml file should be returned")
                assertEquals("pipelines/build.yaml", sources[0].path)
            }
        }
    }

    @Test
    fun `tree walk returns sorted by path`() {
        val gitDir = tempDir.resolve("git-repo")
        initJgitRepo(gitDir) {
            it.writeFile("z.yaml", "apiVersion: v1\nkind: PipelineDefinition")
            it.writeFile("a.yaml", "apiVersion: v1\nkind: PipelineDefinition")
            it.writeFile("m.yaml", "apiVersion: v1\nkind: PipelineDefinition")
        }

        Git.open(gitDir.toFile()).use { git ->
            val repo = git.repository
            val resolved = repo.resolve("HEAD")
            RevWalk(repo).use { revWalk ->
                val commit = revWalk.parseCommit(resolved!!)
                val loader = GitTreeLoader(repo)
                val sources = loader.loadSources(commit)

                assertEquals(3, sources.size)
                assertEquals("a.yaml", sources[0].path)
                assertEquals("m.yaml", sources[1].path)
                assertEquals("z.yaml", sources[2].path)
            }
        }
    }

    @Test
    fun `blob read returns UTF-8 with non-ASCII`() {
        val gitDir = tempDir.resolve("git-repo")
        val content = "apiVersion: v1\nkind: PipelineDefinition\nmetadata:\n  name: pipeline-ñ" // ñ is UTF-8
        initJgitRepo(gitDir) {
            it.writeFile("pipelines/build.yaml", content)
        }

        Git.open(gitDir.toFile()).use { git ->
            val repo = git.repository
            val resolved = repo.resolve("HEAD")
            RevWalk(repo).use { revWalk ->
                val commit = revWalk.parseCommit(resolved!!)
                val loader = GitTreeLoader(repo)
                val sources = loader.loadSources(commit)

                assertEquals(1, sources.size)
                assertEquals(content, sources[0].content, "UTF-8 content must round-trip byte-identically")
                assertContentEquals(
                    content.toByteArray(Charsets.UTF_8),
                    sources[0].content.toByteArray(Charsets.UTF_8)
                )
            }
        }
    }

    @Test
    fun `tree walk skips git directory`() {
        val gitDir = tempDir.resolve("git-repo")
        initJgitRepo(gitDir) {
            it.writeFile("pipelines/build.yaml", "apiVersion: v1\nkind: PipelineDefinition")
        }

        Git.open(gitDir.toFile()).use { git ->
            val repo = git.repository
            val resolved = repo.resolve("HEAD")
            RevWalk(repo).use { revWalk ->
                val commit = revWalk.parseCommit(resolved!!)
                val loader = GitTreeLoader(repo)
                val sources = loader.loadSources(commit)

                assertTrue(sources.none { it.path.contains(".git/") }, "Should not contain .git/ paths")
            }
        }
    }
}
