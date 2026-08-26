package dev.rubentxu.pipelattice.resource

import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticCode
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Invariant tests for the resource-model value classes.
 * Each assertion documents a constraint that MUST hold for the model to be correct.
 */
class ResourceModelInvariantsTest {

    // --- ApiVersion ---

    @Test
    fun `ApiVersion blank value throws`() {
        val exception = runCatching { ApiVersion("") }.exceptionOrNull()
        assertNotNull(exception)
        assertContains(exception.message!!, "must not be blank")
    }

    @Test
    fun `ApiVersion missing slash throws`() {
        val exception = runCatching { ApiVersion("v1alpha1") }.exceptionOrNull()
        assertNotNull(exception)
        assertContains(exception.message!!, "must have the form '<group>/<version>'")
    }

    @Test
    fun `ApiVersion with space throws`() {
        val exception = runCatching { ApiVersion("pipelattice dev/v1alpha1") }.exceptionOrNull()
        assertNotNull(exception)
        assertContains(exception.message!!, "must have the form '<group>/<version>'")
    }

    @Test
    fun `ApiVersion KNOWN is recognized`() {
        assertTrue(ApiVersion.KNOWN.isKnown)
        assertEquals("pipelattice.dev/v1alpha1", ApiVersion.KNOWN.value)
    }

    // --- Metadata ---

    @Test
    fun `Metadata name blank throws`() {
        val exception = runCatching { Metadata(name = "") }.exceptionOrNull()
        assertNotNull(exception)
        assertContains(exception.message!!, "metadata.name must not be blank")
    }

    @Test
    fun `Metadata with all fields succeeds`() {
        val metadata = Metadata(name = "my-resource", version = "1.0.0", labels = mapOf("env" to "prod"))
        assertEquals("my-resource", metadata.name)
        assertEquals("1.0.0", metadata.version)
        assertEquals(mapOf("env" to "prod"), metadata.labels)
    }

    @Test
    fun `Metadata version can be null`() {
        val metadata = Metadata(name = "my-resource")
        assertNull(metadata.version)
    }

    @Test
    fun `Metadata labels can be empty map`() {
        val metadata = Metadata(name = "my-resource", labels = emptyMap())
        assertEquals(emptyMap(), metadata.labels)
    }

    // --- Constraints ---

    @Test
    fun `Constraints min greater than max throws`() {
        val exception = runCatching { Constraints(min = 10, max = 5) }.exceptionOrNull()
        assertNotNull(exception)
        assertContains(exception.message!!, "constraints.min (10) must be <= constraints.max (5)")
    }

    @Test
    fun `Constraints min equals max is valid`() {
        val constraints = Constraints(min = 5, max = 5)
        assertEquals(5L, constraints.min)
        assertEquals(5L, constraints.max)
    }

    @Test
    fun `Constraints only min is valid`() {
        val constraints = Constraints(min = 1)
        assertEquals(1L, constraints.min)
        assertNull(constraints.max)
    }

    @Test
    fun `Constraints only max is valid`() {
        val constraints = Constraints(max = 100)
        assertNull(constraints.min)
        assertEquals(100L, constraints.max)
    }

    // --- Governance ---

    @Test
    fun `Governance guardrail mode requires constraints`() {
        val exception = runCatching { Governance(mode = GovernanceMode.GUARDRAIL) }.exceptionOrNull()
        assertNotNull(exception)
        assertContains(exception.message!!, "governance mode 'guardrail' requires constraints")
    }

    @Test
    fun `Governance guardrail with constraints succeeds`() {
        val governance = Governance(mode = GovernanceMode.GUARDRAIL, constraints = Constraints(min = 1, max = 10))
        assertEquals(GovernanceMode.GUARDRAIL, governance.mode)
        assertNotNull(governance.constraints)
    }

    @Test
    fun `Governance constraints only valid with guardrail`() {
        val exception = runCatching { Governance(mode = GovernanceMode.MANDATORY, constraints = Constraints(min = 1)) }.exceptionOrNull()
        assertNotNull(exception)
        assertContains(exception.message!!, "governance.constraints is only valid with mode 'guardrail'")
    }

    @Test
    fun `Governance mandatory without constraints succeeds`() {
        val governance = Governance(mode = GovernanceMode.MANDATORY)
        assertEquals(GovernanceMode.MANDATORY, governance.mode)
        assertNull(governance.constraints)
    }

    @Test
    fun `Governance default mode succeeds without constraints`() {
        val governance = Governance(mode = GovernanceMode.DEFAULT)
        assertEquals(GovernanceMode.DEFAULT, governance.mode)
        assertNull(governance.constraints)
    }

    // --- ParameterDeclaration ---

