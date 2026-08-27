package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.fleet.diff.ports.GitRefResolution
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import dev.rubentxu.pipelattice.resource.ResourceParser
import dev.rubentxu.pipelattice.resource.SourceDocument
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Factory for creating content-derived [GraphSnapshot] from YAML sources at a git ref.
 *
 * ## Content emission (m15+v2)
 * Each [SourceDocument] is parsed via the [ResourceParser] port. If any parse error
 * occurs, the factory returns `null` and emits diagnostics to stderr.
 *
 * Each source file becomes one [GraphNode.ConfigSource] node. The node's `contentHash`
 * is the first 16 hex chars of SHA-256 of the raw YAML bytes.
 *
 * ## Edge emission (m16 v2)
 * Edge emission is handled by [GitSnapshotRepository.runCompositionPass]. This factory
 * creates snapshots with `edges = emptySet()`. The repository is the single source of
 * truth for edge emission in the production path.
 *
 * ## Fingerprint scheme (m16 v2)
 * `SHA-256("graph-content/v2:<refSha>:<inputHash>")` where `<inputHash>` =
 * SHA-256 of the sorted-by-path concatenated YAML content bytes. The domain tag
 * `graph-content/v2:` auto-invalidates m15 v1 cache files at the application layer.
 *
 * ## Error handling
 * When any [ResourceParser.parse] returns [dev.rubentxu.pipelattice.resource.ParseResult.hasErrors],
 * all ERROR diagnostics are written to stderr (one line each, prefix `[m15-parse-error]`)
 * and `create` returns `null`. The caller propagates `null` → CLI exit 2.
 *
 * @see GitSnapshotRepository
 * @see GitRefResolution.Resolved
 */
public class GitSnapshotFactory {

    /**
     * Creates a content-derived [GraphSnapshot] from YAML sources.
     *
     * Note: the returned snapshot has `edges = emptySet()`. Edge emission is handled
     * by [GitSnapshotRepository.runCompositionPass] after this factory returns.
     *
     * @param resolution The resolved ref (valid 40-hex SHA).
     * @param sources The YAML source documents at the ref (sorted by path, from [GitTreeLoader]).
     * @param resourceParser The parser to use for each document (port, not concrete class).
     * @return A [GraphSnapshot] with nodes and fingerprint populated, or `null` if any
     *         source fails to parse (with diagnostics on stderr).
     */
    public fun create(
        resolution: GitRefResolution.Resolved,
        sources: List<SourceDocument>,
        resourceParser: ResourceParser,
    ): GraphSnapshot? {
        val allDiagnostics = mutableListOf<dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic>()

        // Parse all sources; collect errors
        for (source in sources) {
            val result = resourceParser.parse(source)
            if (result.hasErrors) {
                allDiagnostics.addAll(result.diagnostics.filter { it.severity == DiagnosticSeverity.ERROR })
            }
        }

        if (allDiagnostics.isNotEmpty()) {
            for (diag in allDiagnostics) {
                System.err.println("[m15-parse-error] ${diag.location?.path ?: "unknown"}: ${diag.code}: ${diag.message}")
            }
            return null
        }

        // Compute input-hash: SHA-256(sorted-by-path concatenated YAML bytes)
        val inputHash = computeInputHash(sources)

        // Build one ConfigSource node per source file
        val nodes = sources.map { source ->
            val contentHash = sha256Hex(source.content).substring(0, 16)
            GraphNode.ConfigSource(Path.of(source.path), contentHash)
        }.toSet()

        // Content-derived fingerprint: domain-tagged, includes input-hash (v2 scheme)
        val fingerprintValue = sha256Hex("graph-content/v2:${resolution.sha}:$inputHash")

        return GraphSnapshot(
            nodes = nodes,
            edges = emptySet(), // Edges are emitted by GitSnapshotRepository.runCompositionPass
            fingerprint = PlanFingerprint(fingerprintValue),
        )
    }

    private fun computeInputHash(sources: List<SourceDocument>): String {
        val sortedSources = sources.sortedBy { it.path }
        val concatenatedBytes = sortedSources.joinToString("") { it.content }
        return sha256Hex(concatenatedBytes)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
