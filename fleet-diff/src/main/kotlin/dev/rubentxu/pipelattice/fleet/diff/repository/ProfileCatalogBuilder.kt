package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.compose.ports.CatalogSource
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.resource.PipelineProfileResource
import dev.rubentxu.pipelattice.resource.ResourceParser
import dev.rubentxu.pipelattice.resource.SourceDocument

/**
 * Builds a profile catalog map from a list of source documents.
 *
 * The catalog maps [ResourceRef] to [SourceDocument] using the same conventions as
 * [ImportResolver]: the ref is derived from the source file path with extensions stripped.
 *
 * Example:
 * - `profiles/java.yaml` → `catalog://profiles/java`
 * - `profiles/base.yml`  → `catalog://profiles/base`
 *
 * **Critical**: This function uses the ORIGINAL [SourceDocument] content (with actual YAML),
 * not empty strings. Empty content causes [ResourceParser.parse] to fail in [ImportResolver],
 * breaking import chains and causing false `E-COMPOSE-AFFECTED-001` errors.
 *
 * @param sources The source documents to build the catalog from.
 * @param resourceParser The resource parser for identifying profile resources.
 * @return A map from [ResourceRef] to [SourceDocument] suitable for use with [CatalogSource].
 */
internal fun buildProfileCatalog(
    sources: List<SourceDocument>,
    resourceParser: ResourceParser,
): Map<ResourceRef, SourceDocument> {
    if (sources.isEmpty()) return emptyMap()

    // Build sourceByMetadataName: profile metadata name -> original SourceDocument
    val sourceByMetadataName = mutableMapOf<String, SourceDocument>()
    for (source in sources) {
        val result = resourceParser.parse(source)
        for (resource in result.resources) {
            if (resource is PipelineProfileResource) {
                sourceByMetadataName[resource.metadata.name] = source
            }
        }
    }

    // Build catalog: derive ref from source path, use original document
    return sources
        .mapNotNull { source ->
            val result = resourceParser.parse(source)
            val profile = result.resources.filterIsInstance<PipelineProfileResource>().firstOrNull()
                ?: return@mapNotNull null
            val originalSource = sourceByMetadataName[profile.metadata.name]
                ?: return@mapNotNull null
            // Derive catalog ref from file path: "profiles/java.yaml" -> "catalog://profiles/java"
            val catalogRef = ResourceRef.parse(
                "catalog://${originalSource.path.removeSuffix(".yaml").removeSuffix(".yml")}"
            )
            catalogRef to originalSource
        }
        .toMap()
}

/**
 * Simple in-memory [CatalogSource] backed by a map of [ResourceRef] to [SourceDocument].
 *
 * This is used by both [dev.rubentxu.pipelattice.fleet.diff.domain.CompileAffectedValidator]
 * and [GitSnapshotRepository] for building the composition catalog from candidate sources.
 *
 * Resolution is a simple exact-match lookup — no aliasing or fallback logic.
 */
internal class SimpleCatalogSource(
    private val documents: Map<ResourceRef, SourceDocument>
) : CatalogSource {
    override fun resolve(ref: ResourceRef, sink: DiagnosticSink): SourceDocument? {
        return documents[ref]
    }
}
