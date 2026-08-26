package dev.rubentxu.pipelattice.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * M0 architecture fitness rules (ids per pipelattice-spec/docs/12_TESTING_FITNESS.md §5).
 *
 * These are the constraints that must exist BEFORE functionality grows. Rules that cannot
 * bite yet (e.g. provider isolation, FARCH-007) are added together with the code they guard.
 */
@AnalyzeClasses(
    packages = ["dev.rubentxu.pipelattice"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class ArchitectureFitnessTest {

    @ArchTest
    fun `FARCH-003 - pipelattice does not depend on pipeline-kotlin`(imported: JavaClasses) {
        rule(
            noClasses().that().resideInAPackage("dev.rubentxu.pipelattice..")
                .should().dependOnClassesThat().resideInAnyPackage("dev.rubentxu.pipeline.."),
            "Pipelattice must never import its runtime (ADR-0006)",
        ).check(imported)
    }

    @ArchTest
    fun `FARCH-004 - no Jenkins or Groovy in core`(imported: JavaClasses) {
        rule(
            noClasses().that().resideInAPackage("dev.rubentxu.pipelattice..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("jenkins..", "org.jenkinsci..", "groovy..", "org.codehaus.groovy.."),
            "Domain stays framework-free (ADR-0001)",
        ).check(imported)
    }

    @ArchTest
    fun `FARCH-005 - no reflection based DI containers`(imported: JavaClasses) {
        rule(
            noClasses().that().resideInAPackage("dev.rubentxu.pipelattice..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "com.google.inject..",
                    "org.springframework..",
                    "javax.inject..",
                    "jakarta.inject..",
                ),
            "Wiring is explicit at the composition root (ADR-0008)",
        ).check(imported)
    }

    @ArchTest
    fun `FARCH-006 - no service locator types`(imported: JavaClasses) {
        rule(
            noClasses().that().resideInAPackage("dev.rubentxu.pipelattice..")
                .should().haveNameMatching(".*ServiceLocator.*"),
            "No global context, no service locator (README principles 11)",
        ).check(imported)
    }

    @ArchTest
    fun `foundation has no outward dependencies within pipelattice`(imported: JavaClasses) {
        rule(
            noClasses().that().resideInAPackage("dev.rubentxu.pipelattice.foundation..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "dev.rubentxu.pipelattice.testkit..",
                    "dev.rubentxu.pipelattice.architecture..",
                ),
            "foundation is the innermost layer",
        ).check(imported)
    }

    @ArchTest
    fun `testkit depends only on foundation inside pipelattice plus jdk and stdlib`(imported: JavaClasses) {
        rule(
            classes().that().resideInAPackage("dev.rubentxu.pipelattice.testkit..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                    "java..",
                    "javax..",
                    "kotlin..",
                    // Kotlin compiler emits org.jetbrains.annotations.NotNull into bytecode.
                    "org.jetbrains.annotations..",
                    "dev.rubentxu.pipelattice.foundation..",
                    "dev.rubentxu.pipelattice.testkit..",
                ),
            "testkit is a leaf that only knows the public model",
        ).check(imported)
    }

    private fun rule(definition: ArchRule, because: String): ArchRule = definition.because(because)
}
