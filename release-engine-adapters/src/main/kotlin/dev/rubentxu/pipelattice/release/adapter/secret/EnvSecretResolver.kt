package dev.rubentxu.pipelattice.release.adapter.secret

import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.foundation.secret.SecretFailure
import dev.rubentxu.pipelattice.foundation.secret.SecretRef
import dev.rubentxu.pipelattice.foundation.secret.SecretResolver
import dev.rubentxu.pipelattice.foundation.secret.SecretValue

/**
 * Real [SecretResolver] adapter that maps `secret://<authority>/<key>` references
 * to environment variables.
 *
 * The mapping rule is: `secret://<authority>/<key>` → `System.getenv("<AUTHORITY>_<KEY>")`
 * where both `<AUTHORITY>` and `<KEY>` are uppercased and joined with an underscore.
 *
 * ## Example
 * ```
 * secret://github/access-token  →  System.getenv("GITHUB_ACCESS_TOKEN")
 * secret://vault/prod-token   →  System.getenv("VAULT_PROD_TOKEN")
 * ```
 *
 * If the environment variable is not set or is empty, the resolver returns
 * [Outcome.Failure] with [SecretFailure.Unknown].
 *
 * ## Non-rendering invariant
 * This adapter NEVER logs, prints, or includes the secret material in any diagnostic.
 * [SecretValue] carries the non-rendering invariant: [SecretValue.toString] returns
 * `"<redacted:SecretValue>"`, not the material.
 *
 * @see SecretResolver The port interface this adapter implements.
 */
public class EnvSecretResolver : SecretResolver {

    /**
     * Resolves a [SecretRef] to its underlying [SecretValue] by looking up the
     * corresponding environment variable.
     *
     * The mapping rule: `secret://<authority>/<key>` maps to the environment variable
     * `"<AUTHORITY>_<KEY>"` (both segments uppercased, joined with `_`).
     *
     * @param ref The secret reference to resolve. Must be a valid `secret://` URI.
     * @return [Outcome.Success] with [SecretValue] if the env var is set and non-empty,
     *         or [Outcome.Failure] with [SecretFailure.Unknown] if the env var is null or empty.
     */
    override suspend fun resolve(ref: SecretRef): Outcome<SecretValue, SecretFailure> {
        val envVarName = buildEnvVarName(ref)
        val envValue = System.getenv(envVarName)

        return if (envValue.isNullOrBlank()) {
            Outcome.Failure(SecretFailure.Unknown(ref.authority, ref.key))
        } else {
            // The marker is the env var name (safe per FARCH-018 — it's just an identifier).
            // The material is the actual secret value (redacted by SecretValue.toString).
            Outcome.Success(SecretValue.of(envVarName, envValue))
        }
    }

    /**
     * Builds the environment variable name from a [SecretRef].
     *
     * Format: `<AUTHORITY>_<KEY>` with both segments uppercased.
     * Example: `secret://github/access-token` → `GITHUB_ACCESS_TOKEN`
     */
    private fun buildEnvVarName(ref: SecretRef): String {
        val authority = ref.authority.uppercase()
        val key = ref.key.uppercase()
        return "${authority}_${key}"
    }
}
