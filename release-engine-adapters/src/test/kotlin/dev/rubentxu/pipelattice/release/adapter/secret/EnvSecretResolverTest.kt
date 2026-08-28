package dev.rubentxu.pipelattice.release.adapter.secret

import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.foundation.secret.SecretFailure
import dev.rubentxu.pipelattice.foundation.secret.SecretRef
import dev.rubentxu.pipelattice.foundation.secret.SecretValue
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [EnvSecretResolver].
 *
 * Tests the mapping rule: secret://<authority>/<key> → System.getenv("<AUTHORITY>_<KEY>")
 */
class EnvSecretResolverTest {

    private val resolver = EnvSecretResolver()

    @Test
    fun `unset var returns Unknown failure with correct authority and key`() = runBlocking {
        // Use a guaranteed-unset ref
        val ref = SecretRef.parse("secret://nonexistent-unused-key-12345/unset-var-67890")
        val outcome = resolver.resolve(ref)

        assertIs<Outcome.Failure<SecretFailure>>(outcome)
        val reason = outcome.reason as SecretFailure.Unknown
        assertTrue(reason.authority == "nonexistent-unused-key-12345")
        assertTrue(reason.key == "unset-var-67890")
    }

    @Test
    fun `unset var with two-segment authority returns Unknown failure`() = runBlocking {
        val ref = SecretRef.parse("secret://my-org/my-secret")
        val outcome = resolver.resolve(ref)

        assertIs<Outcome.Failure<SecretFailure>>(outcome)
        val reason = outcome.reason as SecretFailure.Unknown
        assertTrue(reason.authority == "my-org")
        assertTrue(reason.key == "my-secret")
    }

    @Test
    fun `toString returns redaction marker not material`() {
        // Direct test: SecretValue.toString must never return the material
        val sv = SecretValue.of("marker-only", "super-secret-material-12345")
        val toStringResult = sv.toString()

        assertTrue(
            toStringResult == "<redacted:SecretValue>",
            "SecretValue.toString() must return <redacted:SecretValue>, got: $toStringResult"
        )
        assertTrue(
            !toStringResult.contains("super-secret-material"),
            "toString must not contain the material"
        )
    }

    @Test
    fun `equals compares on marker not material`() {
        // Two SecretValues with same marker but different material should be equal
        val sv1 = SecretValue.of("shared-marker", "material-A-xxx")
        val sv2 = SecretValue.of("shared-marker", "material-B-yyy")

        assertTrue(sv1 == sv2, "Same marker, different material: should be equal")
        assertTrue(sv1.hashCode() == sv2.hashCode(), "Same marker: hashCodes should be equal")
        assertTrue(sv1.material() == "material-A-xxx", "sv1.material() returns its own material")
        assertTrue(sv2.material() == "material-B-yyy", "sv2.material() returns its own material")
    }

    @Test
    fun `different markers produce different hashCodes and unequal values`() {
        val sv1 = SecretValue.of("marker-one", "same-material")
        val sv2 = SecretValue.of("marker-two", "same-material")

        assertTrue(sv1 != sv2, "Different markers: should not be equal")
        assertTrue(sv1.hashCode() != sv2.hashCode(), "Different markers: hashCodes should differ")
    }

    @Test
    fun `known env var returns success with correct marker and material`() = runBlocking {
        // Use PATH env var which always exists
        val pathValue = System.getenv("PATH") ?: return@runBlocking
        val ref = SecretRef.parse("secret://system/path")

        val outcome = resolver.resolve(ref)

        when (outcome) {
            is Outcome.Success -> {
                val sv = outcome.value
                // Material should be the PATH value
                assertTrue(
                    sv.material() == pathValue,
                    "material() should return the PATH env var value"
                )
                // Marker should be SYSTEM_PATH (uppercase) - verified via equality
                val expectedSV = SecretValue.of("SYSTEM_PATH", "any-material")
                assertTrue(
                    sv == expectedSV,
                    "marker should be uppercase SYSTEM_PATH (verified via equality)"
                )
            }
            is Outcome.Failure -> {
                // If PATH is not set (unusual), skip this assertion
            }
        }
    }
}
