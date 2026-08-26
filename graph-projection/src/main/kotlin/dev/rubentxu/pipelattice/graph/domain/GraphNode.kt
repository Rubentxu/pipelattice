package dev.rubentxu.pipelattice.graph.domain

import dev.rubentxu.pipelattice.foundation.ResourceRef
import java.nio.file.Path

/**
 * A node in the reactive configuration graph.
 *
 * Mirrors the 13 node kinds defined in spec 04 §3. A-min covers 5 core kinds
 * (the ones needed by V1 invalidation: profile → project → fragment → source → plan);
 * the remaining 8 (GitRevision, ConfigurationFragment, Workflow, Capability,
 * Provider, PolicySet, Environment, ReleasePolicy) are deferred to A-lite.
 *
 * Each variant is a data class so structural equality + hashCode are stable
 * for use in [Set] and as [Map] keys.
 */
public sealed interface GraphNode {

    public data class Project(public val id: ResourceRef) : GraphNode

    public data class Component(public val id: ResourceRef, public val owner: ResourceRef) : GraphNode

    public data class PipelineProfile(public val id: ResourceRef) : GraphNode

    public data class ConfigSource(public val path: Path, public val contentHash: String) : GraphNode

    public data class ResolvedPipelinePlan(
        public val projectId: ResourceRef,
        public val planDigest: String,
    ) : GraphNode
}
