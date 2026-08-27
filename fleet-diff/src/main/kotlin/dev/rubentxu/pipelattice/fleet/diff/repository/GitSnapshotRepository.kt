package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.compiler.parse.YamlResourceParser
import dev.rubentxu.pipelattice.compose.CompositionEngine
import dev.rubentxu.pipelattice.compose.createCompositionEngine
import dev.rubentxu.pipelattice.compose.domain.CompositionRequest
import dev.rubentxu.pipelattice.compose.domain.CompositionResult
import dev.rubentxu.pipelattice.compose.domain.ExplainResult
import dev.rubentxu.pipelattice.compose.ports.CatalogSource
import dev.rubentxu.pipelattice.compose.ports.ProvenanceSink
import dev.rubentxu.pipelattice.compose.translate.CompositionToGraphTranslator
import dev.rubentxu.pipelattice.fleet.diff.cache.SnapshotDiskCache
import dev.rubentxu.pipelattice.fleet.diff.domain.SnapshotRepository
import dev.rubentxu.pipelattice.fleet.diff.ports.GitRefResolution
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSink
import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.resource.PipelineDefinitionResource
import dev.rubentxu.pipelattice.resource.PipelineProfileResource
import dev.rubentxu.pipelattice.resource.ResourceParser
import dev.rubentxu.pipelattice.resource.SourceDocument
import org.eclipse.jgit.errors.AmbiguousObjectException
import org.eclipse.jgit.errors.IncorrectObjectTypeException
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.revwalk.RevWalk
import java.io.IOException
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Result of loading a snapshot from a git repository, including the sources used.
 *
 * This is returned by [GitSnapshotRepository.loadWithSources] to provide both
 * the [snapshot] and the [sources] used to create it. This allows downstream
 * consumers (like [dev.rubentxu.pipelattice.fleet.diff.domain.CompileAffectedValidator])
 * to re-compose affected projects without re-parsing from git.
 *
 * @property snapshot The loaded graph snapshot (with edges already populated by the repository).
 * @property sources The source documents that were parsed to create the snapshot.
 */
public data class LoadedSnapshot(
    val snapshot: GraphSnapshot,
    val sources: List<SourceDocument>,
)

/**
 * Git-backed [SnapshotRepository] implementation that resolves refs via JGit.
 *
 * ## Content emission (m15+v2)
 * This implementation loads YAML files from a git ref via JGit tree walk,
 * parses them through the [ResourceParser] port (not the concrete [YamlResourceParser] class),
 * runs the composition engine to emit edges via [CompositionToGraphTranslator], and returns
 * a real [GraphSnapshot] with content-derived fingerprint (v2 scheme). Results are cached
 * on disk keyed by `<ref-sha>:<input-hash>`.
 *
 * This implementation uses JGit's pure-JVM IO and MUST NOT use `ProcessBuilder`,
 * `Runtime.exec`, `java.lang.Process`, `kotlin.system.exitProcess`,
 * or `org.apache.tools.ant.taskdefs.Execute` directly.
 * This invariant is enforced by FARCH-016 v1 (ArchUnit package-import check).
 *
 * ## Exit code mapping
 * - JGit `Repository.resolve(ref)` returns ObjectId → [GitRefResolution.Resolved] → snapshot returned.
 * - JGit returns null (ref not found) or throws `AmbiguousObjectException` / `MissingObjectException` /
 *   `IncorrectObjectTypeException` → `null` returned (consumed by [FleetCandidateDiff.diff] →
 *   `IllegalArgumentException` → CLI exit 2).
 * - JGit throws `IOException` (not a git repo, corrupted store) → [GitRepositoryUnavailableException]
 *   (consumed by CLI generic `Exception` handler → CLI exit 10).
 * - Parse error (any YAML fails) → `null` returned (CLI exit 2), diagnostics on stderr.
 *
 * @param workingDir The git working tree directory. Must be a valid git repository.
 * @param resourceParser Parser for YAML source documents. Defaults to [YamlResourceParser] (production convenience;
 *        tests inject [FakeResourceParser][dev.rubentxu.pipelattice.fleet.diff.repository.FakeResourceParser]).
 * @param snapshotFactory Factory for creating [GraphSnapshot] from source documents.
 *        Defaults to [GitSnapshotFactory].
 * @param cache Disk cache for resolved snapshots. Defaults to `${XDG_CACHE_HOME:-~/.cache}/pipelattice/fleet-snapshots/<repo-id>/`.
 * @param compositionEngine Engine for running composition to emit edges. Defaults to a real engine
 *        via [createCompositionEngine]. Use a [NoOpCompositionEngine] in tests.
 * @throws GitRepositoryUnavailableException when the working directory is not a git repository
 *         or the git object store is inaccessible.
 * @see GitSnapshotFactory
 * @see GitTreeLoader
 * @see SnapshotDiskCache
 */
