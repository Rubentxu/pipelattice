package dev.rubentxu.pipelattice.policy.domain

import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticCode
import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyTest {

    @Test
    fun `Policy carries id version scope and rules`() {
        val rule = Rule.AllowedKeys(setOf("foo", "bar"))
        val policy = Policy(
            id = "policy-001",
            version = "1.0.0",
            scope = "catalog://profiles/dev",
            rules = listOf(rule),
        )
        assertEquals("policy-001", policy.id)
        assertEquals("1.0.0", policy.version)
        assertEquals("catalog://profiles/dev", policy.scope)
        assertEquals(1, policy.rules.size)
    }

    @Test
    fun `AllowedKeys rule code is POLICY-RULE-001`() {
        val rule = Rule.AllowedKeys(setOf("key1"))
        assertEquals(DiagnosticCode("POLICY-RULE-001"), rule.code)
    }

    @Test
    fun `ForbiddenPattern rule code is POLICY-RULE-002`() {
        val rule = Rule.ForbiddenPattern(".*secret.*")
        assertEquals(DiagnosticCode("POLICY-RULE-002"), rule.code)
    }

    @Test
    fun `MaxDepth rule code is POLICY-RULE-003`() {
        val rule = Rule.MaxDepth(max = 5)
        assertEquals(DiagnosticCode("POLICY-RULE-003"), rule.code)
    }
}
