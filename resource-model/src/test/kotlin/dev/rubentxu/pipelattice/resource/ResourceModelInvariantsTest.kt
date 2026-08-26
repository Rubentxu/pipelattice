package dev.rubentxu.pipelattice.resource

import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticCode
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity
import org.junit.jupiter.api.Nested
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Invariant tests for the resource-model value classes.
 * Each test documents a constraint that MUST hold for the model to be correct.
 */
class ResourceModelInvariantsTest {

    // -------------------------------------------------------------------------
    // REQ: Resource-Envelope-Per-Kind — ApiVersion and Metadata invariants
    // -------------------------------------------------------------------------

    @Nested
    inner class ApiVersionInvariants {
        @Test
        fun `blank value throws`() {
            val exception = runCatching { ApiVersion("") }.exceptionOrNull()
            assertNotNull(exception)
            assertContains(exception.message!!, "apiVersion must not be blank")
        }

        @Test
        fun `missing slash throws`() {
            val exception = runCatching { ApiVersion("v1alpha1") }.exceptionOrNull()
            assertNotNull(exception)
            assertContains(exception.message!!, "must have the form '<group>/<version>'")
        }

        @Test
        fun `space in value throws`() {
            val exception = runCatching { ApiVersion("pipelattice dev/v1alpha1") }.exceptionOrNull()
            assertNotNull(exception)
            assertContains(exception.message!!, "must have the form '<group>/<version>'")
        }

        @Test
        fun `KNOWN is recognized`() {
            assertTrue(ApiVersion.KNOWN.isKnown)
            assertEquals("pipelattice.dev/v1alpha1", ApiVersion.KNOWN.value)
        }

        @Test
        fun `toString returns the value`() {
            assertEquals("pipelattice.dev/v1alpha1", ApiVersion.KNOWN.toString())
        }
    }

    @Nested
    inner class MetadataInvariants {
        @Test
        fun `name blank throws`() {
            val exception = runCatching { Metadata(name = "") }.exceptionOrNull()
            assertNotNull(exception)
            assertContains(exception.message!!, "metadata.name must not be blank")
        }

        @Test
        fun `all fields succeeds`() {
            val metadata = Metadata(name = "my-resource", version = "1.0.0", labels = mapOf("env" to "prod"))
            assertEquals("my-resource", metadata.name)
            assertEquals("1.0.0", metadata.version)
            assertEquals(mapOf("env" to "prod"), metadata.labels)
        }

        @Test
        fun `version is null by default`() {
            val metadata = Metadata(name = "my-resource")
            assertNull(metadata.version)
        }

        @Test
        fun `labels empty map is valid`() {
            val metadata = Metadata(name = "my-resource", labels = emptyMap())
            assertEquals(emptyMap(), metadata.labels)
        }

        @Test
        fun `multiple labels are preserved`() {
            val labels = mapOf("env" to "prod", "team" to "platform", "criticality" to "high")
            val metadata = Metadata(name = "my-resource", labels = labels)
            assertEquals(3, metadata.labels.size)
            assertEquals("prod", metadata.labels["env"])
            assertEquals("platform", metadata.labels["team"])
            assertEquals("high", metadata.labels["criticality"])
        }
    }

    // -------------------------------------------------------------------------
    // REQ: Governance-Modes-And-Constraints — Constraints + Governance
    // -------------------------------------------------------------------------

    @Nested
    inner class ConstraintsInvariants {
        @Test
        fun `min greater than max throws`() {
            val exception = runCatching { Constraints(min = 10, max = 5) }.exceptionOrNull()
            assertNotNull(exception)
            assertContains(exception.message!!, "constraints.min (10) must be <= constraints.max (5)")
        }

        @Test
        fun `min equals max is valid`() {
            val constraints = Constraints(min = 5, max = 5)
            assertEquals(5L, constraints.min)
            assertEquals(5L, constraints.max)
        }

        @Test
        fun `only min is valid`() {
            val constraints = Constraints(min = 1)
            assertEquals(1L, constraints.min)
            assertNull(constraints.max)
        }

        @Test
        fun `only max is valid`() {
            val constraints = Constraints(max = 100)
            assertNull(constraints.min)
            assertEquals(100L, constraints.max)
        }

        @Test
        fun `both null is valid`() {
            val constraints = Constraints()
            assertNull(constraints.min)
            assertNull(constraints.max)
        }
    }

