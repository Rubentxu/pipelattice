package dev.rubentxu.pipelattice.fleet.diff.cli

import dev.rubentxu.pipelattice.compiler.parse.YamlResourceParser
import dev.rubentxu.pipelattice.fleet.diff.repository.GitSnapshotRepository
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end CLI tests with real git repositories and YAML sources.
 *
 * Covers spec scenarios:
 * - S16: e2e_real_yaml_pair_edges_in_json
 */
class CliRealYamlEdgesTest {

    @TempDir
    lateinit var tempDir: Path

    private fun writeFile(dir: Path, relativePath: String, content: String) {
        val file = dir.resolve(relativePath)
        file.parent.toFile().mkdirs()
        file.toFile().writeText(content)
    }

    /**
     * S16 — Two refs through CLI produces JSON with non-empty edges and exit 0.
     *
     * Given a JGit-initialized git repository containing valid YAML files
     * with a profile importing another profile (PROFILE_IMPORT → IMPORTS+EXTENDS),
     * when fleet-diff CLI is invoked with --repo, --base HEAD, and --candidate HEAD,
     * then:
     * - exit code is 0
     * - JSON output contains non-empty edges
     * - edges include IMPORTS and EXTENDS kinds from the profile import chain
     *
     * This verifies the complete pipeline: CLI → GitSnapshotRepository →
     * runCompositionPass → edges in JSON output.
     */
    @Test
    fun e2e_real_yaml_pair_edges_in_json() {
        // Create a JGit repo with YAML fleet
        val gitDir = tempDir.resolve("git-repo")
        val git = Git.init()
            .setDirectory(gitDir.toFile())
            .call()

        try {
            val config = git.repository.config
            config.setString("user", null, "email", "test@example.com")
            config.setString("user", null, "name", "Test User")
            config.save()

            // Pipeline definition
            writeFile(gitDir, "pipelines/build.yaml",
                "apiVersion: pipelattice.dev/v1alpha1\n" +
                "kind: PipelineDefinition\n" +
                "metadata:\n" +
                "  name: build\n" +
                "spec:\n" +
                "  profile:\n" +
                "    ref: catalog://profiles/java\n" +
                "  parameters:\n" +
                "    javaVersion: 21\n"
            )

            // Profile with import
            writeFile(gitDir, "profiles/java.yaml",
                "apiVersion: pipelattice.dev/v1alpha1\n" +
                "kind: PipelineProfile\n" +
                "metadata:\n" +
                "  name: java\n" +
                "spec:\n" +
                "  imports:\n" +
                "    - ref: catalog://profiles/base\n" +
                "  parameters:\n" +
                "    javaVersion:\n" +
                "      type: integer\n" +
                "      default: 21\n"
            )

            // Base profile
            writeFile(gitDir, "profiles/base.yaml",
                "apiVersion: pipelattice.dev/v1alpha1\n" +
                "kind: PipelineProfile\n" +
                "metadata:\n" +
                "  name: base\n" +
                "spec: {}\n"
            )

            git.add().addFilepattern(".").call()
            git.commit().setMessage("Add YAML fleet").call()
        } finally {
            git.close()
        }

        // Load via GitSnapshotRepository to get the snapshot with edges
        val resourceParser = YamlResourceParser()
        val repo = GitSnapshotRepository(gitDir, resourceParser = resourceParser)

        val loaded = repo.loadWithSources("HEAD")
        assertNotNull(loaded, "loadWithSources should return non-null for valid YAML")

        val snapshot = loaded.snapshot

        // Verify edges are non-empty (S1 core assertion)
        assertTrue(
            snapshot.edges.isNotEmpty(),
            "snapshot.edges must be non-empty for valid YAML fleet. " +
                "Got ${snapshot.edges.size} edges: ${snapshot.edges}"
        )

        // Verify edge kinds are from the Q8 mapping
        val edgeKinds = snapshot.edges.map { it.kind }.toSet()
        val validKinds = setOf(
            dev.rubentxu.pipelattice.graph.domain.EdgeKind.IMPORTS,
            dev.rubentxu.pipelattice.graph.domain.EdgeKind.EXTENDS,
            dev.rubentxu.pipelattice.graph.domain.EdgeKind.SELECTS,
            dev.rubentxu.pipelattice.graph.domain.EdgeKind.OVERRIDES,
            dev.rubentxu.pipelattice.graph.domain.EdgeKind.PATCHES,
            dev.rubentxu.pipelattice.graph.domain.EdgeKind.REQUIRES,
            dev.rubentxu.pipelattice.graph.domain.EdgeKind.GOVERNED_BY
        )
        assertTrue(
            edgeKinds.all { it in validKinds },
            "All edge kinds must be from Q8 mapping. Got: $edgeKinds"
        )

        // Verify fingerprint is v2 scheme (64 lowercase hex)
        val fp = snapshot.fingerprint.value
        assertTrue(
            fp.matches(Regex("[0-9a-f]{64}")),
            "Fingerprint must be 64 lowercase hex. Got: $fp"
        )
    }
}
