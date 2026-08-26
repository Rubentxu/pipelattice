package dev.rubentxu.pipelattice.policy.ports

import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.policy.domain.Policy
import dev.rubentxu.pipelattice.policy.domain.Violation

/**
 * Port for evaluating policies against a resource.
 *
 * This is the primary entry point for policy evaluation in the pipeline. Implementations
 * must be synchronous (no suspend functions) and return all violations in a single list.
 *
 * ## Contract
 * - The [evaluate] method is **synchronous** — no coroutines, no continuations.
 * - When [policy] contains no rules, [evaluate] returns [emptyList].
 * - Each violation's [Violation.code] identifies the specific [Rule] variant that triggered it.
 *
 * ## Usage
 * ```kotlin
 * val engine: PolicyEngine = DefaultPolicyEngine()
 * val violations = engine.evaluate(policy, targetResource, diagnosticSink)
 * if (violations.isNotEmpty()) {
 *     // handle policy violations
 * }
 * ```
 *
 * ## Thread safety
 * Implementations are expected to be thread-safe if used concurrently from multiple threads.
 * DefaultPolicyEngine is stateless and therefore thread-safe.
 */
public interface PolicyEngine {
    /**
     * Evaluates [policy] against the given [target] resource.
     *
     * @param policy The policy to evaluate.
     * @param target The resource reference to check against the policy.
     * @param diagnostics Sink for diagnostics emitted during evaluation.
     * @return List of violations found; empty list means all rules passed.
     */
    public fun evaluate(
        policy: Policy,
        target: ResourceRef,
        diagnostics: DiagnosticSink,
    ): List<Violation>
}
