package dev.rubentxu.pipelattice.policy.engine

import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.policy.domain.Policy
import dev.rubentxu.pipelattice.policy.domain.Violation
import dev.rubentxu.pipelattice.policy.ports.PolicyEngine

/**
 * Default no-op policy engine for A-min.
 *
 * This implementation satisfies the [PolicyEngine] contract but performs no actual rule
 * evaluation — it always returns an empty list of violations. Real dispatch logic
 * is deferred to A-lite.
 */
internal class DefaultPolicyEngine : PolicyEngine {

    override fun evaluate(
        policy: Policy,
        target: ResourceRef,
        diagnostics: DiagnosticSink,
    ): List<Violation> = emptyList()
}
