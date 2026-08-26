package dev.rubentxu.pipelattice.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProjectIdTest {

    @Test
    fun `preserves value and value semantics`() {
        val id = ProjectId("payments-api")
        assertEquals("payments-api", id.value)
        assertEquals(ProjectId("payments-api"), id)
        assertEquals(ProjectId("payments-api").hashCode(), id.hashCode())
    }

    @Test
    fun `renders as raw value`() {
        assertEquals("payments-api", ProjectId("payments-api").toString())
    }

    @Test
    fun `rejects blank identifiers`() {
        val error = assertFailsWith<IllegalArgumentException> { ProjectId("   ") }
        assertTrue(error.message!!.contains("must not be blank"))
    }
}
