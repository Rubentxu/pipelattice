package dev.rubentxu.pipelattice.foundation.secret

/**
 * Typed reference to a secret identified by URI.
 *
 * Canonical form:
 *
 * ```
 * secret://<authority>/<key>
 * ```
 *
 * Examples: `secret://vault.team/registry-token`, `secret://aws/prod-access-key`.
 *
 * The [raw] field carries only the URI; it never materialises the underlying secret.
 * The [authority] and [key] fields are safe to render in diagnostics and logs.
 *
 * @param raw The canonical URI string, e.g. `secret://vault/registry-token`.
 */
@JvmInline
public value class SecretRef(public val raw: String) {

    init {
        require(raw.isNotBlank()) { "SecretRef.raw must not be blank" }
    }

    /**
     * The authority portion of the URI, e.g. `vault.team` from
     * `secret://vault.team/registry-token`.
     */
    public val authority: String
        get() {
            val rest = raw.removePrefix(SCHEME_PREFIX)
            val slashIndex = rest.indexOf('/')
            require(slashIndex > 0) { "SecretRef authority must not be blank" }
            return rest.substring(0, slashIndex)
        }

    /**
     * The key portion of the URI, e.g. `registry-token` from
     * `secret://vault.team/registry-token`.
     */
    public val key: String
        get() {
            val rest = raw.removePrefix(SCHEME_PREFIX)
            val slashIndex = rest.indexOf('/')
            require(slashIndex >= 0 && slashIndex < rest.length - 1) {
                "SecretRef key must not be blank"
            }
            return rest.substring(slashIndex + 1)
        }

    override fun toString(): String = raw

    public companion object {
        public const val SCHEME: String = "secret"

        private const val SCHEME_PREFIX = "$SCHEME://"

        /**
         * Parses a canonical secret reference such as `secret://vault/registry-token`.
         *
         * @throws IllegalArgumentException if the input is not in canonical form.
         */
        public fun parse(raw: String): SecretRef {
            val prefix = SCHEME_PREFIX
            require(raw.startsWith(prefix)) {
                "Unsupported secret ref '$raw'; expected '$prefix<authority>/<key>'"
            }
            val rest = raw.removePrefix(prefix)
            require(rest.isNotBlank()) {
                "Unsupported secret ref '$raw'; authority segment is empty"
            }
            val slashIndex = rest.indexOf('/')
            require(slashIndex > 0) {
                "Unsupported secret ref '$raw'; authority must not be blank"
            }
            require(slashIndex < rest.length - 1) {
                "Unsupported secret ref '$raw'; key must not be blank"
            }
            val authority = rest.substring(0, slashIndex)
            val key = rest.substring(slashIndex + 1)

            require(authority.isNotBlank()) {
                "Unsupported secret ref '$raw'; authority must not be blank"
            }
            require(key.isNotBlank()) {
                "Unsupported secret ref '$raw'; key must not be blank"
            }
            require(authority.none { it.isWhitespace() }) {
                "Unsupported secret ref '$raw'; authority must not contain whitespace"
            }
            require(key.none { it.isWhitespace() }) {
                "Unsupported secret ref '$raw'; key must not contain whitespace"
            }
            require('@' !in authority && '@' !in key) {
                "Unsupported secret ref '$raw'; authority and key must not contain '@'"
            }
            require('?' !in authority && '?' !in key) {
                "Unsupported secret ref '$raw'; authority and key must not contain '?'"
            }
            require('#' !in authority && '#' !in key) {
                "Unsupported secret ref '$raw'; authority and key must not contain '#'"
            }

            return SecretRef(raw)
        }
    }
}
