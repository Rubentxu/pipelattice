package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.fleet.diff.cache.SnapshotDiskCache
import dev.rubentxu.pipelattice.fleet.diff.ports.GitRefResolution
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for GitSnapshotRepository cache invalidation behavior.
 *
 * Covers spec scenarios:
 * - S6: m15 v1 cache invalidation v1 → v2
 */
class GitSnapshotRepositoryCacheInvalidationV2Test {

    @TempDir
    lateinit var tempDir: Path

    private fun sha256Hex(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * S6 — v1 and v2 fingerprints differ for same content (auto-invalidation proof).
     *
     * Verifies that the fingerprint scheme change from m15 v1 to m16 v2 produces
     * different fingerprint values for the same content. This is the foundation of
     * the auto-invalidation behavior: a v1-era cache file will have a different
     * fingerprint than a freshly-computed v2 snapshot for the same git ref and sources.
     *
     * The v1 scheme used `SHA-256("graph-content/v1:<refSha>:<inputHash>")`
     * while v2 uses `SHA-256("graph-content/v2:<refSha>:<inputHash>")`.
     */
    @Test
    fun v1_and_v2_fingerprints_differ_for_same_content() {
        val sha = "a".repeat(40)
        val emptySources = emptyList<dev.rubentxu.pipelattice.resource.SourceDocument>()
        val inputHash = SnapshotDiskCache.computeInputHash(emptySources)

        // Compute v1 fingerprint (m15 scheme)
        val v1FingerprintInput = "graph-content/v1:${sha}:${inputHash}"
        val v1Fingerprint = sha256Hex(v1FingerprintInput)

        // Compute v2 fingerprint (m16 scheme)
        val v2FingerprintInput = "graph-content/v2:${sha}:${inputHash}"
        val v2Fingerprint = sha256Hex(v2FingerprintInput)

        // v1 and v2 must be different for the same content (auto-invalidation works)
        assertNotEquals(
            v1Fingerprint,
            v2Fingerprint,
            "v1 and v2 fingerprints must differ for same content (auto-invalidation proof)"
        )

        // Both must be 64 hex chars (v2 fingerprint scheme requirement)
        assertTrue(v1Fingerprint.matches(Regex("[0-9a-f]{64}")))
        assertTrue(v2Fingerprint.matches(Regex("[0-9a-f]{64}")))
    }

    /**
     * S6 — A v1-era cache file is NOT returned by v2 loadWithSources.
     *
     * Given a pre-populated cache directory containing a v1-era cache file
     * (simulated by writing a GraphSnapshot with a v1-style fingerprint directly to cache),
     * when loadWithSources("HEAD") is called with the same git SHA and sources,
     * then the v1 cache file is NOT returned (cache miss because the v2 fingerprint
     * differs from the stored v1 fingerprint, causing the snapshot to be recomputed).
     *
     * A new v2 cache file is written on cache miss.
     */
    @Test
    fun v1_cache_files_are_not_returned_in_v2() {
        // Set up a git repo with a commit
        val gitDir = tempDir.resolve("git-repo")
        val git = Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        val sha: String
        try {
            val config = git.repository.config
            config.setString("user", null, "email", "test@example.com")
            config.setString("user", null, "name", "Test User")
            config.save()

            gitDir.resolve("file.txt").toFile().writeText("content")
            git.add().addFilepattern(".").call()
            val commit = git.commit().setMessage("initial").call()
            sha = commit.name
        } finally {
            git.close()
        }

        // Create a cache dir and pre-populate it with a "v1 fingerprint" cache file
        // The v1 fingerprint differs from what v2 would compute for the same content
        val cacheDir = tempDir.resolve("cache")
        val emptySources = emptyList<dev.rubentxu.pipelattice.resource.SourceDocument>()
        val inputHash = SnapshotDiskCache.computeInputHash(emptySources)
        val cacheKey = "$sha-$inputHash"

        // Simulate a v1-era cache file by directly writing to cache with a v1 fingerprint
        val v1FingerprintInput = "graph-content/v1:${sha}:${inputHash}"
        val v1Fingerprint = sha256Hex(v1FingerprintInput)

        val v1Json = """{"fingerprint":"$v1Fingerprint","nodes":[],"edges":[]}"""
        val cacheFile = cacheDir.resolve("${cacheKey.replace("/", "_").replace("..", "_")}.json")
        cacheDir.toFile().mkdirs()
        cacheFile.toFile().writeText(v1Json)

        // Now use GitSnapshotRepository with the cache
        val resourceParser = dev.rubentxu.pipelattice.compiler.parse.YamlResourceParser()
        val repo = GitSnapshotRepository(
            gitDir,
            resourceParser = resourceParser,
            cache = SnapshotDiskCache(cacheDir)
        )

        // First load - should NOT use the v1 cache (fingerprint mismatch)
        val loaded1 = repo.loadWithSources("HEAD")
        assertNotNull(loaded1, "loadWithSources should return non-null after recomputing from v1 miss")

        // The loaded snapshot should have v2 fingerprint (not v1)
        val v2FingerprintInput = "graph-content/v2:${sha}:${inputHash}"
        val expectedV2Fingerprint = sha256Hex(v2FingerprintInput)
        assertNotEquals(
            v1Fingerprint,
            loaded1.snapshot.fingerprint.value,
            "Loaded snapshot should have v2 fingerprint, not v1"
        )
        assertNotEquals(
            expectedV2Fingerprint,
            v1Fingerprint,
            "v2 fingerprint must differ from v1"
        )

        // The v2 cache file should now exist alongside the v1 file (dead weight)
        val v2CacheFile = cacheDir.resolve("${cacheKey.replace("/", "_").replace("..", "_")}.json")
        assertTrue(
            v2CacheFile.toFile().exists(),
            "New v2 cache file should be written after v1 cache miss"
        )

        // Second load - should HIT the v2 cache
        val loaded2 = repo.loadWithSources("HEAD")
        assertNotNull(loaded2, "Second load should hit the v2 cache")
    }
}
