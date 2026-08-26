package dev.rubentxu.pipelattice.compiler.parse

import dev.rubentxu.pipelattice.resource.SourceDocument
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REQ-Golden-Fixture-Determinism: parse positive fixtures 100 times and assert
 * identical result and stable digest intra-execution.
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

    @Test
    fun `pipeline yaml fixture is deterministic over 100 parses`() {
        val yaml = javaClass.getResource("/fixtures/positive/pipeline.yaml")
            ?.readText() ?: throw IllegalStateException("pipeline.yaml not found on classpath")

        val firstDigest = digest(parser.parse(SourceDocument("pipeline.yaml", yaml)))

        repeat(99) { iteration ->
            val result = parser.parse(SourceDocument("pipeline.yaml", yaml))
            assertTrue(result.diagnostics.isEmpty(), "Iteration $iteration: expected no diagnostics but got: ${result.diagnostics}")
            assertEquals(1, result.resources.size, "Iteration $iteration: expected 1 resource")
            val currentDigest = digest(result)
            assertEquals(firstDigest, currentDigest, "Iteration $iteration: digest mismatch")
        }
    }

    @Test
    fun `java-maven-container profile fixture is deterministic over 100 parses`() {
        val yaml = javaClass.getResource("/fixtures/positive/java-maven-container-profile.yaml")
            ?.readText() ?: throw IllegalStateException("java-maven-container-profile.yaml not found on classpath")

        val firstDigest = digest(parser.parse(SourceDocument("java-maven-container-profile.yaml", yaml)))

        repeat(99) { iteration ->
            val result = parser.parse(SourceDocument("java-maven-container-profile.yaml", yaml))
            assertTrue(result.diagnostics.isEmpty(), "Iteration $iteration: expected no diagnostics but got: ${result.diagnostics}")
            assertEquals(1, result.resources.size, "Iteration $iteration: expected 1 resource")
            val currentDigest = digest(result)
            assertEquals(firstDigest, currentDigest, "Iteration $iteration: digest mismatch")
        }
    }
}
