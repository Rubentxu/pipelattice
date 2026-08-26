package dev.rubentxu.pipelattice.resource

import dev.rubentxu.pipelattice.foundation.ResourceRef

/**
 * Reusable central composition: imports, typed parameter declarations and the workflow it
 * selects. Mirrors pipelattice-spec/examples/catalog/java-maven-container-profile.yaml.
 */
public data class PipelineProfileSpec(
    public val imports: List<ResourceRef> = emptyList(),
    public val parameters: Map<String, ParameterDeclaration> = emptyMap(),
    public val workflowRef: ResourceRef? = null,
)
