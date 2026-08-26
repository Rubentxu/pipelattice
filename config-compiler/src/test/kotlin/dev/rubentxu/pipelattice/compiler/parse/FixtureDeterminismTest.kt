package dev.rubentxu.pipelattice.compiler.parse

import dev.rubentxu.pipelattice.resource.SourceDocument
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path

/**
 * REQ-Golden-Fixture-Determinism: parse real examples from the examples/ directory
 * 100 times and assert identical result and stable digest intra-execution.
 *
 * The real examples/pipeline.yaml contains a `patches` field that is not permitted
 * by the PipelineDefinition schema, so RESOURCE-FIELD-001 is emitted. This is
 * intentional — the test verifies determinism regardless of whether diagnostics
 * are present.
 */
class FixtureDeterminismTest {

    private val parser = YamlResourceParser()

    private fun digest(result: dev.rubentxu.pipelattice.resource.ParseResult): String {
        val canonical = result.resources.joinToString("|") { r ->
            "${r.kind}:${r.apiVersion.value}:${r.metadata.name}"
        }
        val diags = result.diagnostics.joinToString(";") { d ->
            "${d.code.value}:${d.severity}:${d.message}"
        }
        val combined = "$canonical|$diags"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(combined.toByteArray()).fold("") { b, v -> b + "%02x".format(v) }
    }

    /**
     * Resolves a path relative to the project root (pipelattice/).
     * Gradle runs test executors from config-compiler/ subdirectory,
     * so we go up one level to reach the project root.
     */
    private fun readProjectFile(relativePath: String): String {
        // Test executor runs from config-compiler/ subdir, go up one level to pipelattice/
        val projectRoot = Path.of(System.getProperty("user.dir")).parent
        return Files.readString(projectRoot.resolve(relativePath))
    }

    @Test
    fun `examples pipeline yaml is deterministic over 100 parses`() {
        // Real file: examples/pipeline.yaml contains `patches` field which is not permitted
        // by PipelineDefinition schema → RESOURCE-FIELD-001 is expected and deterministic
        val yaml = readProjectFile("examples/pipeline.yaml")

        val firstDigest = digest(parser.parse(SourceDocument("examples/pipeline.yaml", yaml)))

        repeat(99) { iteration ->
            val result = parser.parse(SourceDocument("examples/pipeline.yaml", yaml))
            // The real file produces RESOURCE-FIELD-001 for the `patches` field.
            // Verify the diagnostic is deterministic (same code always).
            assertTrue(result.hasErrors, "Iteration $iteration: expected diagnostics for patches field")
            assertTrue(result.diagnostics.any { it.code.value == "RESOURCE-FIELD-001" },
                "Iteration $iteration: expected RESOURCE-FIELD-001 for patches field but got: ${result.diagnostics}")
            val currentDigest = digest(result)
            assertEquals(firstDigest, currentDigest, "Iteration $iteration: digest mismatch")
        }
    }

    @Test
    fun `examples catalog profile is deterministic over 100 parses`() {
        // Real file: examples/catalog/java-maven-container-profile.yaml is a valid PipelineProfile
        val yaml = readProjectFile("examples/catalog/java-maven-container-profile.yaml")

        val firstDigest = digest(parser.parse(SourceDocument("examples/catalog/java-maven-container-profile.yaml", yaml)))

        repeat(99) { iteration ->
            val result = parser.parse(SourceDocument("examples/catalog/java-maven-container-profile.yaml", yaml))
            assertTrue(result.diagnostics.isEmpty(),
                "Iteration $iteration: expected no diagnostics but got: ${result.diagnostics}")
            assertEquals(1, result.resources.size, "Iteration $iteration: expected 1 resource")
            val currentDigest = digest(result)
            assertEquals(firstDigest, currentDigest, "Iteration $iteration: digest mismatch")
        }
    }
}
