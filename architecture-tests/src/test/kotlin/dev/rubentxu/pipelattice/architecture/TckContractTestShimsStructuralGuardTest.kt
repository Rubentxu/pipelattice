package dev.rubentxu.pipelattice.architecture

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S17: TCK shim structural discipline guard (v6 spec).
 *
 * Verifies:
 * - Zero @Test annotations across all 6 shim files (3 fakes + 3 reals)
 * - Fake shims ≤ 25 LOC (body only, from first override)
 * - Real-adapter shims ≤ 200 LOC (body only, from first override)
 * - PLUS clause (spec v5/v6): real-adapter shims may ONLY override
 *   `invariant_invocations_stable` (the fake-only invariant per the
 *   real-adapter applicability matrix). Any other `override fun invariant_*`
 *   in a real shim is a spec violation.
 * - Gate-set equality (spec v6): the set of contract invariants gated by
 *   `assumeTrue(<gate-hook>, ...)` in real shims must be EXACTLY the
 *   spec-v6 matrix's per-contract gated-fake-only set.
 *
 * Per spec v4 acceptance #22, spec v5 S17 PLUS clause, and spec v6 S17
 * gate-set equality loophole closure.
 */
class TckContractTestShimsStructuralGuardTest {

    private val FAKE_LOC_LIMIT = 25
    private val REAL_ADAPTER_LOC_LIMIT = 200

    // Paths relative to project root (architecture-tests is at the root level)
    private val fakeShims = listOf(
        "release-engine/src/test/kotlin/dev/rubentxu/pipelattice/release/scm/FakeScmSourceContractTest.kt",
        "release-engine/src/test/kotlin/dev/rubentxu/pipelattice/release/artifact/FakeArtifactRepositoryContractTest.kt",
        "release-engine/src/test/kotlin/dev/rubentxu/pipelattice/release/release/FakeReleaseManagerContractTest.kt",
    )

    private val realAdapterShims = listOf(
        "release-engine-adapters/src/test/kotlin/dev/rubentxu/pipelattice/release/adapter/scm/JGitScmSourceContractTest.kt",
        "release-engine-adapters/src/test/kotlin/dev/rubentxu/pipelattice/release/adapter/artifact/LocalFSArtifactRepositoryContractTest.kt",
        "release-engine-adapters/src/test/kotlin/dev/rubentxu/pipelattice/release/adapter/release/GitTagBasedReleaseManagerContractTest.kt",
    )

    // Paths to the 3 contract files (relative to project root)
    private val contractFiles = listOf(
        "release-engine/src/main/kotlin/dev/rubentxu/pipelattice/release/contract/ScmSourceContract.kt",
        "release-engine/src/main/kotlin/dev/rubentxu/pipelattice/release/contract/ArtifactRepositoryContract.kt",
        "release-engine/src/main/kotlin/dev/rubentxu/pipelattice/release/contract/ReleaseManagerContract.kt",
    )

    // Per spec v5/v6 real-adapter applicability matrix: ONLY this invariant is fake-only via override
    private val ALLOWED_REAL_SHIM_OVERRIDE = setOf("invariant_invocations_stable")

    // Per spec v6 matrix: gated invariants by contract (cited: spec v6 §"Real-adapter applicability matrix")
    // ScmSourceContract: 3 gated via supportsQueueBasedScripting
    // ArtifactRepositoryContract: 3 gated via supportsQueueBasedScripting
    // ReleaseManagerContract: 2 gated via supportsQueueBasedScripting + 1 gated via supportsRejectionTest
    // Total: 9 gated invariants across all contracts
    private val SPEC_V6_GATED_SET = setOf(
        // ScmSourceContract - gated by supportsQueueBasedScripting
        "invariant_checkout_empty_raises",
        "invariant_tag_empty_raises",
        "invariant_push_empty_raises",
        // ArtifactRepositoryContract - gated by supportsQueueBasedScripting
        "invariant_publish_empty_raises",
        "invariant_resolve_empty_raises",
        "invariant_download_empty_raises",
        // ReleaseManagerContract - gated by supportsQueueBasedScripting
        "invariant_calculate_empty_raises",
        "invariant_promote_empty_raises",
        // ReleaseManagerContract - gated by supportsRejectionTest
        "invariant_promote_rejected",
    )

    private val projectRoot = File("..").absoluteFile

