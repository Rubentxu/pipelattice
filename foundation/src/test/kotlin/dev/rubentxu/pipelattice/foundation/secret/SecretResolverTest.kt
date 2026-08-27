package dev.rubentxu.pipelattice.foundation.secret

import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SecretResolverTest {

    /**
     * A fake resolver for testing the [SecretResolver] port contract.
     */
    private class FakeSecretResolver(
        private val scripts: MutableMap<String, Outcome<SecretValue, SecretFailure>> = mutableMapOf(),
    ) : SecretResolver {

        fun script(ref: SecretRef, outcome: Outcome<SecretValue, SecretFailure>) {
            scripts[ref.raw] = outcome
        }

        override suspend fun resolve(ref: SecretRef): Outcome<SecretValue, SecretFailure> {
            return scripts[ref.raw]
                ?: Outcome.Failure(SecretFailure.Unknown(ref.authority, ref.key))
        }
    }

    // --- Scenario S2: resolver returns typed failure for unknown ref ---

    @Test
    fun `resolve returns Failure Unknown for unknown ref`() = runBlocking {
        val resolver = FakeSecretResolver()
        val ref = SecretRef.parse("secret://unknown/missing")

        val outcome = resolver.resolve(ref)

        assertIs<Outcome.Failure<SecretFailure>>(outcome)
        assertIs<SecretFailure.Unknown>(outcome.reason)
        assertEquals("unknown", outcome.reason.authority)
        assertEquals("missing", outcome.reason.key)
    }

    @Test
    fun `Unknown toString does not contain synthetic material`() {
        val unknown = SecretFailure.Unknown("auth", "key")
        val str = unknown.toString()
        assertTrue(!str.contains("synthetic"), "Unknown.toString must not leak material")
    }

    // --- Scenario S3: resolver returns Success for known ref ---

    @Test
    fun `resolve returns Success for known ref`() = runBlocking {
        val resolver = FakeSecretResolver()
        val ref = SecretRef.parse("secret://vault/registry-token")
        resolver.script(ref, Outcome.Success(SecretValue.of("env-var", "synthetic-payload")))

        val outcome = resolver.resolve(ref)

        assertIs<Outcome.Success<SecretValue>>(outcome)
        assertEquals("synthetic-payload", outcome.value.material())
    }

    @Test
    fun `Success SecretValue preserves non-rendering invariant`() = runBlocking {
        val resolver = FakeSecretResolver()
        val ref = SecretRef.parse("secret://vault/registry-token")
        resolver.script(ref, Outcome.Success(SecretValue.of("env-var", "synthetic-payload")))

        val outcome = resolver.resolve(ref)

        assertIs<Outcome.Success<SecretValue>>(outcome)
        assertTrue(
            !outcome.value.toString().contains("synthetic-payload"),
            "SecretValue.toString must not contain material across Outcome path"
        )
    }

    @Test
    fun `resolve returns Failure AccessDenied when access denied`() = runBlocking {
        val resolver = FakeSecretResolver()
        val ref = SecretRef.parse("secret://vault/denied-key")
        resolver.script(ref, Outcome.Failure(SecretFailure.AccessDenied("vault", "denied-key")))

        val outcome = resolver.resolve(ref)

        assertIs<Outcome.Failure<SecretFailure>>(outcome)
        assertIs<SecretFailure.AccessDenied>(outcome.reason)
    }

    @Test
    fun `resolve returns Failure Malformed for structurally invalid secret`() = runBlocking {
        val resolver = FakeSecretResolver()
        val ref = SecretRef.parse("secret://vault/bad-entry")
        resolver.script(
            ref,
            Outcome.Failure(SecretFailure.Malformed(ref.raw, "decode error: invalid base64"))
        )

        val outcome = resolver.resolve(ref)

        assertIs<Outcome.Failure<SecretFailure>>(outcome)
        assertIs<SecretFailure.Malformed>(outcome.reason)
        assertEquals("decode error: invalid base64", outcome.reason.reason)
    }

    @Test
    fun `AccessDenied toString does not contain synthetic material`() {
        val denied = SecretFailure.AccessDenied("auth", "key")
        val str = denied.toString()
        assertTrue(!str.contains("synthetic"), "AccessDenied.toString must not leak material")
    }

    @Test
    fun `Malformed toString does not contain synthetic material`() {
        val malformed = SecretFailure.Malformed("secret://auth/key", "decode error")
        val str = malformed.toString()
        assertTrue(!str.contains("synthetic"), "Malformed.toString must not leak material")
    }
}