    @Nested
    inner class GovernanceInvariants {
        @Test
        fun `guardrail mode requires constraints`() {
            val exception = runCatching { Governance(mode = GovernanceMode.GUARDRAIL) }.exceptionOrNull()
            assertNotNull(exception)
            assertContains(exception.message!!, "governance mode 'guardrail' requires constraints")
        }

        @Test
        fun `guardrail with constraints succeeds`() {
            val governance = Governance(mode = GovernanceMode.GUARDRAIL, constraints = Constraints(min = 1, max = 10))
            assertSame(GovernanceMode.GUARDRAIL, governance.mode)
            val constraints = assertNotNull(governance.constraints)
            assertEquals(1L, constraints.min)
            assertEquals(10L, constraints.max)
        }

        @Test
        fun `constraints without guardrail throws`() {
            val exception = runCatching {
                Governance(mode = GovernanceMode.MANDATORY, constraints = Constraints(min = 1))
            }.exceptionOrNull()
            assertNotNull(exception)
            assertContains(exception.message!!, "governance.constraints is only valid with mode 'guardrail'")
        }

        @Test
        fun `mandatory without constraints succeeds`() {
            val governance = Governance(mode = GovernanceMode.MANDATORY)
            assertSame(GovernanceMode.MANDATORY, governance.mode)
            assertNull(governance.constraints)
        }

        @Test
        fun `default mode succeeds without constraints`() {
            val governance = Governance(mode = GovernanceMode.DEFAULT)
            assertSame(GovernanceMode.DEFAULT, governance.mode)
            assertNull(governance.constraints)
        }

        @Test
        fun `default governance is DEFAULT with null constraints`() {
            val governance = Governance()
            assertSame(GovernanceMode.DEFAULT, governance.mode)
            assertNull(governance.constraints)
        }
    }

    // -------------------------------------------------------------------------
    // REQ: Typed-Parameter-Values — ParameterDeclaration + ParameterValue
    // -------------------------------------------------------------------------

    @Nested
    inner class ParameterDeclarationInvariants {
        @Test
        fun `integer type with matching int default succeeds`() {
            val declaration = ParameterDeclaration(
                type = ParameterType.INTEGER,
                default = ParameterValue.IntValue(42),
            )
            assertSame(ParameterType.INTEGER, declaration.type)
            assertEquals(ParameterValue.IntValue(42), declaration.default)
        }

        @Test
        fun `integer type with mismatched string default throws`() {
            val exception = runCatching {
                ParameterDeclaration(type = ParameterType.INTEGER, default = ParameterValue.StringValue("forty-two"))
            }.exceptionOrNull()
            assertNotNull(exception)
            assertContains(exception.message!!, "does not match declared type")
        }

        @Test
        fun `boolean type with matching bool default succeeds`() {
            val declaration = ParameterDeclaration(
                type = ParameterType.BOOLEAN,
                default = ParameterValue.BoolValue(true),
            )
            assertSame(ParameterType.BOOLEAN, declaration.type)
        }

        @Test
        fun `string type with matching string default succeeds`() {
            val declaration = ParameterDeclaration(
                type = ParameterType.STRING,
                default = ParameterValue.StringValue("hello"),
            )
            assertSame(ParameterType.STRING, declaration.type)
        }

        @Test
        fun `no default is valid for any type`() {
            ParameterType.entries.forEach { type ->
                val declaration = ParameterDeclaration(type = type)
                assertNull(declaration.default)
            }
        }

        @Test
        fun `with governance succeeds`() {
            val governance = Governance(mode = GovernanceMode.GUARDRAIL, constraints = Constraints(min = 1, max = 10))
            val declaration = ParameterDeclaration(type = ParameterType.INTEGER, governance = governance)
            assertEquals(governance, declaration.governance)
        }
    }

