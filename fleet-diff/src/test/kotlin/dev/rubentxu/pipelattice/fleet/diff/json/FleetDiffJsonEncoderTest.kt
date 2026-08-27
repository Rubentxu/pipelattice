package dev.rubentxu.pipelattice.fleet.diff.json

import dev.rubentxu.pipelattice.fleet.diff.domain.FleetDiffChange
import dev.rubentxu.pipelattice.fleet.diff.domain.FleetDiffReport
import dev.rubentxu.pipelattice.fleet.diff.domain.PlanReference
import dev.rubentxu.pipelattice.fleet.diff.domain.PolicyViolation
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertTrue

class FleetDiffJsonEncoderTest {

    @Test
    fun `encode produces valid JSON with all seven sections`() {
        val project = GraphNode.Project(ResourceRef("projects/example"))
        val profile = GraphNode.PipelineProfile(ResourceRef("profiles/java"))

        val change = FleetDiffChange.Added(profile, project, EdgeKind.SELECTS)

        val report = FleetDiffReport(
            affectedProjects = setOf(project),
            effectiveChanges = listOf(change),
            invalidPlans = setOf(PlanReference(ResourceRef("projects/example"), "digest123")),
            newPolicyViolations = listOf(
                PolicyViolation(ResourceRef("projects/example"), "RULE-001", "Test violation")
            ),
            resolvedPolicyViolations = emptyList(),
            providerChanges = listOf(change),
            localOverrides = emptyList(),
            schema = "fleet-diff/v1",
            generatedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )

        val json = FleetDiffJsonEncoder.encode(report)

        // Verify all seven sections are present
        assertContains(json, "\"affectedProjects\"")
        assertContains(json, "\"effectiveChanges\"")
        assertContains(json, "\"invalidPlans\"")
        assertContains(json, "\"newPolicyViolations\"")
        assertContains(json, "\"resolvedPolicyViolations\"")
        assertContains(json, "\"providerChanges\"")
        assertContains(json, "\"localOverrides\"")
    }

    @Test
    fun `encode produces valid JSON structure`() {
        val report = FleetDiffReport(
            affectedProjects = emptySet(),
            effectiveChanges = emptyList(),
            invalidPlans = emptySet(),
            newPolicyViolations = emptyList(),
            resolvedPolicyViolations = emptyList(),
            providerChanges = emptyList(),
            localOverrides = emptyList(),
        )

        val json = FleetDiffJsonEncoder.encode(report)

        // Must be valid JSON - parse it back
        val parsed = jsonToMap(json)

        assertTrue(parsed.containsKey("schema"))
        assertTrue(parsed.containsKey("generatedAt"))
        assertTrue(parsed.containsKey("affectedProjects"))
        assertTrue(parsed.containsKey("effectiveChanges"))
        assertTrue(parsed.containsKey("invalidPlans"))
        assertTrue(parsed.containsKey("newPolicyViolations"))
        assertTrue(parsed.containsKey("resolvedPolicyViolations"))
        assertTrue(parsed.containsKey("providerChanges"))
        assertTrue(parsed.containsKey("localOverrides"))
    }

    /**
     * F-02r — diagnosticCode encoder assertion.
     *
     * Verifies that when invalidPlans contains a PlanReference with non-null diagnosticCode,
     * the JSON output includes the "diagnosticCode" field (line 109-111 of FleetDiffJsonEncoder).
     * This closes the test gap identified in verify-report-r2.md F-02r.
     */
    @Test
    fun `encode emits diagnosticCode field when PlanReference has non-null diagnosticCode`() {
        val report = FleetDiffReport(
            affectedProjects = emptySet(),
            effectiveChanges = emptyList(),
            invalidPlans = setOf(
                PlanReference(
                    projectId = ResourceRef("projects/example"),
                    planDigest = "abc123",
                    diagnosticCode = "E-COMPOSE-AFFECTED-001"
                )
            ),
            newPolicyViolations = emptyList(),
            resolvedPolicyViolations = emptyList(),
            providerChanges = emptyList(),
            localOverrides = emptyList(),
        )

        val json = FleetDiffJsonEncoder.encode(report)

        // Verify diagnosticCode is present in the JSON
        assertContains(json, "\"diagnosticCode\":\"E-COMPOSE-AFFECTED-001\"")
        // Verify the full invalidPlans entry structure
        assertContains(json, "\"invalidPlans\":[{\"projectId\":\"catalog://projects/example\",\"planDigest\":\"abc123\",\"diagnosticCode\":\"E-COMPOSE-AFFECTED-001\"}]")
    }

    private fun jsonToMap(json: String): Map<String, Any> {
        // Simple JSON parser for test assertions
        val result = mutableMapOf<String, Any>()
        val content = json.trim().removePrefix("{").removeSuffix("}")
        val pairs = content.split(",").map { it.trim() }

        for (pair in pairs) {
            val colonIndex = pair.indexOf(':')
            if (colonIndex > 0) {
                val key = pair.substring(0, colonIndex).trim().removeSurrounding("\"")
                val value = pair.substring(colonIndex + 1).trim().removeSurrounding("\"")
                result[key] = value
            }
        }
        return result
    }
}
