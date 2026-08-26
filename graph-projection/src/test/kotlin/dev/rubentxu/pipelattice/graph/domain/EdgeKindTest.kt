package dev.rubentxu.pipelattice.graph.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class EdgeKindTest {

    @Test
    fun `EdgeKind sealed interface has 5 data object variants`() {
        val kinds = listOf(
            EdgeKind.IMPORTS,
            EdgeKind.EXTENDS,
            EdgeKind.SELECTS,
            EdgeKind.OVERRIDES,
            EdgeKind.PATCHES,
        )
        assertEquals(5, kinds.size)
    }

    @Test
    fun `EdgeKind variants are singletons`() {
        assertEquals(EdgeKind.IMPORTS, EdgeKind.IMPORTS)
        assertEquals(EdgeKind.EXTENDS, EdgeKind.EXTENDS)
        assertEquals(EdgeKind.SELECTS, EdgeKind.SELECTS)
        assertEquals(EdgeKind.OVERRIDES, EdgeKind.OVERRIDES)
        assertEquals(EdgeKind.PATCHES, EdgeKind.PATCHES)
    }

    @Test
    fun `EdgeKind is exhaustive in when expression`() {
        val kind: EdgeKind = EdgeKind.IMPORTS
        val result = when (kind) {
            EdgeKind.IMPORTS -> "imports"
            EdgeKind.EXTENDS -> "extends"
            EdgeKind.SELECTS -> "selects"
            EdgeKind.OVERRIDES -> "overrides"
            EdgeKind.PATCHES -> "patches"
            EdgeKind.DERIVED_FROM -> "derived_from"
            EdgeKind.USES -> "uses"
            EdgeKind.REQUIRES -> "requires"
            EdgeKind.PROVIDES -> "provides"
            EdgeKind.GOVERNED_BY -> "governed_by"
            EdgeKind.TARGETS -> "targets"
            EdgeKind.PRODUCES -> "produces"
            EdgeKind.CONSUMES -> "consumes"
            EdgeKind.COMPILES_TO -> "compiles_to"
        }
        assertEquals("imports", result)
    }
}
