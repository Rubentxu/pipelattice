package dev.rubentxu.pipelattice.fleet.diff.domain

import dev.rubentxu.pipelattice.fleet.diff.repository.InMemorySnapshotRepository
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [PolicyViolationSource].
 */
class PolicyViolationSourceTest {

    @Test
    fun `seam is explicit empty`() {
        val repo = InMemorySnapshotRepository()
        val policySource = PolicyViolationSource(repo)

        val violations = policySource()

        assertEquals(emptySet(), violations)
    }
}
