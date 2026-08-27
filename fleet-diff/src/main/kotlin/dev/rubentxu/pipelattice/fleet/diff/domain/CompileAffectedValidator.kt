package dev.rubentxu.pipelattice.fleet.diff.domain

import dev.rubentxu.pipelattice.compose.CompositionEngine
import dev.rubentxu.pipelattice.compose.domain.CompositionRequest
import dev.rubentxu.pipelattice.compose.ports.ProvenanceSink
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity
import dev.rubentxu.pipelattice.fleet.diff.repository.SimpleCatalogSource
import dev.rubentxu.pipelattice.fleet.diff.repository.buildProfileCatalog
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.resource.PipelineDefinitionResource
import dev.rubentxu.pipelattice.resource.ResourceParser
import dev.rubentxu.pipelattice.resource.SourceDocument

/**
 * Validates affected projects by re-composing their YAML sets.
 *
 * For each affected project, this validator runs the composition engine against the
 * candidate sources and collects projects whose composition fails (ERROR diagnostics).
 * These become [PlanReference] entries in [FleetDiffReport.invalidPlans].
 *
 * ## Primary signal
 * This validator is the PRIMARY source of [invalidPlans] entries per spec §A S7.
 * The removed-edge heuristic is the secondary signal (see [SecondaryHeuristicReport]).
 *
 * ## Cache-hit behavior
 * When [candidateSources] is null (e.g., cache-hit path where sources are not available),
 * re-composition is not possible and this validator returns [emptySet]. This is acceptable
 * because cache hits imply the snapshot was already validated when it was first created.
 *
 * ## Diagnostic codes
 * - `E-COMPOSE-AFFECTED-001`: composition failed for the affected project
 *
 * @param compositionEngine The composition engine to use for re-composition.
 * @param resourceParser The resource parser for parsing source documents into parsed resources.
 * @see FleetDiffReport
 */
public class CompileAffectedValidator(
    private val compositionEngine: CompositionEngine,
    private val resourceParser: ResourceParser,
) {
    /**
     * Validates the given affected projects by re-composing their YAML sets.
     *
     * @param affectedProjects The set of affected project refs to validate.
     * @param candidateSnapshot The candidate snapshot (used to identify which pipelines exist).
     * @param candidateSources The source documents for the candidate ref. When non-null,
     *        the validator re-composes affected projects and returns [PlanReference] entries
     *        for projects with ERROR diagnostics. When null (cache-hit path), returns [emptySet].
     * @return [PlanReference] entries for projects whose re-composition failed, or [emptySet]
     *         when sources are unavailable.
     */
    public operator fun invoke(
        affectedProjects: Set<ResourceRef>,
        candidateSnapshot: GraphSnapshot,
        candidateSources: List<SourceDocument>?,
    ): Set<PlanReference> {
        if (candidateSources == null) {
            // Cannot re-compose without sources (e.g., cache-hit path)
            return emptySet()
        }

        if (affectedProjects.isEmpty()) {
            return emptySet()
        }

        // Parse sources using the provided resource parser
        val parsedSources = mutableListOf<dev.rubentxu.pipelattice.resource.ParsedResource>()
        for (source in candidateSources) {
            val result = resourceParser.parse(source)
            parsedSources.addAll(result.resources)
        }

        // Build catalog from profile resources using ORIGINAL SourceDocument content.
        // CRITICAL: we must use the actual YAML content, not empty strings.
        // buildProfileCatalog derives the catalog ref from the source file path
        // (e.g. "profiles/java.yaml" -> "catalog://profiles/java") and stores the
        // original document. This ensures ImportResolver can parse the content and
        // resolve import chains correctly.
        val profileCatalog = buildProfileCatalog(candidateSources, resourceParser)
        val catalogSource = SimpleCatalogSource(profileCatalog)

        // Get pipeline definition resources for affected projects
        val affectedPipelines = parsedSources
            .filterIsInstance<PipelineDefinitionResource>()
            .filter { resource ->
                val projectRef = ResourceRef.parse("catalog://pipelines/${resource.metadata.name}")
                affectedProjects.any { affected ->
                    // Check if this pipeline belongs to an affected project
                    // A pipeline "belongs" to a project if the project's plan references it
                    affectedProjects.any { affectedRef ->
                        affectedRef.canonicalForm.contains(resource.metadata.name) ||
                            resource.metadata.name.contains(affectedRef.canonicalForm.substringAfterLast('/'))
                    }
                }
            }

        val invalidPlans = mutableSetOf<PlanReference>()

        for (resource in affectedPipelines) {
            val request = CompositionRequest(resource)
            val emptySink = ProvenanceSink {
                // No-op for validation
            }
            val result = compositionEngine.compose(request, catalogSource, emptySink)

            // Check for ERROR diagnostics
            val hasErrors = result.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }
            if (hasErrors) {
                val planDigest = result.fingerprint.ifEmpty {
                    // Fallback: compute a placeholder digest from the pipeline name
                    resource.metadata.name.hashCode().toString(16)
                }
                invalidPlans.add(
                    PlanReference(
                        projectId = ResourceRef("projects/${resource.metadata.name}"),
                        planDigest = planDigest,
                        diagnosticCode = "E-COMPOSE-AFFECTED-001",
                    )
                )
            }
        }

        return invalidPlans
    }
}

