package dev.rubentxu.pipelattice.foundation.capability

import dev.rubentxu.pipelattice.foundation.secret.SecretRef

/**
 * Capability metadata envelope per spec 03 §2.
 *
 * Carries versioned ID, input/output schema identifiers, failure model,
 * side-effect classification, idempotency policy, and provider requirements.
 *
 * @param id The versioned capability identifier.
 * @param inputSchemaId Schema identifier for the request type.
 * @param outputSchemaId Schema identifier for the result type.
 * @param failureModel The failure classification model.
 * @param sideEffects Classification of side-effects this capability may produce.
 * @param idempotencyPolicy Whether the operation is safe to retry.
 * @param providerRequirements Minimum provider version and authentication requirements.
 */
public data class CapabilityDescriptor(
    public val id: CapabilityId,
    public val inputSchemaId: SchemaId,
    public val outputSchemaId: SchemaId,
    public val failureModel: FailureModel,
    public val sideEffects: SideEffectSet,
    public val idempotencyPolicy: IdempotencyPolicy,
    public val providerRequirements: ProviderRequirements,
)

/**
 * Value class wrapping a schema identifier string.
 * Validated to match `[a-z][a-z0-9-]+`.
 *
 * @param value The schema identifier string.
 */
@JvmInline
public value class SchemaId(public val value: String) {
    init {
        require(value.isNotBlank()) { "SchemaId.value must not be blank" }
        require(value[0].isLowerCase()) {
            "SchemaId must start with a lowercase letter: $value"
        }
        require(SCHEMA_PATTERN.matches(value)) {
            "SchemaId must match [a-z][a-z0-9-]+: $value"
        }
    }

    public override fun toString(): String = value

    public companion object {
        private val SCHEMA_PATTERN = Regex("^[a-z][a-z0-9-]+$")
    }
}

/**
 * Failure model classification for a capability.
 */
public sealed interface FailureModel {

    /**
     * A typed failure code, e.g. `SCM-CHECKOUT-001`.
     */
    public data class Typed(public val code: String) : FailureModel {
        init {
            require(code.isNotBlank()) { "FailureModel.Typed.code must not be blank" }
        }
    }

    /**
     * An untyped failure category, e.g. `network-error`.
     */
    public data class Untyped(public val category: String) : FailureModel {
        init {
            require(category.isNotBlank()) { "FailureModel.Untyped.category must not be blank" }
        }
    }
}

/**
 * Side-effect classification for a capability.
 */
public enum class SideEffect {
    /**
     * The operation reads state but does not modify it.
     */
    READ_ONLY,

    /**
     * The operation modifies state.
     */
    MUTATING,

    /**
     * The operation is safe to call multiple times with the same inputs.
     */
    IDEMPOTENT,
}

/**
 * Set of [SideEffect] values for a capability.
 */
public typealias SideEffectSet = Set<SideEffect>

/**
 * Idempotency policy for a capability.
 */
public sealed interface IdempotencyPolicy {

    /**
     * Strict idempotency with a [retrySafe] flag.
     * When [retrySafe] is true, the operation can safely be retried on failure.
     */
    public data class Strict(public val retrySafe: Boolean) : IdempotencyPolicy

    /**
     * No idempotency guarantee — calling multiple times may produce different results.
     */
    public data object None : IdempotencyPolicy
}

/**
 * Provider requirements for a capability.
 *
 * @param minProviderVersion Minimum required provider version (e.g. `v1`, `v2.1`).
 * @param authRequirements Set of [SecretRef] required for authentication.
 */
public data class ProviderRequirements(
    public val minProviderVersion: ProviderVersion,
    public val authRequirements: Set<SecretRef>,
)

/**
 * Value class wrapping a provider version string.
 * Validated to match `[A-Z][A-Z0-9.-]*`.
 *
 * @param value The provider version string.
 */
@JvmInline
public value class ProviderVersion(public val value: String) {
    init {
        require(value.isNotBlank()) { "ProviderVersion.value must not be blank" }
        require(PROVIDER_VERSION_PATTERN.matches(value)) {
            "ProviderVersion must match [A-Z][A-Z0-9.-]*: $value"
        }
    }

    public override fun toString(): String = value

    public companion object {
        private val PROVIDER_VERSION_PATTERN = Regex("^[A-Z][A-Z0-9.-]*$")
    }
}
