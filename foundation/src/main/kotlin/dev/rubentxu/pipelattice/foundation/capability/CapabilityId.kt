package dev.rubentxu.pipelattice.foundation.capability

/**
 * Versioned capability identifier.
 *
 * Grammar: `<op>/<version>` where:
 * - `<op>` is a dotted operation name using `[a-z0-9.]` characters (e.g. `scm.checkout`).
 * - `<version>` matches `v\d+` (e.g. `v1`, `v2`).
 *
 * Examples: `scm.checkout/v1`, `artifact.publish/v1`, `release.calculate/v1`.
 *
 * @param value The canonical string form, e.g. `scm.checkout/v1`.
 */
@JvmInline
public value class CapabilityId(public val value: String) {

    init {
        require(value.isNotBlank()) { "CapabilityId.value must not be blank" }
    }

    /**
     * The operation portion, e.g. `scm.checkout` from `scm.checkout/v1`.
     */
    public val operation: String
        get() {
            val slash = value.lastIndexOf('/')
            require(slash > 0) { "CapabilityId must contain '/' separator: $value" }
            return value.substring(0, slash)
        }

    /**
     * The version portion, e.g. `v1` from `scm.checkout/v1`.
     */
    public val version: String
        get() {
            val slash = value.lastIndexOf('/')
            require(slash >= 0 && slash < value.length - 1) {
                "CapabilityId version must not be blank: $value"
            }
            return value.substring(slash + 1)
        }

    override fun toString(): String = value

    public companion object {
        private val OP_PATTERN = Regex("^[a-z0-9.]+$")
        private val VERSION_PATTERN = Regex("^v\\d+$")

        /**
         * Parses a canonical capability id such as `scm.checkout/v1`.
         *
         * @throws IllegalArgumentException if the input does not match the grammar.
         */
        public fun parse(raw: String): CapabilityId {
            require(raw.isNotBlank()) { "CapabilityId must not be blank" }

            val slash = raw.lastIndexOf('/')
            require(slash > 0) {
                "CapabilityId must contain '/' separator between op and version: $raw"
            }
            require(slash < raw.length - 1) {
                "CapabilityId version must not be blank: $raw"
            }

            val op = raw.substring(0, slash)
            val version = raw.substring(slash + 1)

            require(op.isNotBlank()) {
                "CapabilityId op must not be blank: $raw"
            }
            require(op matches OP_PATTERN) {
                "CapabilityId op must match [a-z0-9.] only: $raw"
            }
            require(!op.contains("..")) {
                "CapabilityId op must not contain consecutive dots: $raw"
            }

            require(version matches VERSION_PATTERN) {
                "CapabilityId version must match v\\d+ (e.g. v1, v2): $raw"
            }

            // No multiple slashes
            require(raw.indexOf('/') == raw.lastIndexOf('/')) {
                "CapabilityId must not contain multiple '/' separators: $raw"
            }

            return CapabilityId(raw)
        }
    }
}
