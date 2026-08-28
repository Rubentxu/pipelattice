package dev.rubentxu.pipelattice.release.adapter.scm

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.lib.Repository
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Diagnostic test to understand bare repo ref resolution.
 */
class JGitDiagnosticTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun diagnoseBareRepoRefResolution() {
        val bareDir = tempDir.resolve("bare.git")
        Files.createDirectories(bareDir)

        println("=== Creating bare repo at $bareDir ===")
        Git.init().setDirectory(bareDir.toFile()).setBare(true).call().use { bareGit ->
            bareGit.repository.config.setString("user", null, "email", "test@example.com")
            bareGit.repository.config.setString("user", null, "name", "Test User")
            bareGit.repository.config.save()
        }

        println("\n=== Opening with FileRepositoryBuilder ===")
        val repo: Repository = FileRepositoryBuilder()
            .setGitDir(bareDir.toFile())
            .build()
        println("Repo class: ${repo.javaClass.name}")
        println("IsBare: ${repo.isBare}")

        println("\n=== RefDatabase refs BEFORE clone ===")
        val refDb = repo.refDatabase
        val refsBefore = refDb.refs
        println("Ref count: ${refsBefore.size}")
        for (ref in refsBefore) {
            println("  ${ref.name}")
        }

        println("\n=== Resolving refs/heads/main BEFORE clone ===")
        try {
            val objectId = repo.resolve("refs/heads/main")
            println("Resolved: $objectId")
        } catch (e: Exception) {
            println("FAILED: ${e.javaClass.name}: ${e.message}")
        }

        val workDir = tempDir.resolve("work")
        println("\n=== Cloning from bare repo ===")
        Git.cloneRepository()
            .setURI("file://$bareDir")
            .setDirectory(workDir.toFile())
            .call()
            .use { workGit ->
                val workRepo = workGit.repository
                println("Work dir refs after clone:")
                for (ref in workRepo.refDatabase.refs) {
                    println("  ${ref.name}")
                }

                println("\n=== Creating commit in work dir ===")
                Files.writeString(workDir.resolve("file.txt"), "content")
                workGit.add().addFilepattern(".").call()
                workGit.commit().setMessage("test commit").call()

                println("Work dir refs after commit:")
                for (ref in workRepo.refDatabase.refs) {
                    println("  ${ref.name}")
                }

                println("\n=== Pushing to bare repo ===")
                workGit.push().setPushAll().call()
                println("Pushed")
            }

        println("\n=== RefDatabase refs AFTER push ===")
        val refsAfter = refDb.refs
        println("Ref count: ${refsAfter.size}")
        for (ref in refsAfter) {
            println("  ${ref.name}")
        }

        println("\n=== Resolving refs/heads/main AFTER push ===")
        try {
            val objectId = repo.resolve("refs/heads/main")
            println("Resolved: $objectId")
        } catch (e: Exception) {
            println("FAILED: ${e.javaClass.name}: ${e.message}")
        }

        println("\n=== Done. Temp dir: $tempDir ===")
    }
}