public class GitSnapshotRepository(
    private val workingDir: Path,
    private val resourceParser: ResourceParser = YamlResourceParser(),
    private val snapshotFactory: GitSnapshotFactory = GitSnapshotFactory(),
    private val cache: SnapshotDiskCache = SnapshotDiskCache.defaultFor(workingDir),
    private val compositionEngine: CompositionEngine = createCompositionEngine(YamlResourceParser()),
) : SnapshotRepository {

    /**
     * Loads a [GraphSnapshot] by resolving the given git ref using JGit,
     * parsing YAML sources, running composition for edges, and caching the result.
     *
     * @param ref A git ref (branch, tag, SHA, HEAD, HEAD~N, etc.).
     * @return A content-derived [GraphSnapshot] if the ref resolves and YAML parses successfully,
     *         or `null` if the ref does not exist, is ambiguous, or any YAML fails to parse.
     * @throws GitRepositoryUnavailableException if the working directory is not a git repository
     *         or the git object store is inaccessible.
     */
    override fun load(ref: String): GraphSnapshot? = loadWithSources(ref)?.snapshot

    /**
     * Loads a [GraphSnapshot] by resolving the given git ref, returning both the snapshot
     * and the sources used to create it.
     *
     * This method is identical to [load] but additionally returns the [SourceDocument] list
     * used to create the snapshot. This is useful for downstream consumers that need to
     * re-compose affected projects without re-parsing from git.
     *
     * @param ref A git ref (branch, tag, SHA, HEAD, HEAD~N, etc.).
     * @return A [LoadedSnapshot] containing the snapshot and sources, or `null` if the ref
     *         does not exist, is ambiguous, or any YAML fails to parse.
     * @throws GitRepositoryUnavailableException if the working directory is not a git repository
     *         or the git object store is inaccessible.
     */
    public fun loadWithSources(ref: String): LoadedSnapshot? {
        // Check upfront that .git exists - JGit's FileRepositoryBuilder doesn't reliably
        // throw when given a non-git directory, so we validate explicitly.
        val gitDir = workingDir.resolve(".git").toFile()
        if (!gitDir.exists() || !gitDir.isDirectory) {
            throw GitRepositoryUnavailableException(
                "git unavailable at '$workingDir': not a git repository",
                null,
            )
        }

        val repo = FileRepositoryBuilder()
            .setGitDir(gitDir)
            .readEnvironment()
            .findGitDir()
            .build()

        return try {
            // Detect ambiguous refs (branch + tag with same name) BEFORE resolving.
            // JGit's Repository.resolve() in 6.10.1 silently picks one ref instead of
            // throwing AmbiguousObjectException for the branch+tag case. The old
            // subprocess git transport errored with "fatal: ambiguous argument" — exit
            // code 2 in our CLI. Preserve that byte-identical behavior.
            val refDatabase = repo.refDatabase
            val matchingRefs = buildList {
                addAll(refDatabase.getRefsByPrefix("refs/heads/", "refs/tags/", "refs/remotes/").filter {
                    it.name.substringAfterLast('/') == ref
                })
            }
            if (matchingRefs.size > 1) {
                return null
            }

            val revWalk = RevWalk(repo)
            try {
                val objectId = repo.resolve(ref)
                    ?: return null // ref not found

                val commit = revWalk.parseCommit(objectId)
                val sha = commit.name

                // Load YAML sources via JGit tree walk
                val treeLoader = GitTreeLoader(repo)
                val sources = treeLoader.loadSources(commit)

                // Compute cache key
                val cacheKey = cache.key(sha, sources)

                // Cache lookup — short-circuit on hit
                // NOTE: v1 and v2 cache files have the same cache key (sha + inputHash)
                // but different fingerprint values. We must validate the scheme to avoid
                // returning a v1 cache entry when v2 is expected.
                val cached = cache.get(cacheKey)
                if (cached != null) {
                    // Verify this is a v2 snapshot (not stale v1). The v2 fingerprint
                    // is computed as SHA-256("graph-content/v2:<sha>:<inputHash>").
                    // If the cached fingerprint doesn't match the expected v2 fingerprint,
                    // treat as cache miss to auto-invalidate m15 v1 cache files.
                    val expectedV2Fingerprint = sha256Hex("graph-content/v2:${sha}:${SnapshotDiskCache.computeInputHash(sources)}")
                    if (cached.fingerprint.value == expectedV2Fingerprint) {
                        return LoadedSnapshot(cached, sources)
                    }
                    // Cache miss: stale v1 cache or fingerprint mismatch — recompute v2
                }

                // Cache miss — create snapshot via factory
                val resolution = GitRefResolution.Resolved(sha)
                val snapshot = snapshotFactory.create(resolution, sources, resourceParser)

                if (snapshot == null) {
                    return null
                }

                // Run composition pass to get edges (m16 v2)
                val edges = runCompositionPass(sources, resourceParser)
                val snapshotWithEdges = snapshot.copy(edges = edges)

                // Persist on success
                cache.put(cacheKey, snapshotWithEdges)

                LoadedSnapshot(snapshotWithEdges, sources)
            } catch (e: MissingObjectException) {
                null
            } catch (e: IncorrectObjectTypeException) {
                null
            } catch (e: AmbiguousObjectException) {
                null
            } finally {
                revWalk.close()
            }
        } catch (e: AmbiguousObjectException) {
            null
        } catch (e: IOException) {
            throw GitRepositoryUnavailableException(
                "git unavailable at '$workingDir': ${e.message ?: "repository not found"}",
                e,
            )
        } finally {
            repo.close()
        }
    }

    /**
     * Runs the composition pass over sources to emit graph edges.
     */
    private fun runCompositionPass(
        sources: List<SourceDocument>,
        resourceParser: ResourceParser,
    ): Set<Edge> {
        if (sources.isEmpty()) return emptySet()

        // Parse sources to get parsed resources
        val parsedSources = mutableListOf<dev.rubentxu.pipelattice.resource.ParsedResource>()
        for (source in sources) {
            val result = resourceParser.parse(source)
            parsedSources.addAll(result.resources)
        }

        // Build catalog from profile resources.
        // CRITICAL: we must use the ORIGINAL SourceDocument content (with actual YAML),
        // not empty strings. The SimpleRepositoryCatalogSource was incorrectly creating
        // empty-content documents, which caused ImportResolver.resolve() to fail because
        // the parser would fail on empty content and return null, breaking the import chain.
        // Fix: build a map from profile metadata name -> original SourceDocument.
        // Then derive the catalog ref from the source path: "profiles/java.yaml" -> "catalog://profiles/java".
        // This catalog ref is what ImportResolver.resolve() will look up.
        val sourceByMetadataName = mutableMapOf<String, SourceDocument>()
        for (source in sources) {
            val result = resourceParser.parse(source)
            for (resource in result.resources) {
                if (resource is PipelineProfileResource) {
                    sourceByMetadataName[resource.metadata.name] = source
                }
            }
        }

        val profileRefs = parsedSources
            .filterIsInstance<PipelineProfileResource>()
            .mapNotNull { profile ->
                val originalSource = sourceByMetadataName[profile.metadata.name]
                    ?: return@mapNotNull null
                // Derive catalog ref from file path: "profiles/java.yaml" -> "catalog://profiles/java"
                val catalogRef = dev.rubentxu.pipelattice.foundation.ResourceRef.parse(
                    "catalog://${originalSource.path.removeSuffix(".yaml").removeSuffix(".yml")}"
                )
                catalogRef to originalSource
            }
            .toMap()

        val catalogSource = SimpleRepositoryCatalogSource(profileRefs)

        // Collect all edges from composition results
        val allEdges = mutableSetOf<Edge>()
        val translator = CompositionToGraphTranslator()

        for (resource in parsedSources.filterIsInstance<PipelineDefinitionResource>()) {
            val request = CompositionRequest(resource)
            val emptySink = ProvenanceSink {
                // No-op for edge collection
            }
            val result = compositionEngine.compose(request, catalogSource, emptySink)
            val changeSet = translator.translate(result)
            allEdges.addAll(changeSet.addedEdges)
        }

        return allEdges
    }

    /**
     * Computes SHA-256 hex string for a given input string.
     * Used for fingerprint computation to validate cached snapshots.
     */
    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * No-op composition engine for repository (test only).
 */
private class NoOpCompositionEngine : CompositionEngine {
    override fun compose(
        request: CompositionRequest,
        catalog: CatalogSource,
        provenance: ProvenanceSink,
    ): CompositionResult {
        return CompositionResult(
            pipelineId = request.definition.metadata.name,
            parameters = emptyMap(),
            provenance = emptyMap(),
            fingerprint = "",
            diagnostics = emptyList()
        )
    }

    override fun explain(result: CompositionResult, path: String): ExplainResult {
        return ExplainResult.Miss
    }
}

/**
 * Simple catalog source for repository use.
 */
private class SimpleRepositoryCatalogSource(
    private val documents: Map<dev.rubentxu.pipelattice.foundation.ResourceRef, SourceDocument>
) : CatalogSource {
    override fun resolve(ref: dev.rubentxu.pipelattice.foundation.ResourceRef, sink: DiagnosticSink): SourceDocument? {
        return documents[ref]
    }
}
