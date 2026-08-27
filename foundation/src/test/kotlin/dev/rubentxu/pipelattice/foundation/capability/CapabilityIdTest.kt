package dev.rubentxu.pipelattice.foundation.capability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class CapabilityIdTest {

    // --- Scenario: parse accepts canonical capability id ---

    @Test
    fun `parse accepts canonical scm checkout id`() {
        val id = CapabilityId.parse("scm.checkout/v1")
        assertEquals("scm.checkout/v1", id.value)
        assertEquals("scm.checkout", id.operation)
        assertEquals("v1", id.version)
    }

    @Test
    fun `parse accepts artifact publish id`() {
        val id = CapabilityId.parse("artifact.publish/v1")
        assertEquals("artifact.publish", id.operation)
        assertEquals("v1", id.version)
    }

    @Test
    fun `parse accepts release calculate id`() {
        val id = CapabilityId.parse("release.calculate/v1")
        assertEquals("release.calculate", id.operation)
        assertEquals("v1", id.version)
    }

    @Test
    fun `parse equality is stable across roundtrips`() {
        val id1 = CapabilityId.parse("scm.checkout/v1")
        val id2 = CapabilityId.parse("scm.checkout/v1")
        assertEquals(id1, id2)
        assertEquals(id1.hashCode(), id2.hashCode())
    }

    @Test
    fun `toString returns canonical form`() {
        val id = CapabilityId.parse("scm.checkout/v1")
        assertEquals("scm.checkout/v1", id.toString())
    }

    @Test
    fun `value class equality holds`() {
        val a = CapabilityId.parse("scm.checkout/v1")
        val b = CapabilityId.parse("scm.checkout/v1")
        assertEquals(a, b)
    }

    // --- Scenario: parse rejects malformed version and malformed op ---

    @Test
    fun `parse rejects empty version`() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityId.parse("scm.checkout/")
        }
    }

    @Test
    fun `parse rejects version without v prefix`() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityId.parse("scm.checkout/1")
        }
    }

    @Test
    fun `parse rejects multi-segment version`() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityId.parse("scm.checkout/v1.0")
        }
    }

    @Test
    fun `parse rejects empty op`() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityId.parse("/v1")
        }
    }

    @Test
    fun `parse rejects double dot in op`() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityId.parse("scm..checkout/v1")
        }
    }

    @Test
    fun `parse rejects multiple slash separators`() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityId.parse("scm.checkout/v1/extra")
        }
    }

    @Test
    fun `parse rejects uppercase in operation`() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityId.parse("SCM.checkout/v1")
        }
    }

    @Test
    fun `parse rejects special chars in operation`() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityId.parse("scm_checkout/v1")
        }
    }

    @Test
    fun `parse rejects blank string`() {
        assertFailsWith<IllegalArgumentException> {
            CapabilityId.parse("   ")
        }
    }

    @Test
    fun `different operations are not equal`() {
        val a = CapabilityId.parse("scm.checkout/v1")
        val b = CapabilityId.parse("scm.tag/v1")
        assertNotEquals(a, b)
    }

    @Test
    fun `different versions are not equal`() {
        val a = CapabilityId.parse("scm.checkout/v1")
        val b = CapabilityId.parse("scm.checkout/v2")
        assertNotEquals(a, b)
    }
}
