package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.resource.SourceDocument
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.TreeWalk
import java.nio.charset.StandardCharsets

/**
 * Loads YAML source documents from a git commit via JGit tree walk.
 *
 * Walks the commit's tree recursively, filters to `.yaml` / `.yml` files,
 * reads each blob as UTF-8 bytes, and returns a sorted list of [SourceDocument].
 *
 * ## Filtering
 * - Only files with extension `.yaml` or `.yml` (case-sensitive, lower-case) are included.
 * - Paths containing `.git/` as a segment are skipped (defensive; JGit does not
 *   normally descend into `.git/).
 *
 * ## Ordering
 * - Results are sorted lexicographically by path (ascending) so the returned list
 *   is deterministic regardless of tree-walk order.
 *
 * ## Resource limits
 * - A soft warning is emitted to `System.err` if more than 1000 documents are found;
 *   only the first 1000 (by tree-walk order) are returned.
 *
 * @param repo The JGit repository (must not be null; caller is responsible for closing).
 */
public class GitTreeLoader(private val repo: Repository) {

    private companion object {
        private const val MAX_DOCUMENTS = 1000
        private val YAML_EXTENSIONS = setOf(".yaml", ".yml")
    }

    /**
     * Loads all YAML source documents from the given commit's tree.
     *
     * @param commit The resolved commit.
     * @return A sorted list of [SourceDocument] for each `.yaml` / `.yml` file in the tree.
     */
    public fun loadSources(commit: RevCommit): List<SourceDocument> {
        val documents = mutableListOf<SourceDocument>()

        TreeWalk(repo).use { walk ->
            walk.addTree(commit.tree)
            walk.isRecursive = true

            while (walk.next()) {
                val pathString = walk.pathString

                // Defensive: skip .git/ segments
                if (pathString.contains(".git/")) continue

                // Filter by extension
                val ext = extensionOf(pathString)
                if (ext !in YAML_EXTENSIONS) continue

                val blobId = walk.getObjectId(0)
                val content = String(repo.open(blobId).bytes, StandardCharsets.UTF_8)

                documents.add(SourceDocument(path = pathString, content = content))

                if (documents.size >= MAX_DOCUMENTS) {
                    System.err.println("[GitTreeLoader] Warning: tree contains more than $MAX_DOCUMENTS " +
                        "YAML files; only the first $MAX_DOCUMENTS are loaded.")
                    break
                }
            }
        }

        return documents.sortedBy { it.path }
    }

    private fun extensionOf(path: String): String {
        val lastDot = path.lastIndexOf('.')
        return if (lastDot >= 0) path.substring(lastDot) else ""
    }
}