    @Test
    fun `ParameterDeclaration integer type with matching int default succeeds`() {
        val declaration = ParameterDeclaration(type = ParameterType.INTEGER, default = ParameterValue.IntValue(42))
        assertEquals(ParameterType.INTEGER, declaration.type)
        assertEquals(ParameterValue.IntValue(42), declaration.default)
    }

    @Test
    fun `ParameterDeclaration integer type with mismatched string default throws`() {
        val exception = runCatching {
            ParameterDeclaration(type = ParameterType.INTEGER, default = ParameterValue.StringValue("forty-two"))
        }.exceptionOrNull()
        assertNotNull(exception)
        assertContains(exception.message!!, "does not match declared type")
    }

    @Test
    fun `ParameterDeclaration boolean type with matching bool default succeeds`() {
        val declaration = ParameterDeclaration(type = ParameterType.BOOLEAN, default = ParameterValue.BoolValue(true))
        assertEquals(ParameterType.BOOLEAN, declaration.type)
    }

    @Test
    fun `ParameterDeclaration string type with matching string default succeeds`() {
        val declaration = ParameterDeclaration(type = ParameterType.STRING, default = ParameterValue.StringValue("hello"))
        assertEquals(ParameterType.STRING, declaration.type)
    }

    @Test
    fun `ParameterDeclaration with no default succeeds`() {
        val declaration = ParameterDeclaration(type = ParameterType.INTEGER)
        assertNull(declaration.default)
    }

    @Test
    fun `ParameterDeclaration with governance succeeds`() {
        val governance = Governance(mode = GovernanceMode.GUARDRAIL, constraints = Constraints(min = 1, max = 10))
        val declaration = ParameterDeclaration(type = ParameterType.INTEGER, governance = governance)
        assertEquals(governance, declaration.governance)
    }

    // --- ResourceKind ---

    @Test
    fun `ResourceKind fromWire known kind returns enum entry`() {
        assertEquals(ResourceKind.PIPELINE_DEFINITION, ResourceKind.fromWire("PipelineDefinition"))
        assertEquals(ResourceKind.PIPELINE_PROFILE, ResourceKind.fromWire("PipelineProfile"))
    }

    @Test
    fun `ResourceKind fromWire unknown kind returns null`() {
        assertNull(ResourceKind.fromWire("UnknownKind"))
        assertNull(ResourceKind.fromWire(""))
        assertNull(ResourceKind.fromWire("pipeline_definition"))
    }

    // --- ParseResult ---

    @Test
    fun `ParseResult hasErrors returns true when ERROR diagnostic present`() {
        val diagnostic = Diagnostic(
            code = DiagnosticCode("TEST-001"),
            severity = DiagnosticSeverity.ERROR,
            message = "test error",
        )
        val result = ParseResult(resources = emptyList(), diagnostics = listOf(diagnostic))
        assertTrue(result.hasErrors)
    }

    @Test
    fun `ParseResult hasErrors returns false when only WARNING present`() {
        val diagnostic = Diagnostic(
            code = DiagnosticCode("TEST-002"),
            severity = DiagnosticSeverity.WARNING,
            message = "test warning",
        )
        val result = ParseResult(resources = emptyList(), diagnostics = listOf(diagnostic))
        assertFalse(result.hasErrors)
    }

    @Test
    fun `ParseResult hasErrors returns false when no diagnostics`() {
        val result = ParseResult(resources = emptyList(), diagnostics = emptyList())
        assertFalse(result.hasErrors)
    }

    @Test
    fun `ParseResult failed creates empty resources with diagnostics`() {
        val diagnostic = Diagnostic(
            code = DiagnosticCode("TEST-003"),
            severity = DiagnosticSeverity.ERROR,
            message = "test failure",
        )
        val result = ParseResult.failed(listOf(diagnostic))
        assertTrue(result.resources.isEmpty())
        assertEquals(1, result.diagnostics.size)
        assertEquals("test failure", result.diagnostics[0].message)
    }

    // --- ParameterValue sealed hierarchy ---

    @Test
    fun `ParameterValue sealed hierarchy is exhaustive`() {
        val intValue: ParameterValue = ParameterValue.IntValue(42L)
        val boolValue: ParameterValue = ParameterValue.BoolValue(true)
        val stringValue: ParameterValue = ParameterValue.StringValue("test")

        assertIs<ParameterValue.IntValue>(intValue)
        assertIs<ParameterValue.BoolValue>(boolValue)
        assertIs<ParameterValue.StringValue>(stringValue)

        assertEquals(42L, intValue.value)
        assertEquals(true, boolValue.value)
        assertEquals("test", stringValue.value)
    }
}
