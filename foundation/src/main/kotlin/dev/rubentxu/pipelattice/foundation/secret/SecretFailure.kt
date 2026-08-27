package dev.rubentxu.pipelattice.foundation.secret

import dev.rubentxu.pipelattice.foundation.outcome.Outcome

/**
 * Failure variants for [SecretResolver] operations.
 */
public sealed interface SecretFailure {

    /**
     * The secret authority/key pair is not registered in the resolver.
     */
    public data class Unknown(
        public val authority: String,
        public val key: String,
    ) : SecretFailure {
        override fun toString(): String = "SecretFailure.Unknown(authority=$authority, key=$key)"
    }

    /**
     * The resolver has the secret but refused to return its material,
     * e.g. insufficient permissions.
     */
    public data class AccessDenied(
        public val authority: String,
        public val key: String,
    ) : SecretFailure {
        override fun toString(): String = "SecretFailure.AccessDenied(authority=$authority, key=$key)"
    }

    /**
     * The [SecretRef] is well-formed per [SecretRef.parse] but the underlying
     * secret entry is structurally invalid (decode error, schema mismatch, etc.).
     */
    public data class Malformed(
        public val refRaw: String,
        public val reason: String,
    ) : SecretFailure {
        override fun toString(): String = "SecretFailure.Malformed(refRaw=$refRaw, reason=$reason)"
    }
}
