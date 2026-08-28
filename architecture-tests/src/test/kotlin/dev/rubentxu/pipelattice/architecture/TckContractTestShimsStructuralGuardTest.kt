package dev.rubentxu.pipelattice.architecture

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S17: TCK shim structural discipline guard (v5 spec).
 *
 * Verifies:
 * - Zero @Test annotations across all 6 shim files (3 fakes + 3 reals)
 * - Fake shims ≤ 25 LOC (body only, from first override)
 * - Real-adapter shims ≤ 200 LOC (body only, from first override)
 * - PLUS clause (spec v5): real-adapter shims may ONLY override
 *   `invariant_invocations_stable` (the fake-only invariant per the
 *   real-adapter applicability matrix). Any other `override fun invariant_*`
 *   in a real shim is a spec violation.
 *
 * Per spec v4 acceptance #22 and spec v5 S17 PLUS clause.
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

    // Per spec v5 real-adapter applicability matrix: ONLY this invariant is fake-only
    private val ALLOWED_REAL_SHIM_OVERRIDE = setOf("invariant_invocations_stable")

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
                "per spec v5 real-adapter applicability matrix. " +
                "Any other override is a spec violation (vacuous TCK exclusion)."
            )
        }
    }
}
