package dev.rubentxu.pipelattice.fleet.diff.json

import dev.rubentxu.pipelattice.fleet.diff.domain.FleetDiffReport
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import kotlin.test.Test
import kotlin.test.assertContains

class FleetDiffJsonEncoderSchemaTest {

    @Test
    fun `encode includes schema version fleet-diff-v1`() {
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

        assertContains(json, "\"schema\":\"fleet-diff/v1\"")
    }

    @Test
    fun `schema version appears first in output`() {
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

        // Schema should be the first field
        assertContains(json, "\"schema\":\"fleet-diff/v1\"")
    }
}
