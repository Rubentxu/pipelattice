package dev.rubentxu.pipelattice.architecture

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S17: TCK shim structural discipline guard (v4 spec).
 *
 * Verifies:
 * - Zero @Test annotations across all 6 shim files (3 fakes + 3 reals)
 * - Fake shims ≤ 25 LOC (body only, from first override)
 * - Real-adapter shims ≤ 200 LOC (body only, from first override)
 *
 * Per spec v4 acceptance #22.
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
}