    @Nested
    inner class ParameterValueSealedHierarchy {
        @Test
        fun `IntValue holds a Long`() {
            val value: ParameterValue = ParameterValue.IntValue(42L)
            assertIs<ParameterValue.IntValue>(value)
            assertEquals(42L, value.value)
        }

        @Test
        fun `BoolValue holds a Boolean`() {
            val value: ParameterValue = ParameterValue.BoolValue(false)
            assertIs<ParameterValue.BoolValue>(value)
            assertFalse(value.value)
        }

        @Test
        fun `StringValue holds a String`() {
            val value: ParameterValue = ParameterValue.StringValue("hello world")
            assertIs<ParameterValue.StringValue>(value)
            assertEquals("hello world", value.value)
        }

        @Test
        fun `sealed hierarchy is exhaustive via when`() {
            val intValue = ParameterValue.IntValue(1L)
            val boolValue = ParameterValue.BoolValue(true)
            val stringValue = ParameterValue.StringValue("x")

            val descriptions = listOf(intValue, boolValue, stringValue).map { pv ->
                when (pv) {
                    is ParameterValue.IntValue -> "int:${pv.value}"
                    is ParameterValue.BoolValue -> "bool:${pv.value}"
                    is ParameterValue.StringValue -> "str:${pv.value}"
                }
            }
            assertEquals(listOf("int:1", "bool:true", "str:x"), descriptions)
        }
    }

    // -------------------------------------------------------------------------
    // REQ: Resource-Envelope-Per-Kind — ResourceKind enum invariants
    // -------------------------------------------------------------------------

    @Nested
    inner class ResourceKindInvariants {
        @Test
        fun `known kinds are recognized`() {
            assertEquals(ResourceKind.PIPELINE_DEFINITION, ResourceKind.fromWire("PipelineDefinition"))
            assertEquals(ResourceKind.PIPELINE_PROFILE, ResourceKind.fromWire("PipelineProfile"))
        }

        @Test
        fun `unknown kind returns null`() {
            assertNull(ResourceKind.fromWire("UnknownKind"))
            assertNull(ResourceKind.fromWire(""))
            assertNull(ResourceKind.fromWire("pipeline_definition"))
            assertNull(ResourceKind.fromWire("PipelineDefinitionX"))
        }

        @Test
        fun `wireName matches expected string`() {
            assertEquals("PipelineDefinition", ResourceKind.PIPELINE_DEFINITION.wireName)
            assertEquals("PipelineProfile", ResourceKind.PIPELINE_PROFILE.wireName)
        }
    }

    // -------------------------------------------------------------------------
    // REQ: Resource-Envelope-Per-Kind — ParseResult invariants
    // -------------------------------------------------------------------------

    @Nested
    inner class ParseResultInvariants {
        @Test
        fun `hasErrors true when ERROR diagnostic present`() {
            val diagnostic = Diagnostic(
                code = DiagnosticCode("TEST-001"),
                severity = DiagnosticSeverity.ERROR,
                message = "test error",
            )
            val result = ParseResult(resources = emptyList(), diagnostics = listOf(diagnostic))
            assertTrue(result.hasErrors)
        }

        @Test
        fun `hasErrors false when only WARNING present`() {
            val diagnostic = Diagnostic(
                code = DiagnosticCode("TEST-002"),
                severity = DiagnosticSeverity.WARNING,
                message = "test warning",
            )
            val result = ParseResult(resources = emptyList(), diagnostics = listOf(diagnostic))
            assertFalse(result.hasErrors)
        }

        @Test
        fun `hasErrors false when no diagnostics`() {
            val result = ParseResult(resources = emptyList(), diagnostics = emptyList())
            assertFalse(result.hasErrors)
        }

        @Test
        fun `failed creates empty resources with diagnostics`() {
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

        @Test
        fun `failed with multiple diagnostics preserves all`() {
            val diagnostics = listOf(
                Diagnostic(DiagnosticCode("TEST-004"), DiagnosticSeverity.ERROR, "error one"),
                Diagnostic(DiagnosticCode("TEST-005"), DiagnosticSeverity.ERROR, "error two"),
            )
            val result = ParseResult.failed(diagnostics)
            assertTrue(result.resources.isEmpty())
            assertEquals(2, result.diagnostics.size)
        }
    }
}
