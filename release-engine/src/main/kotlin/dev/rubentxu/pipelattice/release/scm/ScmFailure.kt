package dev.rubentxu.pipelattice.release.scm

import dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor
import dev.rubentxu.pipelattice.foundation.capability.CapabilityId
import dev.rubentxu.pipelattice.foundation.capability.FailureModel
import dev.rubentxu.pipelattice.foundation.capability.IdempotencyPolicy
import dev.rubentxu.pipelattice.foundation.capability.ProviderRequirements
import dev.rubentxu.pipelattice.foundation.capability.ProviderVersion
import dev.rubentxu.pipelattice.foundation.capability.SchemaId
import dev.rubentxu.pipelattice.foundation.capability.SideEffect
import dev.rubentxu.pipelattice.foundation.secret.SecretRef

/**
 * Failure variants for [ScmSource] operations.
 */
public sealed interface ScmFailure {

    /**
     * An unexpected error occurred.
     */
    public data class Unknown(
        public val operation: String,
        public val reason: String,
    ) : ScmFailure {
        override fun toString(): String = "ScmFailure.Unknown(operation=$operation, reason=$reason)"
    }

    /**
     * A conflict was detected (e.g. non-fast-forward push, conflicting tag).
     */
    public data class Conflict(
        public val operation: String,
        public val reason: String,
    ) : ScmFailure {
        override fun toString(): String = "ScmFailure.Conflict(operation=$operation, reason=$reason)"
    }
}

/**
 * Factory for creating [CapabilityDescriptor] constants for SCM operations.
 */
public object ScmSourceCapabilities {

    private fun descriptor(
        id: CapabilityId,
        inputSchema: String,
        outputSchema: String,
        sideEffects: Set<SideEffect>,
        idempotency: IdempotencyPolicy,
    ): CapabilityDescriptor = CapabilityDescriptor(
        id = id,
        inputSchemaId = SchemaId(inputSchema),
        outputSchemaId = SchemaId(outputSchema),
        failureModel = FailureModel.Typed("SCM-${id.value.replace(".", "-").uppercase()}"),
        sideEffects = sideEffects,
        idempotencyPolicy = idempotency,
        providerRequirements = ProviderRequirements(
            minProviderVersion = ProviderVersion("V1"),
            authRequirements = emptySet(),
        ),
    )

    /** Descriptor for `scm.checkout/v1` — READ_ONLY. */
    public val SCM_CHECKOUT_V1: CapabilityDescriptor = descriptor(
        id = ScmSource.SCM_CHECKOUT_V1,
        inputSchema = "scm-checkout-request",
        outputSchema = "scm-checkout-result",
        sideEffects = setOf(SideEffect.READ_ONLY),
        idempotency = IdempotencyPolicy.Strict(retrySafe = true),
    )

    /** Descriptor for `scm.tag/v1` — MUTATING, IDEMPOTENT. */
    public val SCM_TAG_V1: CapabilityDescriptor = descriptor(
        id = ScmSource.SCM_TAG_V1,
        inputSchema = "scm-tag-request",
        outputSchema = "scm-tag-result",
        sideEffects = setOf(SideEffect.MUTATING, SideEffect.IDEMPOTENT),
        idempotency = IdempotencyPolicy.Strict(retrySafe = true),
    )

    /** Descriptor for `scm.push/v1` — MUTATING. */
    public val SCM_PUSH_V1: CapabilityDescriptor = descriptor(
        id = ScmSource.SCM_PUSH_V1,
        inputSchema = "scm-push-request",
        outputSchema = "scm-push-result",
        sideEffects = setOf(SideEffect.MUTATING),
        idempotency = IdempotencyPolicy.None,
    )
}
