package dev.rubentxu.pipelattice.foundation.secret

import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class SecretValueTest {

    // --- Scenario S3: toString never exposes material ---

    @Test
    fun `toString does not contain material substring`() {
        val value = SecretValue.of("env-var", "synthetic-payload-do-not-use-as-real-secret")
        val str = value.toString()
        assertTrue(
            !str.contains("synthetic-payload-do-not-use-as-real-secret"),
            "toString must NOT contain the material substring"
        )
    }

    @Test
    fun `toString returns redaction marker`() {
        val value = SecretValue.of("env-var", "synthetic-payload-X")
        assertEquals("<redacted:SecretValue>", value.toString())
    }

    // --- Scenario: equality is marker-based ---

    @Test
    fun `equals is marker-based same marker different material`() {
        val a = SecretValue.of("env-var", "p-A")
        val b = SecretValue.of("env-var", "p-B-different")
        assertEquals(a, b, "Same marker = equal regardless of material")
    }

    @Test
    fun `equals false for different markers`() {
        val a = SecretValue.of("env-var-A", "same-material")
        val b = SecretValue.of("env-var-B", "same-material")
        assertNotEquals(a, b, "Different markers = not equal")
    }

    @Test
    fun `hashCode is marker-based`() {
        val a = SecretValue.of("env-var", "p-A")
        val b = SecretValue.of("env-var", "p-B-different")
        assertEquals(a.hashCode(), b.hashCode(), "Same marker = same hashCode")
    }

    // --- Scenario: material() is the only accessor ---

    @Test
    fun `material returns underlying material verbatim`() {
        val value = SecretValue.of("env-var", "synthetic-payload-X")
        assertEquals("synthetic-payload-X", value.material())
    }

    @Test
    fun `material is distinct from toString`() {
        val value = SecretValue.of("env-var", "synthetic-payload-X")
        assertNotSame(value.material(), value.toString())
    }

    @Test
    fun `of factory creates instances correctly`() {
        val value = SecretValue.of("my-marker", "my-secret-material")
        assertEquals("my-secret-material", value.material())
        assertEquals("<redacted:SecretValue>", value.toString())
    }

    @Test
    fun `same marker same instance returns equal`() {
        val a = SecretValue.of("marker", "material-a")
        val b = SecretValue.of("marker", "material-b")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `different marker returns not equal`() {
        val a = SecretValue.of("marker-a", "same-material")
        val b = SecretValue.of("marker-b", "same-material")
        assertNotEquals<Any>(a, b)
    }

    @Test
    fun `equals with non-SecretValue returns false`() {
        val value = SecretValue.of("marker", "material")
        assertNotEquals<Any>(value, "not-a-secret-value")
        assertNotEquals<Any?>(value, null)
    }

    // --- S25: reflection privacy guard ---

    @Test
    fun `S25 marker field is not exposed via reflection`() {
        // Verify marker is private (privacy-positive deviation)
        val markerProperty = SecretValue::class.memberProperties.find { it.name == "marker" }
        assertTrue(
            markerProperty == null || markerProperty.visibility == KVisibility.PRIVATE,
            "marker must be private or not exposed as a public property"
        )
    }

    @Test
    fun `S25 no public property returns raw secret material`() {
        // Verify the only public String-returning accessor is material()
        val stringType = String::class
        val publicStringAccessors = SecretValue::class.memberProperties
            .filter { it.returnType == String::class }
            .filter { it.visibility == KVisibility.PUBLIC }
        assertTrue(
            publicStringAccessors.isEmpty() || (publicStringAccessors.size == 1 && publicStringAccessors.first().name == "material"),
            "No public property should expose raw secret material except material() itself"
        )
    }

    @Test
    fun `S25 toString does not render marker value`() {
        // Additional guard: toString must not contain the marker name
        val value = SecretValue.of("MY_SECRET_ENV_VAR", "super-secret-material-12345")
        val str = value.toString()
        assertTrue(
            !str.contains("MY_SECRET_ENV_VAR"),
            "toString must not contain the marker value '$str'"
        )
        assertTrue(
            !str.contains("super-secret-material-12345"),
            "toString must not contain the material substring"
        )
    }
}
