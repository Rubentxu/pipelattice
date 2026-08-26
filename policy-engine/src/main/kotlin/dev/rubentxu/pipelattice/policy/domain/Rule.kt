package dev.rubentxu.pipelattice.policy.domain

import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticCode

/**
 * Sealed hierarchy of policy rules.
 *
 * Each variant carries a frozen [DiagnosticCode] that identifies the rule
 * in diagnostics and reports.
 */
public sealed interface Rule {

    /**
     * Stable diagnostic code for this rule variant.
     */
    public val code: DiagnosticCode

    /**
     * Rule that allows only a specific set of keys.
     */
    public data class AllowedKeys(
        public val keys: Set<String>,
    ) : Rule {
        public companion object {
            public val CODE: DiagnosticCode = DiagnosticCode("POLICY-RULE-001")
        }
        override val code: DiagnosticCode get() = CODE
    }

    /**
     * Rule that forbids a specific pattern in resource names or values.
     */
    public data class ForbiddenPattern(
        public val pattern: String,
    ) : Rule {
        public companion object {
            public val CODE: DiagnosticCode = DiagnosticCode("POLICY-RULE-002")
        }
        override val code: DiagnosticCode get() = CODE
    }

    /**
     * Rule that limits the maximum depth of a resource hierarchy.
     */
    public data class MaxDepth(
        public val max: Int,
    ) : Rule {
        public companion object {
            public val CODE: DiagnosticCode = DiagnosticCode("POLICY-RULE-003")
        }
        override val code: DiagnosticCode get() = CODE
    }
}
