package dev.rubentxu.pipelattice.policy.ports

import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Verifies that the [PolicyEngine] interface is properly declared
 * and can be compiled against.
 */
class PolicyEngineInterfaceTest {

    @Test
    fun `PolicyEngine interface is resolvable`() {
        // This test simply verifies the interface compiles and is accessible.
        // Actual behavior is tested via DefaultPolicyEngine substitution.
        val clazz = PolicyEngine::class.java
        assertNotNull(clazz)
    }
}
