package dev.rubentxu.pipelattice.compose.compose

import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.resource.Governance
import dev.rubentxu.pipelattice.resource.GovernanceMode
import dev.rubentxu.pipelattice.resource.ParameterDeclaration
import dev.rubentxu.pipelattice.resource.ParameterType
import dev.rubentxu.pipelattice.resource.ParameterValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [ParameterBinder.bind].
 *
 * Implements 6 cases (A-F) from design spec §6.2:
 * A: Key in profileDecls and localOverrides, types match → localOverride wins
 * B: Key in profileDecls and localOverrides, types mismatch → RESOURCE-SCHEMA-002 error
 * C: Key in profileDecls only, no localOverride → use profile default
 * D: Key in localOverrides only (not in profileDecls) → error (undeclared parameter)
 * E: Key in profileDecls with MANDATORY governance, no localOverride → error (required missing)
 * F: Key in profileDecls only with no default, no localOverride → unbound (not an error)
 *
 * Plus additional test for type mismatch rejection.
 */
class ParameterBinderTest {

    private val profileRef = ResourceRef.parse("catalog://profiles/java-maven")
    private val pipelineRef = ResourceRef.parse("catalog://pipelines/build")

    private fun collectingSink(): Pair<MutableList<Diagnostic>, DiagnosticSink> {
        val reports = mutableListOf<Diagnostic>()
        val sink = DiagnosticSink { reports.add(it) }
        return reports to sink
    }

    // Helper constructors
    private fun decl(
        type: ParameterType,
        default: ParameterValue? = null,
        governance: Governance = Governance(),
    ) = ParameterDeclaration(type = type, default = default, governance = governance)

    private fun intDefault(value: Long) = ParameterValue.IntValue(value)
    private fun boolDefault(value: Boolean) = ParameterValue.BoolValue(value)
    private fun strDefault(value: String) = ParameterValue.StringValue(value)

    // --- Case A: Override matches type → override wins ---

    @Test
    fun `bind Case A - override matches declared type uses override`() {
        val (reports, sink) = collectingSink()
        val binder = ParameterBinder(sink)

        val profileDecls = mapOf(
            "timeout" to decl(ParameterType.INTEGER, intDefault(30))
        )
        val localOverrides = mapOf(
            "timeout" to intDefault(60)
        )

        val result = binder.bind(profileDecls, profileRef, localOverrides, pipelineRef)

        assertEquals(1, result.bindings.size)
        assertEquals(60L, (result.bindings["timeout"] as ParameterValue.IntValue).value)
        assertTrue(reports.isEmpty(), "No diagnostics expected for Case A")
    }

    // --- Case B: Type mismatch between override and declaration ---

    @Test
    fun `bind Case B - override type mismatch produces RESOURCE-SCHEMA-002`() {
        val (reports, sink) = collectingSink()
        val binder = ParameterBinder(sink)

        val profileDecls = mapOf(
            "timeout" to decl(ParameterType.INTEGER, intDefault(30))
        )
        val localOverrides = mapOf(
            "timeout" to ParameterValue.StringValue("60")  // Wrong type!
        )

        val result = binder.bind(profileDecls, profileRef, localOverrides, pipelineRef)

        assertTrue(reports.isNotEmpty())
        assertTrue(reports.any { it.code.value == "RESOURCE-SCHEMA-002" },
            "Expected RESOURCE-SCHEMA-002 for type mismatch")
    }

    // --- Case C: Profile decl with default, no override → use default ---

    @Test
    fun `bind Case C - uses profile default when no override`() {
        val (reports, sink) = collectingSink()
        val binder = ParameterBinder(sink)

        val profileDecls = mapOf(
            "timeout" to decl(ParameterType.INTEGER, intDefault(30))
        )
        val localOverrides = emptyMap<String, ParameterValue>()

        val result = binder.bind(profileDecls, profileRef, localOverrides, pipelineRef)

        assertEquals(1, result.bindings.size)
        assertEquals(30L, (result.bindings["timeout"] as ParameterValue.IntValue).value)
        assertTrue(reports.isEmpty(), "No diagnostics expected for Case C")
    }

    // --- Case D: Override for undeclared parameter → error ---

    @Test
    fun `bind Case D - override for undeclared parameter produces error`() {
        val (reports, sink) = collectingSink()
        val binder = ParameterBinder(sink)

        val profileDecls = mapOf(
            "timeout" to decl(ParameterType.INTEGER, intDefault(30))
        )
        val localOverrides = mapOf(
            "timeout" to intDefault(60),
            "unknownParam" to intDefault(100)  // Not declared in profile
        )

        val result = binder.bind(profileDecls, profileRef, localOverrides, pipelineRef)

        // timeout is bound, unknownParam is not
        assertEquals(1, result.bindings.size)
        assertTrue(reports.any { it.code.value == "RESOURCE-SCHEMA-002" },
            "Expected RESOURCE-SCHEMA-002 for undeclared parameter")
    }

    // --- Case E: MANDATORY governance, no override → error ---

    @Test
    fun `bind Case E - mandatory parameter without override produces error`() {
        val (reports, sink) = collectingSink()
        val binder = ParameterBinder(sink)

        val profileDecls = mapOf(
            "requiredTimeout" to decl(
                ParameterType.INTEGER,
                governance = Governance(mode = GovernanceMode.MANDATORY)
            )
        )
        val localOverrides = emptyMap<String, ParameterValue>()

        val result = binder.bind(profileDecls, profileRef, localOverrides, pipelineRef)

        assertTrue(result.bindings.isEmpty())
        assertTrue(reports.any { it.code.value == "RESOURCE-SCHEMA-001" },
            "Expected RESOURCE-SCHEMA-001 for missing mandatory parameter")
    }

    // --- Case F: Optional param with no default, no override → unbound (not an error) ---

    @Test
    fun `bind Case F - optional param with no default and no override is unbound`() {
        val (reports, sink) = collectingSink()
        val binder = ParameterBinder(sink)

        val profileDecls = mapOf(
            "optionalFlag" to decl(ParameterType.BOOLEAN, default = null)
        )
        val localOverrides = emptyMap<String, ParameterValue>()

        val result = binder.bind(profileDecls, profileRef, localOverrides, pipelineRef)

        assertTrue(result.bindings.isEmpty())
        assertTrue(result.unboundKeys.contains("optionalFlag"))
        assertTrue(reports.isEmpty(), "No diagnostics expected for Case F (unbound is not an error)")
    }

    // --- Additional: Type mismatch rejection ---

    @Test
    fun `bind rejects type mismatch with RESOURCE-SCHEMA-002 and correct message`() {
        val (reports, sink) = collectingSink()
        val binder = ParameterBinder(sink)

        val profileDecls = mapOf(
            "enabled" to decl(ParameterType.BOOLEAN, boolDefault(true))
        )
        val localOverrides = mapOf(
            "enabled" to ParameterValue.StringValue("true")  // Should be boolean!
        )

        binder.bind(profileDecls, profileRef, localOverrides, pipelineRef)

        assertTrue(reports.isNotEmpty())
        val error = reports.first { it.code.value == "RESOURCE-SCHEMA-002" }
        assertTrue(error.message.contains("enabled"))
        assertTrue(error.message.contains("boolean"))
        assertTrue(error.message.contains("string"))
        assertTrue(error.remediationHint?.contains("boolean") == true)
    }
}
