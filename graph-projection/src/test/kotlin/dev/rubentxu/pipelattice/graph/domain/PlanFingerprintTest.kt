package dev.rubentxu.pipelattice.graph.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlanFingerprintTest {

    @Test
    fun `PlanFingerprint accepts valid 64-char hex digest`() {
        val fingerprint = PlanFingerprint("0".repeat(64))
        assertEquals(64, fingerprint.value.length)
        assertEquals("0".repeat(64), fingerprint.value)
    }

    @Test
    fun `PlanFingerprint accepts mixed hex digest`() {
        // 64-char lowercase hex string
        val digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val fingerprint = PlanFingerprint(digest)
        assertEquals(64, fingerprint.value.length)
        assertTrue(fingerprint.value.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `PlanFingerprint rejects blank string`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            PlanFingerprint("")
        }
        assertEquals("PlanFingerprint must not be blank", exception.message)
    }

    @Test
    fun `PlanFingerprint rejects 63-char string`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            PlanFingerprint("a".repeat(63))
        }
        assertEquals("PlanFingerprint must be a 64-char SHA-256 hex digest", exception.message)
    }

    @Test
    fun `PlanFingerprint rejects 65-char string`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            PlanFingerprint("a".repeat(65))
        }
        assertEquals("PlanFingerprint must be a 64-char SHA-256 hex digest", exception.message)
    }

    @Test
    fun `PlanFingerprint rejects non-hex characters`() {
        val invalid = "g".repeat(64) // 'g' is not a valid hex char
        val exception = assertFailsWith<IllegalArgumentException> {
            PlanFingerprint(invalid)
        }
        assertEquals("PlanFingerprint must be a valid 64-char SHA-256 hex digest", exception.message)
    }

    @Test
    fun `PlanFingerprint rejects uppercase hex`() {
        val upper = "A".repeat(64)
        val exception = assertFailsWith<IllegalArgumentException> {
            PlanFingerprint(upper)
        }
        assertEquals("PlanFingerprint must be a valid 64-char SHA-256 hex digest", exception.message)
    }
}
