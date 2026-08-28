package dev.rubentxu.pipelattice.architecture

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * S17: TCK shim LOC discipline guard.
 *
 * Verifies that each `*ContractTest` fake shim in `:release-engine`
 * has a body of ≤ 15 LOC (counting from first override to end, excluding boilerplate).
 * Real adapter shims in `:release-engine-adapters` are exempt as they require
 * infrastructure setup (temp dirs, bare repos, etc.).
 */
class TckContractTestShimsLocGuardTest {

    private val BODY_LOC_LIMIT = 15

    // Paths relative to project root (architecture-tests is at the root level)
    private val fakeShims = listOf(
        "release-engine/src/test/kotlin/dev/rubentxu/pipelattice/release/scm/FakeScmSourceContractTest.kt",
        "release-engine/src/test/kotlin/dev/rubentxu/pipelattice/release/artifact/FakeArtifactRepositoryContractTest.kt",
        "release-engine/src/test/kotlin/dev/rubentxu/pipelattice/release/release/FakeReleaseManagerContractTest.kt",
    )

    @Test
    fun `fake TCK shim bodies are within 15 LOC`() {
        // When running tests, Gradle sets the working directory to the module directory.
        // Since architecture-tests is at the project root level, we navigate up one level.
        val projectRoot = File("..").absoluteFile
        for (shimPath in fakeShims) {
            val file = File(projectRoot, shimPath)
            assertTrue(file.exists(), "Shim not found: ${file.absolutePath}")
            val lines = file.readLines()

            // Count LOC from first 'override' keyword to end of file (body lines only)
            val firstOverrideIdx = lines.indexOfFirst { it.trim().startsWith("override") }
            assertTrue(firstOverrideIdx >= 0, "No override found in $shimPath")

            val bodyLines = lines.drop(firstOverrideIdx)
            val nonEmptyBodyLines = bodyLines.count { it.trim().isNotEmpty() }

            assertTrue(
                nonEmptyBodyLines <= BODY_LOC_LIMIT,
                "Shim $shimPath body is $nonEmptyBodyLines LOC (limit: $BODY_LOC_LIMIT)"
            )
        }
    }
}
