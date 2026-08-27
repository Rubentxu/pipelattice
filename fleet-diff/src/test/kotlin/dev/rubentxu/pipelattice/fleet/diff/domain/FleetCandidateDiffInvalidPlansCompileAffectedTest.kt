package dev.rubentxu.pipelattice.fleet.diff.domain

import dev.rubentxu.pipelattice.compose.CompositionEngine
import dev.rubentxu.pipelattice.compose.domain.CompositionRequest
import dev.rubentxu.pipelattice.compose.domain.CompositionResult
import dev.rubentxu.pipelattice.compose.domain.ExplainResult
import dev.rubentxu.pipelattice.compose.ports.CatalogSource
import dev.rubentxu.pipelattice.compose.ports.ProvenanceSink
import dev.rubentxu.pipelattice.compiler.parse.YamlResourceParser
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticCode
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
import dev.rubentxu.pipelattice.fleet.diff.repository.InMemorySnapshotRepository
import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import dev.rubentxu.pipelattice.graph.store.InMemoryGraphProjectionStore
import dev.rubentxu.pipelattice.resource.SourceDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [CompileAffectedValidator] integration with [FleetCandidateDiff].
 *
 * Covers spec scenarios:
 * - S7: compile-affected failure populates invalidPlans as primary signal
 * - S8: compose-success excludes valid plans from invalidPlans
 */
class FleetCandidateDiffInvalidPlansCompileAffectedTest {

    /**
     * A [CompositionEngine] that returns ERROR diagnostics for specific pipeline names.
     * Used to simulate composition failures for affected project validation.
     */
    private class FakeFailingCompositionEngine(
        private val failingPipelineNames: Set<String> = setOf("example"),
    ) : CompositionEngine {
        override fun compose(
            request: CompositionRequest,
            catalog: CatalogSource,
            provenance: ProvenanceSink,
        ): CompositionResult {
            return if (request.definition.metadata.name in failingPipelineNames) {
                CompositionResult(
                    pipelineId = request.definition.metadata.name,
                    parameters = emptyMap(),
                    provenance = emptyMap(),
                    fingerprint = "fake-digest-${request.definition.metadata.name}",
                    diagnostics = listOf(
                        Diagnostic(
                            code = DiagnosticCode("E-COMPOSE-AFFECTED-001"),
                            severity = DiagnosticSeverity.ERROR,
                            message = "Composition failed for ${request.definition.metadata.name}",
                            location = SourceLocation(path = "pipelines/${request.definition.metadata.name}.yaml")
                        )
                    )
                )
            } else {
                CompositionResult(
                    pipelineId = request.definition.metadata.name,
                    parameters = emptyMap(),
                    provenance = emptyMap(),
                    fingerprint = "fake-digest-${request.definition.metadata.name}",
                    diagnostics = emptyList()
                )
            }
        }

        override fun explain(result: CompositionResult, path: String): ExplainResult = ExplainResult.Miss
    }

    /**
     * A [CompositionEngine] that always returns successful results (no errors).
     */
    private class FakeSuccessfulCompositionEngine : CompositionEngine {
        override fun compose(
            request: CompositionRequest,
            catalog: CatalogSource,
            provenance: ProvenanceSink,
        ): CompositionResult {
            return CompositionResult(
                pipelineId = request.definition.metadata.name,
                parameters = emptyMap(),
                provenance = emptyMap(),
                fingerprint = "fake-digest-${request.definition.metadata.name}",
                diagnostics = emptyList()
            )
        }

        override fun explain(result: CompositionResult, path: String): ExplainResult = ExplainResult.Miss
    }

    /**
     * S7 — compile-affected failure populates invalidPlans as primary source.
     *
     * Given a FleetCandidateDiff wired to a CompositionEngine that returns ERROR
     * diagnostics for project "projects/example", when diff() is called with
     * "projects/example" in the affected set, then invalidPlans contains
     * PlanReference(projectId=projects/example, diagnosticCode=E-COMPOSE-AFFECTED-001).
     */
    @Test
    fun compile_failure_populates_invalidPlans_primary() {
        val repo = InMemorySnapshotRepository()
        val store = InMemoryGraphProjectionStore()
        val resourceParser = YamlResourceParser()
        val failingEngine = FakeFailingCompositionEngine(setOf("example"))
        val validator = CompileAffectedValidator(failingEngine, resourceParser)

        val project = GraphNode.Project(ResourceRef("projects/example"))
        val profile = GraphNode.PipelineProfile(ResourceRef("profiles/java"))
        val profile2 = GraphNode.PipelineProfile(ResourceRef("profiles/golang"))

        // Baseline: project selected by java profile
        val baselineEdge = Edge(profile, project, EdgeKind.SELECTS)
        val baseline = GraphSnapshot(
            nodes = setOf(project, profile),
            edges = setOf(baselineEdge),
            fingerprint = PlanFingerprint("0".repeat(64)),
        )

        // Candidate: project selected by golang profile (structural diff triggers affectedProjects)
        val candidateEdge = Edge(profile2, project, EdgeKind.SELECTS)
        val candidate = GraphSnapshot(
            nodes = setOf(project, profile2),
            edges = setOf(candidateEdge),
            fingerprint = PlanFingerprint("1".repeat(64)),
        )

        repo.store("baseline", baseline)
        repo.store("candidate", candidate)

        // Source documents for the candidate (required for validator to run)
        val candidateSources = listOf(
            SourceDocument(
                path = "pipelines/example.yaml",
                content = """
                    |apiVersion: pipelattice.dev/v1alpha1
                    |kind: PipelineDefinition
                    |metadata:
                    |  name: example
                    |spec:
                    |  profile:
                    |    ref: catalog://profiles/golang
                """.trimMargin().trim()
            )
        )

        val diff = FleetCandidateDiff(
            repo,
            store,
            compileAffectedValidator = validator
        )
        val report = diff.diff("baseline", "candidate", candidateSources)

        // The affected set must be non-empty (structural diff between baseline and candidate)
        assertTrue(
            report.affectedProjects.isNotEmpty(),
            "affectedProjects must be non-empty due to structural diff. Got: ${report.affectedProjects}"
        )

        // The invalidPlans should contain the failing project with diagnosticCode
        val failingPlanRefs = report.invalidPlans.filter {
            it.projectId == ResourceRef("projects/example") && it.diagnosticCode == "E-COMPOSE-AFFECTED-001"
        }

        assertTrue(
            failingPlanRefs.isNotEmpty(),
            "invalidPlans should contain PlanReference for projects/example with " +
                "diagnosticCode=E-COMPOSE-AFFECTED-001. " +
                "Got: ${report.invalidPlans}"
        )

        // The diagnosticCode should be exactly E-COMPOSE-AFFECTED-001
        assertEquals(
            "E-COMPOSE-AFFECTED-001",
            failingPlanRefs.first().diagnosticCode,
            "diagnosticCode must be E-COMPOSE-AFFECTED-001"
        )
    }

