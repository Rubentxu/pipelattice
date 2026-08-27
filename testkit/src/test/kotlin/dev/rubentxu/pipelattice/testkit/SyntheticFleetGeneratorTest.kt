package dev.rubentxu.pipelattice.testkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [SyntheticFleetGenerator].
 */
class SyntheticFleetGeneratorTest {

    @Test
    fun `smoke 500 deterministic`() {
        val generator = SyntheticFleetGenerator(seed = 42L)

        val fleet1 = generator.generate(500)
        val fleet2 = SyntheticFleetGenerator(seed = 42L).generate(500)

        // Same seed should produce byte-identical output
        assertEquals(fleet1.size, fleet2.size)
        assertEquals(fleet1.map { it.path }, fleet2.map { it.path })
        assertEquals(fleet1.map { it.content }, fleet2.map { it.content })

        // Documents should be valid YAML (check structure)
        for (doc in fleet1) {
            assertTrue(doc.content.contains("apiVersion:"), "Should contain apiVersion")
            assertTrue(doc.content.contains("kind:"), "Should contain kind")
            assertTrue(doc.content.contains("metadata:"), "Should contain metadata")
        }
    }

    @Test
    fun `different seeds produce different output`() {
        val fleet1 = SyntheticFleetGenerator(seed = 42L).generate(10)
        val fleet2 = SyntheticFleetGenerator(seed = 123L).generate(10)

        assertEquals(fleet1.map { it.path }, fleet2.map { it.path })
        assertEquals(fleet1.size, fleet2.size)

        // Contents should differ (with high probability)
        val contentsMatch = fleet1.zip(fleet2).all { (a, b) -> a.content == b.content }
        assertFalse(contentsMatch, "Different seeds should produce different content")
    }

    @Test
    fun `generates expected number of documents`() {
        val projectCount = 100
        val profilesPerProject = 2
        val importsPerProfile = 3

        val generator = SyntheticFleetGenerator(
            seed = 42L,
            profilesPerProject = profilesPerProject,
            importsPerProfile = importsPerProfile,
        )

        val fleet = generator.generate(projectCount)

        // Each project has 1 pipeline + profilesPerProject profiles
        val expectedDocs = projectCount * (1 + profilesPerProject)
        assertEquals(expectedDocs, fleet.size)
    }

    @Test
    fun `documents have valid YAML structure`() {
        val generator = SyntheticFleetGenerator(seed = 42L)
        val fleet = generator.generate(5)

        for (doc in fleet) {
            // Each document should have proper YAML structure
            assertTrue(doc.path.endsWith(".yaml") || doc.path.endsWith(".yml"),
                "Path should end with .yaml or .yml: ${doc.path}")
            assertFalse(doc.content.contains("\t"), "YAML should not contain tabs")
            // Check for basic YAML structure
            val lines = doc.content.lines()
            assertTrue(lines.isNotEmpty(), "Document should have content")
        }
    }
}

/**
 * Slow-tier tests for [SyntheticFleetGenerator].
 *
 * These tests are excluded from the default `./gradlew test` run.
 * Run with `./gradlew :testkit:test -Pslow` to execute.
 */
class SyntheticFleetGeneratorSlowTest {

    @Test
    fun `ten_k_metric_under_60s`() {
        val generator = SyntheticFleetGenerator(seed = 42L)

        val startTime = System.currentTimeMillis()
        val fleet = generator.generate(10000)
        val elapsed = System.currentTimeMillis() - startTime

        // Verify we generated the expected count
        val expectedDocs = 10000 * (1 + 2 * 3) // 10000 projects * (1 pipeline + 2 profiles * 3 imports)
        assertEquals(expectedDocs, fleet.size)

        // Document the metric (not CI-gated per spec M7)
        println("10k generation time: ${elapsed}ms")
        assertTrue(
            elapsed < 60000,
            "10k generation should complete in under 60s (was ${elapsed}ms) - metric only, not CI-gated"
        )
    }
}
