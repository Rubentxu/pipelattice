package dev.rubentxu.pipelattice.compose.domain

import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
import dev.rubentxu.pipelattice.resource.ParameterValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Invariants for [Provenance], [ProvenanceSource], and [Transformation].
 *
 * Phase 2 — domain/Provenance
 */
class ProvenanceInvariantsTest {

    // --- ProvenanceSource ---

    @Test
    fun `ProvenanceSource accepts valid fields`() {
        val resource = ResourceRef.parse("catalog://test/profile")
        val location = SourceLocation(path = "p.yaml", line = 1, column = 1)
        val humanForm = ResourceRef.parse("catalog://human/form")
        val sut = ProvenanceSource(resource = resource, location = location, humanForm = humanForm)
        assertEquals(resource, sut.resource)
        assertEquals(location, sut.location)
        assertEquals(humanForm, sut.humanForm)
    }

    @Test
    fun `ProvenanceSource accepts null humanForm`() {
        val resource = ResourceRef.parse("catalog://test/profile")
        val location = SourceLocation(path = "p.yaml", line = 1, column = 1)
        val sut = ProvenanceSource(resource = resource, location = location, humanForm = null)
        assertNull(sut.humanForm)
    }

    // --- Transformation ---

    @Test
    fun `Transformation accepts non-blank kind`() {
        val sut = Transformation(kind = "REQUESTED_AS", detail = "user request")
        assertEquals("REQUESTED_AS", sut.kind)
        assertEquals("user request", sut.detail)
    }

    @Test
    fun `Transformation rejects blank kind`() {
        for (blank in listOf("", "   ", "\t")) {
            assertFailsWith<IllegalArgumentException>("kind '$blank' must throw") {
                Transformation(kind = blank, detail = "x")
            }
        }
    }

    // --- Transformation companion constants ---

    @Test
    fun `Transformation companion exposes all reserved kinds`() {
        assertEquals("REQUESTED_AS", Transformation.REQUESTED_AS)
        assertEquals("IMPORTED_BY", Transformation.IMPORTED_BY)
        assertEquals("SELECTED_BY", Transformation.SELECTED_BY)
        assertEquals("OVERRIDDEN_BY", Transformation.OVERRIDDEN_BY)
        assertEquals("PROVIDED_BY", Transformation.PROVIDED_BY)
    }

    @Test
    fun `Transformation companion kinds are non-blank`() {
        for (kind in listOf(
            Transformation.REQUESTED_AS,
            Transformation.IMPORTED_BY,
            Transformation.SELECTED_BY,
            Transformation.OVERRIDDEN_BY,
            Transformation.PROVIDED_BY
        )) {
            assertNotNull(kind)
            assert(kind.isNotBlank()) { "kind '$kind' must not be blank" }
        }
    }

    // --- Provenance ---

    @Test
    fun `Provenance rejects empty transformations list`() {
        val source = ProvenanceSource(
            resource = ResourceRef.parse("catalog://test/profile"),
            location = SourceLocation(path = "p.yaml", line = 1, column = 1),
            humanForm = null
        )
        assertFailsWith<IllegalArgumentException>("empty transformations must throw") {
            Provenance(
                key = "timeout",
                layer = Layer.PROFILE,
                source = source,
                transformations = emptyList()
            )
        }
    }

    @Test
    fun `Provenance accepts valid fields with null effectiveValue`() {
        val source = ProvenanceSource(
            resource = ResourceRef.parse("catalog://test/profile"),
            location = SourceLocation(path = "p.yaml", line = 1, column = 1),
            humanForm = null
        )
        val transformation = Transformation(kind = Transformation.REQUESTED_AS, detail = "from profile")
        val sut = Provenance(
            key = "timeout",
            layer = Layer.PROFILE,
            source = source,
            transformations = listOf(transformation),
            effectiveValue = null
        )
        assertEquals("timeout", sut.key)
        assertEquals(Layer.PROFILE, sut.layer)
        assertEquals(1, sut.transformations.size)
        assertNull(sut.effectiveValue)
    }

    @Test
    fun `Provenance accepts non-null effectiveValue (leaf node)`() {
        val source = ProvenanceSource(
            resource = ResourceRef.parse("catalog://test/profile"),
            location = SourceLocation(path = "p.yaml", line = 1, column = 1),
            humanForm = null
        )
        val transformation = Transformation(kind = Transformation.PROVIDED_BY, detail = "default")
        val sut = Provenance(
            key = "timeout",
            layer = Layer.LOCAL,
            source = source,
            transformations = listOf(transformation),
            effectiveValue = ParameterValue.StringValue("120m")
        )
        assertNotNull(sut.effectiveValue)
        assertEquals(ParameterValue.StringValue("120m"), sut.effectiveValue)
    }

    @Test
    fun `Provenance preserves transformation order`() {
        val source = ProvenanceSource(
            resource = ResourceRef.parse("catalog://test/profile"),
            location = SourceLocation(path = "p.yaml", line = 1, column = 1),
            humanForm = null
        )
        val t1 = Transformation(kind = Transformation.REQUESTED_AS, detail = "first")
        val t2 = Transformation(kind = Transformation.SELECTED_BY, detail = "second")
        val t3 = Transformation(kind = Transformation.PROVIDED_BY, detail = "third")
        val sut = Provenance(
            key = "timeout",
            layer = Layer.LOCAL,
            source = source,
            transformations = listOf(t1, t2, t3)
        )
        assertEquals(3, sut.transformations.size)
        assertEquals(t1, sut.transformations[0])
        assertEquals(t2, sut.transformations[1])
        assertEquals(t3, sut.transformations[2])
    }
}
