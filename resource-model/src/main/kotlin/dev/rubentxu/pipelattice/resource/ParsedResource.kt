package dev.rubentxu.pipelattice.resource

/**
 * A fully parsed source resource. The sealed hierarchy (instead of a generic envelope) keeps
 * downstream `when` statements exhaustive as kinds are added.
 */
public sealed interface ParsedResource {
    public val apiVersion: ApiVersion
    public val kind: ResourceKind
    public val metadata: Metadata
}

public data class PipelineDefinitionResource(
    override val apiVersion: ApiVersion,
    override val metadata: Metadata,
    public val spec: PipelineDefinitionSpec,
) : ParsedResource {
    override val kind: ResourceKind = ResourceKind.PIPELINE_DEFINITION
}

public data class PipelineProfileResource(
    override val apiVersion: ApiVersion,
    override val metadata: Metadata,
    public val spec: PipelineProfileSpec,
) : ParsedResource {
    override val kind: ResourceKind = ResourceKind.PIPELINE_PROFILE
}