    /**
     * Strips Kotlin comments (line comments // and block comments /* */) from source code
     * before checking for annotations.
     */
    private fun stripComments(source: String): String {
        // Remove block comments /* ... */
        val withoutBlockComments = source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        // Remove line comments //
        val withoutLineComments = withoutBlockComments.replace(Regex("//.*"), "")
        return withoutLineComments
    }

    @Test
    fun `zero_test_annotations_and_loc_caps`() {
        // Check zero @Test annotations across all 6 shim files
        // First strip comments to avoid counting mentions in documentation
        val allShims = fakeShims + realAdapterShims
        for (shimPath in allShims) {
            val file = File(projectRoot, shimPath)
            assertTrue(file.exists(), "Shim not found: ${file.absolutePath}")
            val content = stripComments(file.readText())
            // Count actual @Test annotations (followed by space, newline, or opening paren)
            val testAnnotationCount = "@Test".toRegex().findAll(content).count()
            assertEquals(
                0, testAnnotationCount,
                "Shim $shimPath must have ZERO @Test annotations but found $testAnnotationCount. " +
                "All TCK invariants must be declared in the abstract contract class only."
            )
        }

        // Check fake shims LOC cap
        for (shimPath in fakeShims) {
            val file = File(projectRoot, shimPath)
            val lines = file.readLines()
            val firstOverrideIdx = lines.indexOfFirst { it.trim().startsWith("override") }
            assertTrue(firstOverrideIdx >= 0, "No override found in $shimPath")
            val bodyLines = lines.drop(firstOverrideIdx)
            val nonEmptyBodyLines = bodyLines.count { it.trim().isNotEmpty() }
            assertTrue(
                nonEmptyBodyLines <= FAKE_LOC_LIMIT,
                "Fake shim $shimPath body is $nonEmptyBodyLines LOC (limit: $FAKE_LOC_LIMIT)"
            )
        }

        // Check real-adapter shims LOC cap
        for (shimPath in realAdapterShims) {
            val file = File(projectRoot, shimPath)
            val lines = file.readLines()
            val firstOverrideIdx = lines.indexOfFirst { it.trim().startsWith("override") }
            assertTrue(firstOverrideIdx >= 0, "No override found in $shimPath")
            val bodyLines = lines.drop(firstOverrideIdx)
            val nonEmptyBodyLines = bodyLines.count { it.trim().isNotEmpty() }
            assertTrue(
                nonEmptyBodyLines <= REAL_ADAPTER_LOC_LIMIT,
                "Real-adapter shim $shimPath body is $nonEmptyBodyLines LOC (limit: $REAL_ADAPTER_LOC_LIMIT)"
            )
        }
    }

    /**
     * S17 PLUS clause (spec v5): real-adapter shims may ONLY override
     * `invariant_invocations_stable` (the fake-only invariant per the
     * real-adapter applicability matrix).
     *
     * This test scans each real shim source for `override fun invariant_*`
     * declarations and asserts the set is ⊆ {invariant_invocations_stable}.
     */
    @Test
    fun `real_shim_override_subset_guard`() {
        val invariantOverrideRegex = Regex("override\\s+fun\\s+(invariant_\\w+)\\s*\\(")
        val realShimOverrides = mutableMapOf<String, Set<String>>()

        for (shimPath in realAdapterShims) {
            val file = File(projectRoot, shimPath)
            assertTrue(file.exists(), "Real shim not found: ${file.absolutePath}")
            val content = stripComments(file.readText())
            val overrides = invariantOverrideRegex.findAll(content)
                .map { it.groupValues[1] }
                .toSet()
            realShimOverrides[shimPath] = overrides
        }

        for ((shimPath, overrides) in realShimOverrides) {
            val disallowed = overrides - ALLOWED_REAL_SHIM_OVERRIDE
            assertTrue(
                disallowed.isEmpty(),
                "Real shim $shimPath overrides disallowed invariants: $disallowed. " +
                "Real adapter shims may ONLY override ${ALLOWED_REAL_SHIM_OVERRIDE} " +
                "per spec v5/v6 real-adapter applicability matrix. " +
                "Any other override is a spec violation (vacuous TCK exclusion)."
            )
        }
    }

    /**
     * S17 gate-set equality (spec v6): the set of contract invariants skipped via
     * assumption gates in real shims must be EXACTLY the spec-v6 matrix's
     * per-contract gated-fake-only set.
     *
     * This test walks each contract's invariant_* methods, collects those whose
     * body calls assumeTrue(<gate-hook>, ...), and asserts the gated set equals
     * the spec-v6 matrix. It also confirms each real shim overrides the gate
     * hooks to skip exactly the gated set (no under-skip, no over-skip).
     *
     * Per spec v6 §"v6 loophole closure (S17 PLUS clause — gate-skip enforcement)":
     * "The guard parses each contract source file, collects the invariants
     * containing an assumeTrue(<gate-hook>, ...) call, and asserts that
     * set equals the matrix's gated-fake-only set."
     */
    @Test
    fun `gate_set_equality_guard`() {
        // Step 1: Walk each contract's invariant_* methods, collect gated invariants.
        // A gated invariant is one whose body contains assumeTrue(<gate-hook>, ...)
        val gatedInvariants = mutableMapOf<String, MutableSet<String>>() // contract -> set of gated invariant names

        for (contractPath in contractFiles) {
            val file = File(projectRoot, contractPath)
            assertTrue(file.exists(), "Contract file not found: ${file.absolutePath}")
            val content = stripComments(file.readText())
            val contractName = contractPath.substringAfterLast("/").removeSuffix(".kt")
            gatedInvariants[contractName] = mutableSetOf()

            val lines = content.lines()
            var inInvariantMethod = false
            var currentInvariant: String? = null
            var braceDepth = 0

            for (line in lines) {
                val trimmed = line.trim()

                // Detect invariant method start: "protected open fun invariant_<name>() {"
                val invariantMatch = Regex("protected open fun (invariant_\\w+)\\s*\\(\\s*\\)\\s*\\{?").find(trimmed)
                if (invariantMatch != null && !inInvariantMethod) {
                    inInvariantMethod = true
                    currentInvariant = invariantMatch.groupValues[1]
                    braceDepth = 0
                    // If the opening brace is on the same line, count it
                    if (trimmed.contains("{")) braceDepth++
                    continue
                }

                // If we're inside an invariant method, track braces and look for assumeTrue
                if (inInvariantMethod && currentInvariant != null) {
                    braceDepth += trimmed.count { it == '{' } - trimmed.count { it == '}' }

                    if (trimmed.contains("assumeTrue(")) {
                        gatedInvariants[contractName]!!.add(currentInvariant)
                    }

                    // Check if we've exited the method (braceDepth goes back to 0 or negative)
                    if (braceDepth <= 0 && trimmed.contains("}")) {
                        inInvariantMethod = false
                        currentInvariant = null
                    }
                }
            }
        }

        // Step 2: Assert gate-set equality - the collected gated invariants must match spec v6 matrix
        val allGatedInvariants = gatedInvariants.values.flatten().toSet()
        val extraGated = allGatedInvariants - SPEC_V6_GATED_SET
        val missingGated = SPEC_V6_GATED_SET - allGatedInvariants
        assertTrue(
            extraGated.isEmpty() && missingGated.isEmpty(),
            "Gate-set mismatch with spec v6 matrix. " +
            "Extra gated invariants (in contracts but not in matrix): $extraGated. " +
            "Missing gated invariants (in matrix but not in contracts): $missingGated. " +
            "Spec v6 matrix gated set: $SPEC_V6_GATED_SET. " +
            "Contract gated invariants by contract: $gatedInvariants"
        )

        // Step 3: Walk each real shim, confirm it overrides gate hooks to skip exactly the gated set.
        // Count gate hook overrides per shim and verify the counts match the spec.
        val gateHookOverrideRegex = Regex("override fun (supports\\w+)\\s*\\(\\s*\\)\\s*:\\s*Boolean\\s*=\\s*(true|false)")
        val gateHookOverridesPerShim = mutableMapOf<String, Map<String, Boolean>>()

        for (shimPath in realAdapterShims) {
            val file = File(projectRoot, shimPath)
            assertTrue(file.exists(), "Real shim not found: ${file.absolutePath}")
            val content = stripComments(file.readText())
            val overrides = gateHookOverrideRegex.findAll(content).associate {
                it.groupValues[1] to (it.groupValues[2] == "true")
            }
            gateHookOverridesPerShim[shimPath] = overrides
        }

        // Expected gate hook overrides per real shim based on which contract it extends
        // JGitScmSourceContractTest extends ScmSourceContract -> 1 gate hook (supportsQueueBasedScripting) returning false
        // LocalFSArtifactRepositoryContractTest extends ArtifactRepositoryContract -> 1 gate hook (supportsQueueBasedScripting) returning false
        // GitTagBasedReleaseManagerContractTest extends ReleaseManagerContract -> 2 gate hooks (supportsQueueBasedScripting + supportsRejectionTest) returning false
        val expectedGatedCountPerShim = mapOf(
            "JGitScmSourceContractTest" to 1,      // supportsQueueBasedScripting -> false (skips 3 empty_raises)
            "LocalFSArtifactRepositoryContractTest" to 1, // supportsQueueBasedScripting -> false (skips 3 empty_raises)
            "GitTagBasedReleaseManagerContractTest" to 2, // supportsQueueBasedScripting -> false (skips 2) + supportsRejectionTest -> false (skips 1)
        )

        for ((shimPath, overrides) in gateHookOverridesPerShim) {
            val shimName = shimPath.substringAfterLast("/").removeSuffix(".kt")
            val expectedCount = expectedGatedCountPerShim[shimName]
                ?: throw IllegalStateException("Unexpected real shim: $shimName")

            val actualCount = overrides.values.count { it == false } // false = skip gated invariants
            assertEquals(
                expectedCount, actualCount,
                "Real shim $shimName gate hook override count mismatch. " +
                "Expected $expectedCount gate hook overrides returning false, found $actualCount. " +
                "Gate hooks overridden: $overrides. " +
                "Each false override should skip exactly the matrix's gated invariants per contract."
            )

            // Additional check: ensure no gate hook returns true when it should return false
            // (would mean NOT skipping the gated invariants, causing potential failures)
            val trueOverrides = overrides.filter { it.value }
            assertTrue(
                trueOverrides.isEmpty(),
                "Real shim $shimName has gate hooks returning true: $trueOverrides. " +
                "All gate hooks in real shims must return false to skip fake-only invariants. " +
                "A true return would execute a fake-only invariant against a real adapter, " +
                "potentially causing test failures."
            )
        }
    }
}
