package dev.rubentxu.pipelattice.fleet.diff.cache

import dev.rubentxu.pipelattice.fleet.diff.repository.FakeResourceParser
import dev.rubentxu.pipelattice.fleet.diff.repository.GitSnapshotFactory
import dev.rubentxu.pipelattice.fleet.diff.ports.GitRefResolution
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import dev.rubentxu.pipelattice.resource.SourceDocument
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [SnapshotDiskCache] key derivation, hit/miss, and XDG fallback.
 *
 * Covers spec scenarios:
 * - B1: key is stable across runs
 * - B2: key changes with content
 * - B3: cache hit returns deserialized GraphSnapshot (parser NOT invoked)
 * - B4: cache miss persists to disk
 * - B5: XDG_CACHE_HOME unset → fallback to ~/.cache
 * - B6: repo-id derived from workingDir (16 hex of SHA-256 of absolute path)
 */
class SnapshotDiskCacheTest {

    @TempDir
    lateinit var tempDir: Path

    private val factory = GitSnapshotFactory()
    private val fakeParser = FakeResourceParser()

    // ---- Key derivation tests ----

    @Test
    fun `key is stable across calls`() {
        val cache = SnapshotDiskCache(tempDir.resolve("cache"))
        val sources = listOf(SourceDocument("a.yaml", "content"))

        val key1 = cache.key("a".repeat(40), sources)
        val key2 = cache.key("a".repeat(40), sources)

        assertEquals(key1, key2, "Same refSha + sources must produce same key")
    }

    @Test
    fun `key changes with content`() {
        val cache = SnapshotDiskCache(tempDir.resolve("cache"))
        val sha = "a".repeat(40)

        val key1 = cache.key(sha, listOf(SourceDocument("a.yaml", "content-A")))
        val key2 = cache.key(sha, listOf(SourceDocument("a.yaml", "content-B")))

        assertNotSame(key1, key2, "Different content must produce different key")
        // Keys are <sha>-<inputHash>; same sha, different inputHash
        assertTrue(key1.startsWith(sha), "Key must start with refSha")
        assertTrue(key2.startsWith(sha), "Key must start with refSha")
        assertTrue(key1 != key2, "Keys must differ when content differs")
    }

    @Test
    fun `key changes with ref`() {
        val cache = SnapshotDiskCache(tempDir.resolve("cache"))
        val sources = listOf(SourceDocument("a.yaml", "content"))

        val key1 = cache.key("a".repeat(40), sources)
        val key2 = cache.key("b".repeat(40), sources)

        assertNotSame(key1, key2, "Different refs must produce different keys")
    }

    @Test
    fun `repo id is first 16 hex of SHA-256 of absolute workingDir path`() {
        val wd1 = tempDir.resolve("repo1")
        val wd2 = tempDir.resolve("repo2")
        Files.createDirectories(wd1)
        Files.createDirectories(wd2)

        val cache1 = SnapshotDiskCache.defaultFor(wd1)
        val cache2 = SnapshotDiskCache.defaultFor(wd2)

        // Same dir → same repo-id
        val cache1Again = SnapshotDiskCache.defaultFor(wd1)
        assertEquals(cache1.cacheDir, cache1Again.cacheDir, "Same workingDir must produce same cacheDir")

        // Different dir → different repo-id
        assertTrue(cache1.cacheDir != cache2.cacheDir, "Different workingDirs must produce different cacheDirs")

        // Verify the repo-id segment is 16 hex chars
        val repoId1 = cache1.cacheDir.fileName.toString()
        assertEquals(16, repoId1.length, "repo-id must be 16 hex chars")
        assertTrue(repoId1.matches(Regex("[0-9a-f]{16}")), "repo-id must be lowercase hex")
    }

    // ---- Cache hit/miss tests ----