    /**
     * S8 — compose-success excludes valid plans from invalidPlans.
     *
     * Given a FleetCandidateDiff wired to a CompositionEngine that returns SUCCESS
     * for all affected projects, when diff() is called, then invalidPlans
     * does NOT contain entries for those successfully composed projects
     * (only secondary heuristic entries if any).
     */
    @Test
    fun compose_success_excludes_valid_plans() {
        val repo = InMemorySnapshotRepository()
        val store = InMemoryGraphProjectionStore()
        val resourceParser = YamlResourceParser()
        val successfulEngine = FakeSuccessfulCompositionEngine()
        val validator = CompileAffectedValidator(successfulEngine, resourceParser)

        val projectA = GraphNode.Project(ResourceRef("projects/a"))
        val projectB = GraphNode.Project(ResourceRef("projects/b"))
        val profile = GraphNode.PipelineProfile(ResourceRef("profiles/java"))
        val profile2 = GraphNode.PipelineProfile(ResourceRef("profiles/golang"))

        // Baseline: projectA selected by java profile
        val baselineEdge = Edge(profile, projectA, EdgeKind.SELECTS)
        val baseline = GraphSnapshot(
            nodes = setOf(projectA, projectB, profile),
            edges = setOf(baselineEdge),
            fingerprint = PlanFingerprint("0".repeat(64)),
        )

        // Candidate: projectA selected by golang profile (structural diff triggers affectedProjects)
        val candidateEdge = Edge(profile2, projectA, EdgeKind.SELECTS)
        val candidate = GraphSnapshot(
            nodes = setOf(projectA, projectB, profile2),
            edges = setOf(candidateEdge),
            fingerprint = PlanFingerprint("1".repeat(64)),
        )

        repo.store("baseline", baseline)
        repo.store("candidate", candidate)

        // Source documents for affected projects (all composing successfully)
        val candidateSources = listOf(
            SourceDocument(
                path = "pipelines/a.yaml",
                content = """
                    |apiVersion: pipelattice.dev/v1alpha1
                    |kind: PipelineDefinition
                    |metadata:
                    |  name: a
                    |spec:
                    |  profile:
                    |    ref: catalog://profiles/golang
                """.trimMargin().trim()
            ),
            SourceDocument(
                path = "pipelines/b.yaml",
                content = """
                    |apiVersion: pipelattice.dev/v1alpha1
                    |kind: PipelineDefinition
                    |metadata:
                    |  name: b
                    |spec:
                    |  profile:
                    |    ref: catalog://profiles/java
                """.trimMargin().trim()
            )
        )

        val diff = FleetCandidateDiff(
            repo,
            store,
            compileAffectedValidator = validator
        )
        val report = diff.diff("baseline", "candidate", candidateSources)

        // affectedProjects must be non-empty (structural diff)
        assertTrue(
            report.affectedProjects.isNotEmpty(),
            "affectedProjects must be non-empty due to structural diff. Got: ${report.affectedProjects}"
        )

        // No invalidPlans from primary signal (compose succeeded for all)
        val primaryInvalidPlans = report.invalidPlans.filter { it.diagnosticCode != null }
        assertTrue(
            primaryInvalidPlans.isEmpty(),
            "No invalidPlans with diagnosticCode expected when composition succeeds. " +
                "Got: ${report.invalidPlans.filter { it.diagnosticCode != null }}"
        )

        // Verify affected projects are correctly identified (projectA was changed)
        val affectedProjectRefs = report.affectedProjects
            .filterIsInstance<GraphNode.Project>()
            .map { it.id }
            .toSet()
        assertTrue(
            affectedProjectRefs.contains(ResourceRef("projects/a")),
            "projects/a should be in affected set. Got: $affectedProjectRefs"
        )
    }
}
