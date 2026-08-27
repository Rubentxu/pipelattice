package dev.rubentxu.pipelattice.architecture

/**
 * Synthetic fixture for the FARCH-016 v2 RED regression test.
 *
 * Contains a method that calls `kotlin.system.exitProcess`, which the Kotlin
 * compiler rewrites to `java.lang.System.exit(int)` at byte-code level.
 *
 * This class is intentionally placed in the test source set so that production
 * bytecode scans (which use `DoNotIncludeTests`) do not flag it; only the
 * explicit import in `Farch016RedRegressionTest` exercises the rule against it.
 *
 * Cross-ref: m12-farch016-v2-cleanup R1 closure.
 */
internal class SyntheticSystemExitViolator {
    fun exitsImmediately(): Nothing = kotlin.system.exitProcess(99)
}
