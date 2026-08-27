package dev.rubentxu.pipelattice.fleet.diff.domain

import dev.rubentxu.pipelattice.compose.CompositionEngine
import dev.rubentxu.pipelattice.compose.createCompositionEngine
import dev.rubentxu.pipelattice.compiler.parse.YamlResourceParser
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import dev.rubentxu.pipelattice.graph.store.InMemoryGraphProjectionStore
import dev.rubentxu.pipelattice.resource.SourceDocument
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression test for W-NEW-2: CompileAffectedValidator with real composition engine
 * and profile import chains.
 *
 * ## Bug description
 * CompileAffectedValidator built its composition catalog with EMPTY-content SourceDocuments:
 * `SourceDocument("catalog://${it.metadata.name}", "")`. Under the real DefaultCompositionEngine,
 * when a profile uses imports (profile A imports profile B), ImportResolver.resolve() would
 * call catalog.resolve() which returned the empty-content document. The YAML parser then failed
 * on empty content, causing false E-COMPOSE-AFFECTED-001 errors.
 *
 * ## Fix
 * The shared ProfileCatalogBuilder.buildProfileCatalog() now uses ORIGINAL SourceDocument content
 * and derives catalog refs from file paths ("profiles/java.yaml" -> "catalog://profiles/java"),
 * matching GitSnapshotRepository.runCompositionPass.
 *
 * ## Test strategy
 * Uses the REAL DefaultCompositionEngine (via createCompositionEngine) with a fixture where:
 * - profile A (profiles/base.yaml) has parameters but no imports
 * - profile B (profiles/derived.yaml) imports profile A
 * - pipeline (pipelines/svc.yaml) uses profile B
 *
 * The validator must NOT flag the pipeline as invalid (no E-COMPOSE-AFFECTED-001).
 * This proves the import chain is resolved correctly with actual content.
 */
class CompileAffectedValidatorRealCatalogImportChainTest {

    /**
     * W-NEW-2 — real DefaultCompositionEngine with profile import chain:
     * affected project whose profile uses imports must NOT be falsely flagged invalid.
     *
     * Given:
     * - profiles/base.yaml: a profile with a parameter (no imports)
     * - profiles/derived.yaml: a profile that imports base.yaml
     * - pipelines/svc.yaml: a pipeline that uses the derived profile
     *
     * When the validator runs with the real DefaultCompositionEngine,
     * then composition succeeds (import chain resolves) and the project
     * is NOT flagged as invalid.
     */
    @Test
    fun profile_import_chain_composes_successfully_not_flagged_invalid() {
        val resourceParser = YamlResourceParser()
        // Use the REAL production composition engine, not fakes
        val compositionEngine: CompositionEngine = createCompositionEngine(YamlResourceParser())
        val validator = CompileAffectedValidator(compositionEngine, resourceParser)

        val project = GraphNode.Project(ResourceRef("projects/svc"))
        val profileBase = GraphNode.PipelineProfile(ResourceRef("profiles/base"))
        val profileDerived = GraphNode.PipelineProfile(ResourceRef("profiles/derived"))

        // Baseline: empty snapshot (no edges yet)
        val baseline = GraphSnapshot(
            nodes = setOf(project, profileBase, profileDerived),
            edges = emptySet(),
            fingerprint = PlanFingerprint("0".repeat(64)),
        )

        // Candidate: project selected by derived profile (structural diff triggers affectedProjects)
        val candidateEdge = Edge(profileDerived, project, EdgeKind.SELECTS)
        val candidate = GraphSnapshot(
            nodes = setOf(project, profileBase, profileDerived),
            edges = setOf(candidateEdge),
            fingerprint = PlanFingerprint("1".repeat(64)),
        )

        // Candidate sources: base profile (no imports), derived profile (imports base), and the pipeline
        val candidateSources = listOf(
            SourceDocument(
                path = "profiles/base.yaml",
                content = """
                    |apiVersion: pipelattice.dev/v1alpha1
                    |kind: PipelineProfile
                    |metadata:
                    |  name: base
                    |spec:
                    |  parameters:
                    |    java_version:
                    |      default: "21"
                """.trimMargin().trim()
            ),
            SourceDocument(
                path = "profiles/derived.yaml",
                content = """
                    |apiVersion: pipelattice.dev/v1alpha1
                    |kind: PipelineProfile
                    |metadata:
                    |  name: derived
                    |spec:
                    |  imports:
                    |    - catalog://profiles/base
                    |  parameters:
                    |    derived_param:
                    |      default: "value"
                """.trimMargin().trim()
            ),
            SourceDocument(
                path = "pipelines/svc.yaml",
                content = """
                    |apiVersion: pipelattice.dev/v1alpha1
                    |kind: PipelineDefinition
                    |metadata:
                    |  name: svc
                    |spec:
                    |  profile:
                    |    ref: catalog://profiles/derived
                """.trimMargin().trim()
            )
        )

        val invalidPlans = validator(
            affectedProjects = setOf(ResourceRef("projects/svc")),
            candidateSnapshot = candidate,
            candidateSources = candidateSources,
        )

        // W-NEW-2: the project must NOT be flagged invalid.
        // With the EMPTY-content bug, ImportResolver would fail to resolve the import
        // chain and produce E-COMPOSE-AFFECTED-001 errors.
        val svcInvalidPlans = invalidPlans.filter {
            it.projectId == ResourceRef("projects/svc")
        }

        assertTrue(
            svcInvalidPlans.isEmpty(),
            "projects/svc with imported profile should NOT be flagged invalid. " +
                "Import chain (derived -> base) must resolve correctly with real content. " +
                "Got invalidPlans: $svcInvalidPlans"
        )
    }
}
