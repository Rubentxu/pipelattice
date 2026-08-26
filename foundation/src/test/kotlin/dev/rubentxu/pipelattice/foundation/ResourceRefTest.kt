package dev.rubentxu.pipelattice.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class ResourceRefTest {

    @Test
    fun `parses ref with version`() {
        val ref = ResourceRef.parse("catalog://build/maven@4")
        assertEquals(ResourceRef(path = "build/maven", version = "4"), ref)
    }

    @Test
    fun `parses channel-style version`() {
        val ref = ResourceRef.parse("catalog://profiles/java-maven-container@stable")
        assertEquals("stable", ref.version)
    }

    @Test
    fun `parses ref without version`() {
        val ref = ResourceRef.parse("catalog://company/base")
        assertEquals("company/base", ref.path)
        assertNull(ref.version)
    }

    @Test
    fun `canonical form round-trips through parse`() {
        val original = ResourceRef(path = "language/java", version = "5")
        assertEquals(original, ResourceRef.parse(original.canonicalForm))
    }

    @Test
    fun `canonical form omits absent version`() {
        assertEquals(
            "catalog://security/standard",
            ResourceRef(path = "security/standard").canonicalForm,
        )
    }

    @Test
    fun `rejects foreign schemes and empty input`() {
        assertFailsWith<IllegalArgumentException> { ResourceRef.parse("https://build/maven") }
        assertFailsWith<IllegalArgumentException> { ResourceRef.parse("catalog://") }
    }

    @Test
    fun `rejects at sign inside path when constructed directly`() {
        assertFailsWith<IllegalArgumentException> { ResourceRef(path = "build@maven") }
    }

    @Test
    fun `rejects blank path`() {
        assertFailsWith<IllegalArgumentException> { ResourceRef(path = " ") }
    }
}
