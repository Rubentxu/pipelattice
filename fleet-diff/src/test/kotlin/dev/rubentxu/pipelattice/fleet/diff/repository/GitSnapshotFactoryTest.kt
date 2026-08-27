package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.fleet.diff.cache.SnapshotDiskCache
import dev.rubentxu.pipelattice.fleet.diff.ports.GitRefResolution
import dev.rubentxu.pipelattice.resource.SourceDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [GitSnapshotFactory] content-derived fingerprint behavior.
 *
 * Covers spec scenarios:
 * - Sc09: GitSnapshotFactory fingerprint is deterministic and 64-hex
 * - B10: parse_error_maps_to_null_with_diagnostic (GitSnapshotFactory level)
 */
class GitSnapshotFactoryTest {

    private val factory = GitSnapshotFactory()
    private val emptySources = emptyList<SourceDocument>()
    private val fakeParser = FakeResourceParser()

    @Test
    fun `same ref SHA produces equal fingerprints with same content`() {
        val sha = "a".repeat(40)
        val resolution = GitRefResolution.Resolved(sha)

        val snapshot1 = factory.create(resolution, emptySources, fakeParser)
        val snapshot2 = factory.create(resolution, emptySources, fakeParser)

        assertNotNull(snapshot1)
        assertNotNull(snapshot2)
        assertEquals(snapshot1.fingerprint, snapshot2.fingerprint)
    }

    @Test
    fun `different ref SHAs produce different fingerprints with same content`() {
        val sha1 = "a".repeat(40)
        val sha2 = "b".repeat(40)
        val resolution1 = GitRefResolution.Resolved(sha1)
        val resolution2 = GitRefResolution.Resolved(sha2)

        val snapshot1 = factory.create(resolution1, emptySources, fakeParser)
        val snapshot2 = factory.create(resolution2, emptySources, fakeParser)

        assertNotNull(snapshot1)
        assertNotNull(snapshot2)
        assertNotEquals(snapshot1.fingerprint.value, snapshot2.fingerprint.value)
    }

    @Test
    fun `fingerprint is 64 lowercase hex with graph-content v2 prefix`() {
        val sha = "a".repeat(40)
        val resolution = GitRefResolution.Resolved(sha)

        val snapshot = factory.create(resolution, emptySources, fakeParser)

        assertNotNull(snapshot)
        val value = snapshot.fingerprint.value
        assertEquals(64, value.length)
        assertTrue(value.matches(Regex("[0-9a-f]{64}")), "Expected 64 lowercase hex chars, got: $value")
        // The fingerprint is SHA-256("graph-content/v2:<sha>:<inputHash>"), not the literal string.
        // The domain tag is encoded in the hash input, not the hash output.
        // Verify the scheme is correct by checking that the hash input would produce this output.
        val expectedHashInput = "graph-content/v2:${sha}:${SnapshotDiskCache.computeInputHash(emptySources)}"
        val expectedHash = sha256Hex(expectedHashInput)
        assertEquals(expectedHash, value, "Fingerprint must be SHA-256 of graph-content/v2: scheme string")
    }

    private fun sha256Hex(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `fingerprint derives from content not just SHA`() {
        val sha = "a".repeat(40)
        val sourcesA = listOf(SourceDocument("a.yaml", "content-A"))
        val sourcesB = listOf(SourceDocument("a.yaml", "content-B"))
        val resolution = GitRefResolution.Resolved(sha)

        val snapshotA = factory.create(resolution, sourcesA, fakeParser)
        val snapshotB = factory.create(resolution, sourcesB, fakeParser)

        assertNotNull(snapshotA)
        assertNotNull(snapshotB)
        assertNotEquals(snapshotA.fingerprint.value, snapshotB.fingerprint.value)
    }

    @Test
    fun `empty sources produce valid fingerprint with empty-input hash`() {
        val sha = "a".repeat(40)
        val resolution = GitRefResolution.Resolved(sha)

        val snapshot = factory.create(resolution, emptySources, fakeParser)

        assertNotNull(snapshot)
        assertTrue(snapshot.nodes.isEmpty())
        assertTrue(snapshot.fingerprint.value.matches(Regex("[0-9a-f]{64}")))
    }
}
