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

    @ArchTest
    fun `FARCH-010 - resource-model has no YAML or JSON adapter dependencies`(imported: JavaClasses) {
        rule(
            noClasses().that().resideInAPackage("dev.rubentxu.pipelattice.resource..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                    "org.snakeyaml..",
                    "com.charleskorn.kaml..",
                    "com.fasterxml.jackson..",
                ),
            "resource-model must be YAML/JACKSON-agnostic (ADR-0021)",
        ).check(imported)
    }

    @ArchTest
    fun `FARCH-011 - pipeline-compose is YAML JSON GIT serialization-free except M1CatalogSource`(imported: JavaClasses) {
        val rule = noClasses()
            .that().resideInAPackage("dev.rubentxu.pipelattice.compose..")
            .and().resideOutsideOfPackage("dev.rubentxu.pipelattice.compose.compose..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.snakeyaml..",
                "com.charleskorn.kaml..",
                "com.fasterxml.jackson..",
                "com.google.gson..",
                "org.eclipse.jgit..",
                "kotlinx.serialization..",
            )
        rule.allowEmptyShould(true)
        rule.because("pipeline-compose must not depend on YAML, JSON, GIT, or serialization libraries (FARCH-011)").check(imported)
    }

    @ArchTest
    fun `FARCH-012 - policy-engine is YAML JSON GIT serialization-free`(imported: JavaClasses) {
        val rule = noClasses()
            .that().resideInAPackage("dev.rubentxu.pipelattice.policy..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.snakeyaml..",
                "com.charleskorn.kaml..",
                "com.fasterxml.jackson..",
                "com.google.gson..",
                "org.eclipse.jgit..",
                "kotlinx.serialization..",
            )
        rule.allowEmptyShould(true)
        rule.because("policy-engine must not depend on YAML, JSON, GIT, or serialization libraries (FARCH-012)").check(imported)
    }

    @ArchTest
    fun `FARCH-013 - build-engine is ProcessBuilder and Runtime dot exec free`(imported: JavaClasses) {
        val rule = noClasses()
            .that().resideInAPackage("dev.rubentxu.pipelattice.build..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "java.lang.ProcessBuilder..",
                "java.lang.Runtime..",
                "java.lang.Process..",
                "kotlin.system..",
                "org.apache.tools.ant.taskdefs.Execute..",
            )
        rule.allowEmptyShould(true)
        rule.because("build-engine must not depend on ProcessBuilder, Runtime.exec, or System.getenv (FARCH-013)").check(imported)
    }

    @ArchTest
    fun `FARCH-014 - provider-gradle is ProcessBuilder and Runtime dot exec free`(imported: JavaClasses) {
        val rule = noClasses()
            .that().resideInAPackage("dev.rubentxu.pipelattice.provider.gradle..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "java.lang.ProcessBuilder..",
                "java.lang.Runtime..",
                "java.lang.Process..",
                "kotlin.system..",
                "org.apache.tools.ant.taskdefs.Execute..",
            )
        rule.allowEmptyShould(true)
        rule.because(
            "provider-gradle must execute gradle processes only via the ProcessRunner port " +
                "consumed from :build-engine (FARCH-014); direct ProcessBuilder use would " +
                "violate the abstraction proof and break provider substitution (ADR-0001, " +
                "S-012 kill condition: no when(provider) in application core)",
        ).check(imported)
    }

    @ArchTest
    fun `FARCH-015 - graph-projection is graph-DB and ORM free`(imported: JavaClasses) {
        val rule = noClasses()
            .that().resideInAPackage("dev.rubentxu.pipelattice.graph..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                // Graph databases
                "org.jgrapht..",
                "org.neo4j..",
                "com.tinkerpop..",
                "com.orientechnologies..",
                // ORMs (would imply persistent storage)
                "jakarta.persistence..",
                "javax.persistence..",
                "org.hibernate..",
                "androidx.room..",
                "android.arch.persistence.room..",
            )
        rule.allowEmptyShould(true)
        rule.because(
            "graph-projection must be in-memory only (V1 per spec 04 §11 + ADR-0014); " +
                "any graph-database or ORM dependency would violate the V1 persistence " +
                "decision and pre-commit to scale that has not been measured. SHA-256 + " +
                "java.util collections are sufficient for V1 (FARCH-015).",
        ).check(imported)
    }

    @ArchTest
    fun `FARCH-016 - fleet-diff is ProcessBuilder and Runtime dot exec free`(imported: JavaClasses) {
        val rule = noClasses()
            .that().resideInAPackage("dev.rubentxu.pipelattice.fleet.diff..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "java.lang.ProcessBuilder..",
                "java.lang.Runtime..",
                "java.lang.Process..",
                "kotlin.system..",
                "org.apache.tools.ant.taskdefs.Execute..",
            )
        rule.allowEmptyShould(true)
        rule.because(
            "fleet-diff must execute git processes only via the ProcessRunner port " +
                "consumed from :build-engine (FARCH-016); direct ProcessBuilder use would " +
                "violate the abstraction proof (ADR-0026 docsync permanent reservation).",
        ).check(imported)
    }

    private fun rule(definition: ArchRule, because: String): ArchRule = definition.because(because)
}