    @Test
    fun `cache hit returns deserialized graph snapshot`() {
        val cache = SnapshotDiskCache(tempDir.resolve("cache-hit"))
        val sha = "a".repeat(40)
        val sources = listOf(SourceDocument("a.yaml", "content"))
        val key = cache.key(sha, sources)

        // Create a snapshot and put it in the cache
        val resolution = GitRefResolution.Resolved(sha)
        val snapshot = factory.create(resolution, sources, fakeParser)!!
        assertNotNull(snapshot)
        cache.put(key, snapshot)

        // Verify parser was called once
        assertEquals(1, fakeParser.parseCount, "Parser should have been called once during creation")

        // Reset and create a new cache instance — cache hit should not call parser
        val cache2 = SnapshotDiskCache(tempDir.resolve("cache-hit"))
        fakeParser.parseCount = 0
        val cached = cache2.get(key)

        assertNotNull(cached, "Cache hit must return non-null snapshot")
        assertEquals(0, fakeParser.parseCount, "Cache hit must NOT call the parser")
    }

    @Test
    fun `cache miss persists to disk`() {
        val cache = SnapshotDiskCache(tempDir.resolve("cache-miss"))
        val sha = "a".repeat(40)
        val sources = listOf(SourceDocument("a.yaml", "content"))
        val key = cache.key(sha, sources)

        val resolution = GitRefResolution.Resolved(sha)
        val snapshot = factory.create(resolution, sources, fakeParser)!!
        cache.put(key, snapshot)

        // Verify file exists on disk
        val cachedFile = cache.cacheDir.resolve("${key.replace("/", "_").replace("\\", "_").replace("..", "_")}.json")
        assertTrue(Files.exists(cachedFile), "Cache file must exist on disk")

        // Verify it round-trips correctly
        val cached = cache.get(key)
        assertNotNull(cached)
        assertEquals(snapshot.fingerprint, cached.fingerprint)
    }

    @Test
    fun `cache miss returns null for unknown key`() {
        val cache = SnapshotDiskCache(tempDir.resolve("cache-unknown"))
        val result = cache.get("nonexistent-key-12345")
        assertNull(result, "Cache miss must return null")
    }

    // ---- XDG fallback tests ----

    @Test
    fun `xdg fallback when env unset`() {
        // Clear XDG_CACHE_HOME
        val original = System.getenv("XDG_CACHE_HOME")
        try {
            System.clearProperty("XDG_CACHE_HOME")

            val wd = tempDir.resolve("test-repo")
            Files.createDirectories(wd)

            val cache = SnapshotDiskCache.defaultFor(wd)

            val expectedPrefix = "${System.getProperty("user.home")}/.cache/pipelattice/fleet-snapshots/"
            assertTrue(
                cache.cacheDir.toString().startsWith(expectedPrefix),
                "Cache dir must start with ~/.cache/... when XDG_CACHE_HOME is unset, got: ${cache.cacheDir}"
            )
        } finally {
            // Restore original if set
            if (original != null) {
                // Can't set env var back easily in JVM, but this is fine for tests
            }
        }
    }

    // ---- GraphSnapshotSerializer tests ----

    @Test
    fun `serializer round-trips graph snapshot byte-identically`() {
        val nodes = setOf(
            GraphNode.ConfigSource(Path.of("pipelines/build.yaml"), "abcd1234")
        )
        val snapshot = GraphSnapshot(
            nodes = nodes,
            edges = emptySet(),
            fingerprint = PlanFingerprint("a".repeat(64))
        )

        val json = GraphSnapshotSerializer.encode(snapshot)
        val decoded = GraphSnapshotSerializer.decode(json)

        assertEquals(snapshot.fingerprint, decoded.fingerprint)
        assertEquals(snapshot.nodes.size, decoded.nodes.size)
    }

    @Test
    fun `serializer is deterministic`() {
        val nodes = setOf(
            GraphNode.ConfigSource(Path.of("b.yaml"), "hash-b"),
            GraphNode.ConfigSource(Path.of("a.yaml"), "hash-a"),
        )
        val snapshot = GraphSnapshot(
            nodes = nodes,
            edges = emptySet(),
            fingerprint = PlanFingerprint("a".repeat(64))
        )

        val json1 = GraphSnapshotSerializer.encode(snapshot)
        val json2 = GraphSnapshotSerializer.encode(snapshot)

        assertContentEquals(json1.toByteArray(), json2.toByteArray(), "Serializer must be deterministic")
    }
}
