package dev.rubentxu.pipelattice.fleet.diff.domain

import dev.rubentxu.pipelattice.compose.CompositionEngine
import dev.rubentxu.pipelattice.compose.domain.CompositionRequest
import dev.rubentxu.pipelattice.compose.domain.CompositionResult
import dev.rubentxu.pipelattice.compose.ports.CatalogSource
import dev.rubentxu.pipelattice.compose.ports.ProvenanceSink
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticCode
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
import dev.rubentxu.pipelattice.resource.PipelineDefinitionResource
import dev.rubentxu.pipelattice.resource.PipelineProfileResource
import dev.rubentxu.pipelattice.resource.ResourceParser
import dev.rubentxu.pipelattice.resource.SourceDocument

/**
 * Validates affected projects by re-composing their YAML sets.
 *
 * For each affected project, this validator runs the composition engine
 * and collects projects whose composition fails (ERROR diagnostics).
 * These become [PlanReference] entries in [FleetDiffReport.invalidPlans].
 *
 * ## Diagnostic codes
 * - `E-COMPOSE-AFFECTED-001`: composition failed for the affected project
 *
 * @see FleetDiffReport
 */
public class CompileAffectedValidator(
    private val compositionEngine: CompositionEngine,
    private val resourceParser: ResourceParser,
) : (Set<ProjectRef>) -> Set<PlanReference> {

    /**
     * Validates the given affected projects by re-composing their YAML sets.
     *
     * @param affectedProjects The set of affected projects to validate.
     * @return A set of [PlanReference] for projects whose composition failed.
     */
    override fun invoke(affectedProjects: Set<ProjectRef>): Set<PlanReference> {
        val invalidPlans = mutableSetOf<PlanReference>()

        for (projectRef in affectedProjects) {
            val compositionResult = runComposition(projectRef)
            if (compositionResult.hasErrors) {
                invalidPlans.add(
                    PlanReference(
                        projectId = projectRef.ref,
                        planDigest = compositionResult.fingerprint,
                    )
                )
            }
        }

        return invalidPlans
    }

    /**
     * Runs composition for a project and returns the result.
     */
    private fun runComposition(projectRef: ProjectRef): CompositionResult {
        // Parse the project's YAML files
        val parsedSources = projectRef.sources.map { source ->
            val result = resourceParser.parse(source)
            result
        }

        // Build catalog from profile resources
        val profileRefs = parsedSources
            .flatMap { it.resources }
            .filterIsInstance<PipelineProfileResource>()
            .associate {
                ResourceRef.parse("catalog://${it.metadata.name}") to
                    SourceDocument("catalog://${it.metadata.name}", "")
            }

        val catalogSource = SimpleCatalogSource(profileRefs)

        // Run composition for each pipeline definition
        for (resource in parsedSources.flatMap { it.resources }.filterIsInstance<PipelineDefinitionResource>()) {
            val request = CompositionRequest(resource)
            val emptySink = ProvenanceSink {
                // No-op
            }
            return compositionEngine.compose(request, catalogSource, emptySink)
        }

        // Return a result with errors if no pipeline definitions found
        return CompositionResult(
            pipelineId = projectRef.ref.canonicalForm,
            parameters = emptyMap(),
            provenance = emptyMap(),
            fingerprint = "",
            diagnostics = listOf(
                dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic(
                    code = dev.rubentxu.pipelattice.compose.diagnostics.CompositionDiagnosticCodes.UNKNOWN_KIND,
                    severity = DiagnosticSeverity.ERROR,
                    message = "No pipeline definition found for project: ${projectRef.ref.canonicalForm}"
                )
            )
        )
    }
}

/**
 * Reference to a project with its sources.
 */
public data class ProjectRef(
    val ref: ResourceRef,
    val sources: List<SourceDocument>,
)

/**
 * Simple catalog source for validation.
 */
private class SimpleCatalogSource(
    private val documents: Map<ResourceRef, SourceDocument>
) : dev.rubentxu.pipelattice.compose.ports.CatalogSource {
    override fun resolve(ref: ResourceRef, sink: DiagnosticSink): SourceDocument? {
        return documents[ref]
    }
}
