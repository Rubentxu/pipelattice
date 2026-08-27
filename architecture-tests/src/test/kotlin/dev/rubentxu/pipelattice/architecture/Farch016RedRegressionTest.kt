package dev.rubentxu.pipelattice.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.EvaluationResult
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes

/**
 * RED regression test for the FARCH-016 v2 byte-code System.exit guard.
 *
 * This test proves the rule actually FIRES when a non-sanctioned class calls
 * `java.lang.System.exit` at byte-code level (the same pattern that
 * `kotlin.system.exitProcess` rewrites to after compilation).
 *
 * Test strategy:
 *  1. Create the condition with a non-empty sanctions set that does NOT include the
 *     synthetic violator (emptySet() is rejected by the condition's init block).
 *  2. Import the synthetic fixture class (`SyntheticSystemExitViolator`) which
 *     calls `kotlin.system.exitProcess` in one of its methods.
 *  3. Build an ArchRule that requires the synthetic class NOT to call System.exit.
 *  4. Evaluate the rule (does not throw on violation) and assert it DID fire.
 *
 * If the condition's byte-code inspection logic regresses (e.g. stops looking at
 * `methodCallsFromSelf`, or stops matching `java.lang.System#exit`, or flips the
 * violation flag in `SimpleConditionEvent`), this test fails loudly.
 *
 * Cross-ref: m12-farch016-v2-cleanup R1 closure; spec scenario
 * `Sc_F016v2_bytecode_guard`.
 *
 * Note: this test class deliberately does NOT use `DoNotIncludeTests` import
 * option so that the synthetic test fixture (`SyntheticSystemExitViolator`) is
 * imported alongside the rest of the bytecode.
 */
@AnalyzeClasses(
    packages = ["dev.rubentxu.pipelattice.architecture"],
    importOptions = [ImportOption.DoNotIncludeJars::class],
)
class Farch016RedRegressionTest {

    @ArchTest
    fun `RED regression - FARCH-016 v2 condition fires on synthetic System exit violator`(
        imported: JavaClasses,
    ) {
        // Arrange: condition with a NON-EMPTY sanctions set that does NOT include the
        // synthetic violator (emptySet() is rejected by the condition's init block).
        val condition = NoSystemExitCallCondition.createWithCustomSanctions(
            setOf("some.unrelated.ClassThatDoesNotExist"),
        )

        // Build an ArchRule that requires the synthetic class NOT to call System.exit
        val rule: ArchRule = classes()
            .that().haveSimpleName("SyntheticSystemExitViolator")
            .should(condition)
            .because("FARCH-016 v2 must fire on any non-sanctioned System.exit call")

        // Act: evaluate the rule (does NOT throw on violation, returns EvaluationResult)
        val evaluation: EvaluationResult = rule.evaluate(imported)

        // Assert: the rule fired on the synthetic violator
        check(evaluation.hasViolation()) {
            "Expected FARCH-016 v2 condition to fire on the synthetic System.exit violator; " +
                "instead the rule was silent. The byte-code inspection may have regressed.\n" +
                "Failure report: ${evaluation.failureReport}"
        }
    }
}
