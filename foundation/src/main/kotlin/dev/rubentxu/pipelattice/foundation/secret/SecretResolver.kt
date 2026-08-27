package dev.rubentxu.pipelattice.foundation.secret

import dev.rubentxu.pipelattice.foundation.outcome.Outcome

/**
 * Port for resolving [SecretRef] references to their underlying [SecretValue].
 *
 * Implementations MUST NOT log, print, or include [SecretValue.material] in any
 * diagnostic, error message, or stack trace. Violations fail the FARCH-018
 * secret isolation rule.
 *
 * @see SecretRef The reference type this resolver processes.
 * @see SecretValue The opaque carrier with the non-rendering invariant.
 * @see SecretFailure Typed failures when resolution is not possible.
 */
public interface SecretResolver {

    /**
     * Resolves a [SecretRef] to its underlying [SecretValue].
     *
     * @param ref The secret reference to resolve.
     * @return [Outcome.Success] containing the [SecretValue] if resolution succeeded,
     *         or [Outcome.Failure] with a typed [SecretFailure] if it failed.
     */
    public suspend fun resolve(ref: SecretRef): Outcome<SecretValue, SecretFailure>
}
