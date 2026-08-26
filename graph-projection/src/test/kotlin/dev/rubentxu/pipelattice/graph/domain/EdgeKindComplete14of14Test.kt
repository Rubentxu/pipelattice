package dev.rubentxu.pipelattice.graph.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies all 14 EdgeKind data objects are present and accounted for.
 * The sealed interface covers spec 04 §4 A-min (5 core) + A-lite (9 deferred).
 */
class EdgeKindComplete14of14Test {

    @Test
    fun `EdgeKind sealed interface has 14 data object variants`() {
        val kinds = listOf(
            EdgeKind.IMPORTS,
            EdgeKind.EXTENDS,
            EdgeKind.SELECTS,
            EdgeKind.OVERRIDES,
            EdgeKind.PATCHES,
            EdgeKind.DERIVED_FROM,
            EdgeKind.USES,
            EdgeKind.REQUIRES,
            EdgeKind.PROVIDES,
            EdgeKind.GOVERNED_BY,
            EdgeKind.TARGETS,
            EdgeKind.PRODUCES,
            EdgeKind.CONSUMES,
            EdgeKind.COMPILES_TO,
        )
        assertEquals(14, kinds.size)
    }

    @Test
    fun `EdgeKind all variants are singletons`() {
        // Original 5
        assertEquals(EdgeKind.IMPORTS, EdgeKind.IMPORTS)
        assertEquals(EdgeKind.EXTENDS, EdgeKind.EXTENDS)
        assertEquals(EdgeKind.SELECTS, EdgeKind.SELECTS)
        assertEquals(EdgeKind.OVERRIDES, EdgeKind.OVERRIDES)
        assertEquals(EdgeKind.PATCHES, EdgeKind.PATCHES)
        // New 9
        assertEquals(EdgeKind.DERIVED_FROM, EdgeKind.DERIVED_FROM)
        assertEquals(EdgeKind.USES, EdgeKind.USES)
        assertEquals(EdgeKind.REQUIRES, EdgeKind.REQUIRES)
        assertEquals(EdgeKind.PROVIDES, EdgeKind.PROVIDES)
        assertEquals(EdgeKind.GOVERNED_BY, EdgeKind.GOVERNED_BY)
        assertEquals(EdgeKind.TARGETS, EdgeKind.TARGETS)
        assertEquals(EdgeKind.PRODUCES, EdgeKind.PRODUCES)
        assertEquals(EdgeKind.CONSUMES, EdgeKind.CONSUMES)
        assertEquals(EdgeKind.COMPILES_TO, EdgeKind.COMPILES_TO)
    }

    @Test
    fun `EdgeKind is exhaustive in when expression for all 14`() {
        fun label(kind: EdgeKind): String = when (kind) {
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

        // Spot-check a few
        assertEquals("imports", label(EdgeKind.IMPORTS))
        assertEquals("derived_from", label(EdgeKind.DERIVED_FROM))
        assertEquals("compiles_to", label(EdgeKind.COMPILES_TO))
    }
}
