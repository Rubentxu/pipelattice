package dev.rubentxu.pipelattice.policy.engine

import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.policy.domain.Policy
import dev.rubentxu.pipelattice.policy.domain.Rule
import dev.rubentxu.pipelattice.policy.ports.PolicyEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultPolicyEngineTest {

    @Test
    fun `evaluate returns empty list regardless of policy content`() {
        val engine: PolicyEngine = DefaultPolicyEngine()
        val policy = Policy(
            id = "policy-001",
            version = "1.0.0",
            scope = "catalog://profiles/dev",
            rules = listOf(
                Rule.AllowedKeys(setOf("foo")),
                Rule.MaxDepth(5),
                Rule.ForbiddenPattern(".*secret.*"),
            ),
        )
        val target = ResourceRef.parse("catalog://resources/test")
        val diagnosticSink = DiagnosticSink { /* discard */ }

        val violations = engine.evaluate(policy, target, diagnosticSink)

        assertTrue(violations.isEmpty(), "No-op engine must return empty violations")
    }

    @Test
    fun `evaluate with empty ruleset returns empty list`() {
        val engine: PolicyEngine = DefaultPolicyEngine()
        val policy = Policy(
            id = "policy-empty",
            version = "1.0.0",
            scope = "catalog://profiles/dev",
            rules = emptyList(),
        )
        val target = ResourceRef.parse("catalog://resources/test")
        val diagnosticSink = DiagnosticSink { /* discard */ }

        val violations = engine.evaluate(policy, target, diagnosticSink)

        assertEquals(emptyList(), violations)
    }

    @Test
    fun `DefaultPolicyEngine can be substituted in tests`() {
        // Verify the concrete class exists and is assignable to PolicyEngine
        val engine: PolicyEngine = DefaultPolicyEngine()
        assertTrue(engine is DefaultPolicyEngine)
    }
}
