package dev.rubentxu.pipelattice.compose

import dev.rubentxu.pipelattice.compose.compose.FingerprintComputer
import dev.rubentxu.pipelattice.compose.domain.Layer
import dev.rubentxu.pipelattice.compose.domain.Provenance
import dev.rubentxu.pipelattice.compose.domain.ProvenanceSource
import dev.rubentxu.pipelattice.compose.domain.Transformation
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
import dev.rubentxu.pipelattice.resource.ParameterValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Fingerprint determinism and mutation tests.
 *
 * Requirements:
 * 1. Determinism: Same input 100x produces identical hash
 * 2. Mutation: Any change to input produces different hash
 */
class FingerprintDeterminismTest {

    private fun makeProvenance(
        key: String = "timeout",
        locationPath: String = "profiles/java-maven",
    ): List<Provenance> {
        val source = ProvenanceSource(
            resource = ResourceRef.parse("catalog://$locationPath"),
            location = SourceLocation(path = locationPath)
        )
        return listOf(
            Provenance(
                key = key,
                layer = Layer.PROFILE,
                source = source,
                transformations = listOf(
                    Transformation(kind = Transformation.PROVIDED_BY, detail = "profile default")
                ),
                effectiveValue = ParameterValue.IntValue(30)
            )
        )
    }

    private fun makeParams(vararg pairs: Pair<String, ParameterValue>): Map<String, ParameterValue> =
        mapOf(*pairs)

    // --- Determinism: Same input 100x produces same hash ---

    @Test
    fun `compute produces identical hash for same input 100 times`() {
        val params = makeParams("timeout" to ParameterValue.IntValue(30))
        val provenance = mapOf("timeout" to makeProvenance())

        val hashes = (1..100).map {
            FingerprintComputer.compute(params, provenance)
        }

        // All 100 hashes should be identical
        assertEquals(
            hashes.first(),
            hashes.last(),
            "First and last hash should be identical after 100 iterations"
        )
        assertEquals(
            1,
            hashes.toSet().size,
            "All 100 hashes should be identical (deterministic)"
        )
    }

    // --- Mutation: Any change produces different hash ---

    @Test
    fun `compute produces different hash when parameter value changes`() {
        val params1 = makeParams("timeout" to ParameterValue.IntValue(30))
        val params2 = makeParams("timeout" to ParameterValue.IntValue(60))
        val provenance = mapOf("timeout" to makeProvenance())

        val hash1 = FingerprintComputer.compute(params1, provenance)
        val hash2 = FingerprintComputer.compute(params2, provenance)

        assertNotEquals(hash1, hash2, "Hash should change when parameter value changes")
    }

    @Test
    fun `compute produces different hash when parameter is added`() {
        val params1 = makeParams("timeout" to ParameterValue.IntValue(30))
        val params2 = makeParams(
            "timeout" to ParameterValue.IntValue(30),
            "enabled" to ParameterValue.BoolValue(true)
        )
        val provenance = mapOf("timeout" to makeProvenance())

        val hash1 = FingerprintComputer.compute(params1, provenance)
        val hash2 = FingerprintComputer.compute(params2, provenance)

        assertNotEquals(hash1, hash2, "Hash should change when parameter is added")
    }

    @Test
    fun `compute produces different hash when parameter is removed`() {
        val params1 = makeParams(
            "timeout" to ParameterValue.IntValue(30),
            "enabled" to ParameterValue.BoolValue(true)
        )
        val params2 = makeParams("timeout" to ParameterValue.IntValue(30))
        val provenance = mapOf("timeout" to makeProvenance())

        val hash1 = FingerprintComputer.compute(params1, provenance)
        val hash2 = FingerprintComputer.compute(params2, provenance)

        assertNotEquals(hash1, hash2, "Hash should change when parameter is removed")
    }

    @Test
    fun `compute produces different hash when provenance location changes`() {
        val params = makeParams("timeout" to ParameterValue.IntValue(30))
        val provenance1 = mapOf("timeout" to makeProvenance(locationPath = "profiles/java-maven"))
        val provenance2 = mapOf("timeout" to makeProvenance(locationPath = "profiles/golang"))

        val hash1 = FingerprintComputer.compute(params, provenance1)
        val hash2 = FingerprintComputer.compute(params, provenance2)

        assertNotEquals(hash1, hash2, "Hash should change when provenance location changes")
    }

    @Test
    fun `compute produces different hash when provenance layer changes`() {
        val params = makeParams("timeout" to ParameterValue.IntValue(30))
        val provenance1 = mapOf("timeout" to makeProvenance())
        val provenance2 = mapOf("timeout" to listOf(
            Provenance(
                key = "timeout",
                layer = Layer.LOCAL,
                source = ProvenanceSource(
                    resource = ResourceRef.parse("catalog://pipelines/build"),
                    location = SourceLocation(path = "pipelines/build")
                ),
                transformations = listOf(
                    Transformation(kind = Transformation.OVERRIDDEN_BY, detail = "pipeline override")
                ),
                effectiveValue = ParameterValue.IntValue(30)
            )
        ))

        val hash1 = FingerprintComputer.compute(params, provenance1)
        val hash2 = FingerprintComputer.compute(params, provenance2)

        assertNotEquals(hash1, hash2, "Hash should change when provenance layer changes")
    }

    @Test
    fun `compute produces SHA-256 hex lowercase 64 characters`() {
        val params = makeParams("timeout" to ParameterValue.IntValue(30))
        val provenance = mapOf("timeout" to makeProvenance())

        val hash = FingerprintComputer.compute(params, provenance)

        // SHA-256 produces 64 hex characters
        assertEquals(64, hash.length, "SHA-256 hash should be 64 characters")
        assertEquals(hash, hash.lowercase(), "Hash should be lowercase hex")
        // Verify it's valid hex
        assertEquals(
            hash,
            hash.filter { it in '0'..'9' || it in 'a'..'f' },
            "Hash should only contain hex characters"
        )
    }
}
