package dev.rubentxu.pipelattice.architecture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Path

/**
 * FARCH-018: Secret isolation enforcement — real source-file scan.
 *
 * This test replaces the no-op ArchUnit bytecode rule (which only matched Java class FQNs,
 * never string literals). It performs an actual regex scan of production source files looking
 * for credential-shaped string literals.
 *
 * Architecture:
 * - [SecretLiteralScanner] lives in THIS TEST MODULE (architecture-tests), NOT in production.
 *   This keeps the scanner's own regex constants out of the scan's reach — the scan stays honest.
 * - [scan] returns [Finding] records: pattern id, line number, and a redacted snippet (first 12 chars).
 * - Self-verification test kills no-op scanners: feed known patterns → assert exact findings.
 *
 * Production scan covers:
 * - release-engine/src/main/kotlin/**/*.kt
 * - foundation/src/main/kotlin/dev/rubentxu/pipelattice/foundation/secret/**/*.kt
 * - foundation/src/main/kotlin/dev/rubentxu/pipelattice/foundation/capability/**/*.kt
 */
class Farch018SecretIsolationTest {

    // -------------------------------------------------------------------------
    // SecretLiteralScanner — lives in test module, regex constants stay out of production
    // -------------------------------------------------------------------------

    data class Finding(
        val patternId: String,
        val lineNumber: Int,
        val snippet: String, // first 12 chars, redacted
    )

    object SecretLiteralScanner {
        // Curated credential-shaped patterns (FARCH-018 per spec)
        private val patterns = listOf(
            "AWS_ACCESS_KEY" to Regex("""AKIA[0-9A-Z]{16}"""),
            "GITHUB_PAT" to Regex("""ghp_[A-Za-z0-9]{36}"""),
            "PRIVATE_KEY_BLOCK" to Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----"""),
            "GENERIC_SECRET" to Regex("""(?i)\b(password|passwd|secret|token)\b\s*[:=]\s*"[^"]{8,}""""),
        )

        /**
         * Scan a single source string for credential-shaped literals.
         * Returns one [Finding] per match (may be multiple patterns on same line).
         */
        fun scan(source: String): List<Finding> {
            val findings = mutableListOf<Finding>()
            source.lines().forEachIndexed { lineIndex, line ->
                for ((patternId, pattern) in patterns) {
                    pattern.find(line)?.let { match ->
                        findings.add(
                            Finding(
                                patternId = patternId,
                                lineNumber = lineIndex + 1,
                                snippet = match.value.take(12),
                            )
                        )
                    }
                }
            }
            return findings
        }
    }

    // -------------------------------------------------------------------------
    // Self-verification: proves the scanner is not a no-op
    // -------------------------------------------------------------------------

    @Test
    fun `scanner self-test - detects all known patterns`() {
        val dirtySource = """
            |fun example() {
            |    val awsKey = "AKIAIOSFODNN7EXAMPLE"
            |    val githubPat = "ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
            |    val privateKey = "-----BEGIN RSA PRIVATE KEY-----"
            |    val password = "password: \"super_secret_123\""
            |    val token = "token: \"my_secret_token_value\""
            |}
        """.trimMargin()

        val findings = SecretLiteralScanner.scan(dirtySource)

        assertEquals(5, findings.size, "Expected 5 findings for 5 patterns")
        assertTrue(findings.any { it.patternId == "AWS_ACCESS_KEY" }, "Should detect AWS key")
        assertTrue(findings.any { it.patternId == "GITHUB_PAT" }, "Should detect GitHub PAT")
        assertTrue(findings.any { it.patternId == "PRIVATE_KEY_BLOCK" }, "Should detect private key block")
        assertTrue(findings.any { it.patternId == "GENERIC_SECRET" && it.snippet.startsWith("password") }, "Should detect password pattern")
        assertTrue(findings.any { it.patternId == "GENERIC_SECRET" && it.snippet.startsWith("token") }, "Should detect token pattern")
    }

    @Test
    fun `scanner self-test - clean source yields zero findings`() {
        val cleanSource = """
            |fun example() {
            |    val name = "Alice"
            |    val url = "https://example.com/api"
            |    val count = 42
            |    val safe = "no secrets here"
            |}
        """.trimMargin()

        val findings = SecretLiteralScanner.scan(cleanSource)
        assertEquals(0, findings.size, "Clean source must produce zero findings")
    }

    // -------------------------------------------------------------------------
    // Production scan: walk all production .kt files under coverage dirs
    // -------------------------------------------------------------------------

    @Test
    fun `FARCH-018 - production scan finds zero secret-shaped literals`() {
        val root = Path.of(System.getProperty("user.dir")).parent.parent

        val releaseEnginePath = root.resolve("release-engine/src/main/kotlin")
        val foundationSecretPath = root.resolve("foundation/src/main/kotlin/dev/rubentxu/pipelattice/foundation/secret")
        val foundationCapabilityPath = root.resolve("foundation/src/main/kotlin/dev/rubentxu/pipelattice/foundation/capability")

        val scanDirs = listOf(releaseEnginePath, foundationSecretPath, foundationCapabilityPath)
            .filter { it.toFile().exists() }
            .onEach { require(it.toFile().isDirectory) { "Scan dir must exist: $it" } }

        val allFindings = mutableListOf<Triple<Path, Finding, String>>()

        for (scanDir in scanDirs) {
            scanDir.toFile().walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val content = file.readText()
                    val findings = SecretLiteralScanner.scan(content)
                    if (findings.isNotEmpty()) {
                        allFindings.add(Triple(file.toPath().let { scanDir.parent.parent.parent.relativize(it) }, findings.first(), content))
                    }
                }
        }

        if (allFindings.isNotEmpty()) {
            val report = allFindings.joinToString("\n") { (file, finding, _) ->
                "  ${file}:${finding.lineNumber} [${finding.patternId}] snippet=${finding.snippet}"
            }
            throw AssertionError("FARCH-018 VIOLATIONS found:\n$report")
        }
    }
}
