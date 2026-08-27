package dev.rubentxu.pipelattice.foundation.secret

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecretRefTest {

    // --- Scenario S1: parse accepts canonical secret:// ref ---

    @Test
    fun `parse accepts canonical secret ref`() {
        val ref = SecretRef.parse("secret://vault.team/registry-token")
        assertEquals("vault.team", ref.authority)
        assertEquals("registry-token", ref.key)
    }

    @Test
    fun `toString returns canonical form verbatim`() {
        val raw = "secret://vault.team/registry-token"
        val ref = SecretRef.parse(raw)
        assertEquals(raw, ref.toString())
    }

    @Test
    fun `parse roundtrip preserves identity`() {
        val raw = "secret://vault.team/registry-token"
        val ref = SecretRef.parse(raw)
        val roundtrip = SecretRef.parse(ref.toString())
        assertEquals(ref, roundtrip)
    }

    @Test
    fun `authority and key are derived correctly`() {
        val ref = SecretRef.parse("secret://my-vault/my-secret-key")
        assertEquals("my-vault", ref.authority)
        assertEquals("my-secret-key", ref.key)
    }

    // --- Scenario S2: parse rejects non-secret schemes ---

    @Test
    fun `parse rejects catalog scheme`() {
        assertFailsWith<IllegalArgumentException> {
            SecretRef.parse("catalog://vault/token")
        }
    }

    @Test
    fun `parse rejects https scheme`() {
        assertFailsWith<IllegalArgumentException> {
            SecretRef.parse("https://example/token")
        }
    }

    @Test
    fun `parse rejects ftp scheme`() {
        assertFailsWith<IllegalArgumentException> {
            SecretRef.parse("ftp://example/token")
        }
    }

    @Test
    fun `parse rejects bare-uri`() {
        assertFailsWith<IllegalArgumentException> {
            SecretRef.parse("bare-uri")
        }
    }

    @Test
    fun `parse rejects empty string`() {
        assertFailsWith<IllegalArgumentException> {
            SecretRef.parse("")
        }
    }

    // --- Scenario S3: parse rejects empty authority or empty key ---

    @Test
    fun `parse rejects empty authority`() {
        assertFailsWith<IllegalArgumentException> {
            SecretRef.parse("secret:///key")
        }
    }

    @Test
    fun `parse rejects empty key`() {
        assertFailsWith<IllegalArgumentException> {
            SecretRef.parse("secret://authority/")
        }
    }

    @Test
    fun `parse rejects no key separator`() {
        assertFailsWith<IllegalArgumentException> {
            SecretRef.parse("secret://authority")
        }
    }

    @Test
    fun `parse rejects whitespace in authority`() {
        assertFailsWith<IllegalArgumentException> {
            SecretRef.parse("secret://auth ority/key")
        }
    }

    @Test
    fun `parse rejects whitespace in key`() {
        assertFailsWith<IllegalArgumentException> {
            SecretRef.parse("secret://authority/my key")
        }
    }

    @Test
    fun `parse rejects @ in authority`() {
        assertFailsWith<IllegalArgumentException> {
            SecretRef.parse("secret://auth@ority/key")
        }
    }

    @Test
    fun `parse rejects question mark in key`() {
        assertFailsWith<IllegalArgumentException> {
            SecretRef.parse("secret://authority/key?query")
        }
    }

    @Test
    fun `parse rejects # in key`() {
        assertFailsWith<IllegalArgumentException> {
            SecretRef.parse("secret://authority/key#fragment")
        }
    }
}
