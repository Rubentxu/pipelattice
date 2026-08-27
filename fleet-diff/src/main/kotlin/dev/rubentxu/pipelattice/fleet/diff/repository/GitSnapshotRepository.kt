package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.compiler.parse.YamlResourceParser
import dev.rubentxu.pipelattice.fleet.diff.cache.SnapshotDiskCache
import dev.rubentxu.pipelattice.fleet.diff.domain.SnapshotRepository
import dev.rubentxu.pipelattice.fleet.diff.ports.GitRefResolution
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.resource.ResourceParser
import org.eclipse.jgit.errors.AmbiguousObjectException
import org.eclipse.jgit.errors.IncorrectObjectTypeException
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.revwalk.RevWalk
import java.io.IOException
import java.nio.file.Path

/**
 * Git-backed [SnapshotRepository] implementation that resolves refs via JGit.
 *
 * ## Content emission (m15)
 * This implementation loads YAML files from a git ref via JGit tree walk,
 * parses them through the [ResourceParser] port (not the concrete [YamlResourceParser] class),
 * and emits a real [GraphSnapshot] with content-derived fingerprint. Results are cached
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
) : SnapshotRepository {

    /**
     * Loads a [GraphSnapshot] by resolving the given git ref using JGit,
     * parsing YAML sources, and caching the result.
     *
     * @param ref A git ref (branch, tag, SHA, HEAD, HEAD~N, etc.).
     * @return A content-derived [GraphSnapshot] if the ref resolves and YAML parses successfully,
     *         or `null` if the ref does not exist, is ambiguous, or any YAML fails to parse.
     * @throws GitRepositoryUnavailableException if the working directory is not a git repository
     *         or the git object store is inaccessible.
     */
    override fun load(ref: String): GraphSnapshot? {
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
                val cached = cache.get(cacheKey)
                if (cached != null) {
                    return cached
                }

                // Cache miss — create snapshot via factory
                val resolution = GitRefResolution.Resolved(sha)
                val snapshot = snapshotFactory.create(resolution, sources, resourceParser)

                // Persist on success (snapshot is null on parse error → no cache write)
                if (snapshot != null) {
                    cache.put(cacheKey, snapshot)
                }

                snapshot
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
}
