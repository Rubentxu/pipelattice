package dev.rubentxu.pipelattice.compose.compose

import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticCode
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
import dev.rubentxu.pipelattice.resource.GovernanceMode
import dev.rubentxu.pipelattice.resource.ParameterDeclaration
import dev.rubentxu.pipelattice.resource.ParameterType
import dev.rubentxu.pipelattice.resource.ParameterValue

/**
 * Result of binding parameters from a profile with local overrides.
 *
 * @param bindings The successfully bound parameters (key -> value).
 * @param unboundKeys Keys from profileDecls that were not bound (e.g., optional with no default and no override).
 */
internal data class ParameterBinderResult(
    val bindings: Map<String, ParameterValue>,
    val unboundKeys: Set<String> = emptySet(),
)

/**
 * Internal parameter binder that resolves parameter values from profile declarations and local overrides.
 *
 * Implements the 6 binding cases (A-F) from design spec §6.2:
 *
 * A: Key in profileDecls and localOverrides, types match → localOverride wins
 * B: Key in profileDecls and localOverrides, types mismatch → RESOURCE-SCHEMA-002 error
 * C: Key in profileDecls only, no localOverride → use profile default (if present)
 * D: Key in localOverrides only (not in profileDecls) → RESOURCE-SCHEMA-002 error (undeclared)
 * E: Key in profileDecls with MANDATORY governance, no localOverride → error (required missing)
 * F: Key in profileDecls only with no default, no localOverride → unbound (not an error)
 *
 * @param diagnostics Diagnostic sink for reporting binding errors.
 */
internal class ParameterBinder(
    private val diagnostics: DiagnosticSink,
) {

    /**
     * Reference to the RESOURCE-SCHEMA-002 diagnostic code from M1 config-compiler.
     * Reused here for type mismatch errors to maintain code stability across modules.
     */
    private companion object {
        val TYPE_MISMATCH_CODE = DiagnosticCode("RESOURCE-SCHEMA-002")
    }

    /**
     * Binds parameters from profile declarations with local overrides.
     *
     * @param profileDecls Parameter declarations from the profile.
     * @param profileRef Reference to the profile resource (for provenance).
     * @param localOverrides Local parameter overrides from the pipeline definition.
     * @param pipelineRef Reference to the pipeline resource (for provenance).
     * @return [ParameterBinderResult] containing bound parameters and any unbound keys.
     */
    fun bind(
        profileDecls: Map<String, ParameterDeclaration>,
        profileRef: ResourceRef,
        localOverrides: Map<String, ParameterValue>,
        pipelineRef: ResourceRef,
    ): ParameterBinderResult {
        val bindings = mutableMapOf<String, ParameterValue>()
        val unboundKeys = mutableSetOf<String>()

        // Case A/C/E/F: Keys from profileDecls
        for ((key, decl) in profileDecls) {
            val override = localOverrides[key]

            when {
                // Case A: override exists and matches type → use override
                override != null && typesMatch(decl.type, override) -> {
                    bindings[key] = override
                }

                // Case B: override exists but type mismatch → error
                override != null && !typesMatch(decl.type, override) -> {
                    diagnostics.report(
                        Diagnostic(
                            code = TYPE_MISMATCH_CODE,
                            severity = DiagnosticSeverity.ERROR,
                            message = "Type mismatch for parameter '$key': expected ${decl.type.wireName}, " +
                                    "got ${override.typeName}",
                            location = SourceLocation(path = pipelineRef.path),
                            remediationHint = "Provide a value of type '${decl.type.wireName}' for '$key'"
                        )
                    )
                }

                // Case E: MANDATORY governance, no override → error
                decl.governance.mode == GovernanceMode.MANDATORY && override == null -> {
                    diagnostics.report(
                        Diagnostic(
                            code = DiagnosticCode("RESOURCE-SCHEMA-001"),
                            severity = DiagnosticSeverity.ERROR,
                            message = "Required parameter '$key' is missing",
                            location = SourceLocation(path = pipelineRef.path),
                            remediationHint = "Provide a value for required parameter '$key'"
                        )
                    )
                }

                // Case C: profile decl with default, no override → use default
                override == null -> {
                    val defaultVal = decl.default
                    if (defaultVal != null) {
                        bindings[key] = defaultVal
                    } else {
                        unboundKeys.add(key)
                    }
                }
            }
        }

        // Case D: Keys in localOverrides but not in profileDecls → error (undeclared parameter)
        for ((key, override) in localOverrides) {
            if (key !in profileDecls) {
                diagnostics.report(
                    Diagnostic(
                        code = TYPE_MISMATCH_CODE,
                        severity = DiagnosticSeverity.ERROR,
                        message = "Undeclared parameter '$key' in pipeline; not defined in profile",
                        location = SourceLocation(path = pipelineRef.path),
                        remediationHint = "Remove the undeclared parameter '$key' or add it to the profile"
                    )
                )
            }
        }

        return ParameterBinderResult(bindings = bindings, unboundKeys = unboundKeys)
    }

    /**
     * Checks if a [ParameterValue] matches the expected [ParameterType].
     */
    private fun typesMatch(expected: ParameterType, value: ParameterValue): Boolean {
        return when (expected) {
            ParameterType.INTEGER -> value is ParameterValue.IntValue
            ParameterType.BOOLEAN -> value is ParameterValue.BoolValue
            ParameterType.STRING -> value is ParameterValue.StringValue
        }
    }

    /**
     * Extension property to get the wire name of a ParameterValue's type.
     */
    private val ParameterValue.typeName: String
        get() = when (this) {
            is ParameterValue.IntValue -> ParameterType.INTEGER.wireName
            is ParameterValue.BoolValue -> ParameterType.BOOLEAN.wireName
            is ParameterValue.StringValue -> ParameterType.STRING.wireName
        }
}
