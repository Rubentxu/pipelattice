package dev.rubentxu.pipelattice.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent

/**
 * A custom ArchCondition that checks whether a [JavaClass] contains any method body
 * that calls `java.lang.System.exit(int)` outside of sanctioned entry points.
 *
 * ## Byte-code level guard
 * Kotlin's `kotlin.system.exitProcess(Int)` is compiled to `INVOKESTATIC java/lang/System.exit(I)V`
 * at the byte-code level. This condition detects that call regardless of the source syntax.
 *
 * ## Sanctioned FQNs
 * Two FQNs are explicitly sanctioned because they are the CLI entry points where JVM termination
 * is the correct behavior:
 * - [SANCTIONED_FQNS]
 *
 * All other classes in the scanned package must NOT contain `System.exit` calls.
 *
 * ## Cross-references
 * - INC-010: Main.main and MainKt.main are the only sanctioned System.exit call sites
 * - INC-008: fleet-diff CLI entry points documented at Main.kt:40, Main.kt:54-56, Main.kt:180-182
 *
 * @see ArchitectureFitnessTest for the FARCH-016 v2 ArchTest that uses this condition
 */
class NoSystemExitCallCondition private constructor(
    private val sanctionedFqns: Set<String>,
) : ArchCondition<JavaClass>("not call System.exit outside sanctioned entry points") {

    init {
        require(sanctionedFqns.isNotEmpty()) {
            "NoSystemExitCallCondition requires at least one sanctioned FQN"
        }
    }

    override fun check(item: JavaClass, events: ConditionEvents) {
        if (item.fullName in sanctionedFqns) {
            return // Sanctioned - allow
        }

        for (method in item.methods) {
            checkMethodForSystemExit(method, item, events)
        }
    }

    private fun checkMethodForSystemExit(method: JavaMethod, owner: JavaClass, events: ConditionEvents) {
        val callsFromSelf = method.methodCallsFromSelf
        for (call in callsFromSelf) {
            val target = call.target
            val targetClass = target.owner
            val targetName = target.name

            // Check for java.lang.System.exit(int)
            if (targetClass.fullName == "java.lang.System" && targetName == "exit") {
                val message = buildString {
                    append("${owner.fullName}.${method.name}() ")
                    append("calls System.exit which terminates the JVM. ")
                    append("Only CLI entry points (${sanctionedFqns.first()}, ${sanctionedFqns.last()}) ")
                    append("may call System.exit; all other code must return instead. ")
                    append("See INC-010 and INC-008.")
                }
                // true = IS a violation
                events.add(SimpleConditionEvent(owner, true, message))
            }
        }
    }

    companion object {
        /**
         * The set of FQNs whose `System.exit` byte-code calls are sanctioned.
         *
         * These are the CLI entry points where JVM termination is the correct behavior:
         * - `dev.rubentxu.pipelattice.fleet.diff.cli.Main`
         * - `dev.rubentxu.pipelattice.fleet.diff.cli.MainKt`
         *
         * Cross-ref: INC-010 (sanctioned entries) and INC-008 (CLI entry point documentation).
         */
        val SANCTIONED_FQNS: Set<String> = setOf(
            "dev.rubentxu.pipelattice.fleet.diff.cli.Main",
            "dev.rubentxu.pipelattice.fleet.diff.cli.MainKt",
        )

        /**
         * Creates a new [NoSystemExitCallCondition] with the standard sanctioned FQNs.
         */
        fun create(): NoSystemExitCallCondition = NoSystemExitCallCondition(SANCTIONED_FQNS)

        /**
         * Creates a new [NoSystemExitCallCondition] with custom sanctioned FQNs (for testing).
         */
        fun createWithCustomSanctions(customSanctionedFqns: Set<String>): NoSystemExitCallCondition =
            NoSystemExitCallCondition(customSanctionedFqns)
    }
}
