package dev.rubentxu.pipelattice.compose.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Invariants for [Layer] enum.
 *
 * Phase 2 — domain/Layer
 */
class LayerInvariantsTest {

    @Test
    fun `PROFILE_IMPORT has precedence zero`() {
        val sut = Layer.PROFILE_IMPORT
        assertEquals(0, sut.precedence)
    }

    @Test
    fun `PROFILE has precedence one`() {
        val sut = Layer.PROFILE
        assertEquals(1, sut.precedence)
    }

    @Test
    fun `LOCAL has precedence two`() {
        val sut = Layer.LOCAL
        assertEquals(2, sut.precedence)
    }

    @Test
    fun `all entries are sorted by ascending precedence`() {
        val sorted = Layer.all
        assertEquals(3, sorted.size)
        assertEquals(Layer.PROFILE_IMPORT, sorted[0])
        assertEquals(Layer.PROFILE, sorted[1])
        assertEquals(Layer.LOCAL, sorted[2])
        // Verify strictly ascending
        for (i in 0 until sorted.size - 1) {
            assertTrue(sorted[i].precedence < sorted[i + 1].precedence)
        }
    }

    @Test
    fun `all returns all three entries`() {
        val all = Layer.all
        assertEquals(3, all.size)
        assertTrue(all.contains(Layer.PROFILE_IMPORT))
        assertTrue(all.contains(Layer.PROFILE))
        assertTrue(all.contains(Layer.LOCAL))
    }
}
