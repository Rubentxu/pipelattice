package dev.rubentxu.pipelattice.resource

import dev.rubentxu.pipelattice.foundation.ResourceRef

/** Local intent: which central profile to compose and what this project overrides. */
public data class PipelineDefinitionSpec(
    public val profile: ResourceRef? = null,
    public val parameters: Map<String, ParameterValue> = emptyMap(),
)
