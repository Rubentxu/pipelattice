package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.fleet.diff.ports.GitRefResolution
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for [GitSnapshotFactory] fingerprint determinism and format.
 *
 * Covers spec scenarios:
 * - Sc09: GitSnapshotFactory fingerprint is deterministic and 64-hex
 */
class GitSnapshotFactoryTest {

    private val factory = GitSnapshotFactory()

    @Test
    fun `sameShaProducesEqualFingerprint`() {
        val sha = "a".repeat(40)
        val resolution = GitRefResolution.Resolved(sha)

        val snapshot1 = factory.create(resolution)
        val snapshot2 = factory.create(resolution)

        assertEquals(snapshot1.fingerprint, snapshot2.fingerprint)
    }

    @Test
    fun `differentShasProduceDifferentFingerprints`() {
        val sha1 = "a".repeat(40)
        val sha2 = "b".repeat(40)
        val resolution1 = GitRefResolution.Resolved(sha1)
        val resolution2 = GitRefResolution.Resolved(sha2)

        val snapshot1 = factory.create(resolution1)
        val snapshot2 = factory.create(resolution2)

        assertNotEquals(snapshot1.fingerprint.value, snapshot2.fingerprint.value)
    }

    @Test
    fun `fingerprintIs64LowerHexNotAllZeros`() {
        val sha = "a".repeat(40)
        val resolution = GitRefResolution.Resolved(sha)

        val snapshot = factory.create(resolution)

        val value = snapshot.fingerprint.value
        assertEquals(64, value.length)
        assertTrue(value.matches(Regex("[0-9a-f]{64}")), "Expected 64 lowercase hex chars, got: $value")
        assertTrue(value != "0".repeat(64), "Fingerprint must not be the all-zeros placeholder")
    }
}
