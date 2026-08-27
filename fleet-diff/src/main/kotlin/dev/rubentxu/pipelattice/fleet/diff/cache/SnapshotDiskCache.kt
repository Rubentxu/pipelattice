package dev.rubentxu.pipelattice.fleet.diff.cache

import dev.rubentxu.pipelattice.fleet.diff.repository.SourceDocument
import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.graph.domain.Edge
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import dev.rubentxu.pipelattice.graph.domain.GraphSnapshot
import dev.rubentxu.pipelattice.graph.domain.PlanFingerprint
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Disk-backed cache for [GraphSnapshot] objects.
 *
 * Cache location: `${XDG_CACHE_HOME:-~/.cache}/pipelattice/fleet-snapshots/<repo-id>/`
 * where `repo-id` = first 16 hex chars of SHA-256 of the absolute workingDir path.
 *
 * Cache key format: `<refSha>-<input-hash>` stored in the filename as `<safeFile(key)>.json`.
 *
 * ## Key derivation
 * - `refSha`: 40-hex commit SHA from JGit.
 * - `input-hash`: SHA-256 of sorted-by-path concatenated YAML bytes (UTF-8).
 *   Empty content → SHA-256 of the empty byte string.
 *
 * ## Atomic writes
 * Writes go to `<cacheDir>/<key>.tmp` then are atomically renamed to
 * `<cacheDir>/<key>.json` via [Files.move]. This prevents partial files on crash.
 *
 * ## Safety
 * The [safeFile] function replaces any path-separation characters (`/`, `\`, `..`)
 * in the key with `_` so the cache file stays inside [cacheDir].
 *
 * @param cacheDir The directory where cache files are stored. Created if absent.
 */
public class SnapshotDiskCache(private val cacheDir: Path) {

    init {
        Files.createDirectories(cacheDir)
    }

    /**
     * Creates a [SnapshotDiskCache] with the default cache directory for a working directory.
     *
     * Derives `repo-id` as the first 16 hex chars of SHA-256 of the absolute [workingDir] path.
     * Falls back to `~/.cache` if `XDG_CACHE_HOME` is not set.
     */
    public companion object {
        private const val CACHE_ROOT = "pipelattice/fleet-snapshots"

        public fun defaultFor(workingDir: Path): SnapshotDiskCache {
            val cacheHome = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }
                ?: "${System.getProperty("user.home")}/.cache"
            val absPath = workingDir.toAbsolutePath().invariantSeparatorsPathString
            val repoId = sha256Hex(absPath).substring(0, 16)
            val dir = Path.of(cacheHome, CACHE_ROOT, repoId)
            return SnapshotDiskCache(dir)
        }

        /**
         * Computes the input-hash for a list of source documents.
         *
         * The input-hash is SHA-256 of the sorted-by-path concatenated YAML bytes.
         * Used as part of the cache key derivation.
         */
        public fun computeInputHash(sources: List<SourceDocument>): String {
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

    /**
     * Generates the cache key for a given ref SHA and source documents.
     *
     * @param refSha The 40-hex commit SHA.
     * @param sources The source documents whose content determines the input-hash.
     * @return The cache key string `<refSha>-<input-hash>`.
     */
    public fun key(refSha: String, sources: List<SourceDocument>): String {
        val inputHash = computeInputHash(sources)
        return "$refSha-$inputHash"
    }

    /**
     * Retrieves a cached [GraphSnapshot] by key, or `null` if not found.
     *
     * @param key The cache key (as returned by [key]).
     * @return The deserialized [GraphSnapshot], or `null` if the cache file does not exist.
     */
    public fun get(key: String): GraphSnapshot? {
        val file = cacheDir.resolve("${safeFile(key)}.json")
        return try {
            if (!Files.exists(file)) return null
            val json = Files.readString(file)
            GraphSnapshotSerializer.decode(json)
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Persists a [GraphSnapshot] to the cache.
     *
     * Write is atomic: content is written to `<key>.tmp` then renamed to `<key>.json`.
     *
     * @param key The cache key (as returned by [key]).
     * @param snapshot The snapshot to persist.
     */
    public fun put(key: String, snapshot: GraphSnapshot) {
        val file = cacheDir.resolve("${safeFile(key)}.json")
        val tmpFile = cacheDir.resolve("${safeFile(key)}.json.tmp")
        try {
            val json = GraphSnapshotSerializer.encode(snapshot)
            Files.writeString(tmpFile, json)
            Files.move(tmpFile, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            // Clean up tmp file if something went wrong
            try { Files.deleteIfExists(tmpFile) } catch (_: IOException) { }
            throw e
        }
    }

    /**
     * Makes a key safe for use as a filename by replacing path-separation characters.
     */
    private fun safeFile(key: String): String {
        return key.replace("/", "_").replace("\\", "_").replace("..", "_")
    }
}

/**
 * Hand-written JSON serializer for [GraphSnapshot].
 *
 * Produces deterministic output: nodes and edges are sorted by their canonical
 * string representation so the same snapshot always encodes to the same JSON bytes.
 * This is required for cache invalidation: the on-disk file must be byte-identical
 * across runs with the same content.
 *
 * Schema:
 * ```json
 * {
 *   "fingerprint": "<64-hex>",
 *   "nodes": [{ "kind": "Project", "id": "..." }, ...],
 *   "edges": [{ "source": {...}, "target": {...}, "kind": "IMPORTS" }, ...]
 * }
 * ```
 */
public object GraphSnapshotSerializer {

    /**
     * Encodes [snapshot] to a JSON string.
     */
    public fun encode(snapshot: GraphSnapshot): String {
        return buildString {
            append("{")
            append("\"fingerprint\":\"${snapshot.fingerprint.value}\",")
            append("\"nodes\":")
            append(encodeNodes(snapshot.nodes))
            append(",")
            append("\"edges\":")
            append(encodeEdges(snapshot.edges))
            append("}")
        }
    }

    /**
     * Decodes a JSON string to a [GraphSnapshot].
     *
     * @throws IllegalArgumentException if the JSON is malformed.
     */
    public fun decode(json: String): GraphSnapshot {
        val fp = json.substringAfter("\"fingerprint\":\"").substringBefore('"')
        val nodesJson = json.substringAfter("\"nodes\":").substringBefore(",\"edges\":")
        val edgesJson = json.substringAfter("\"edges\":").substringBeforeLast("}")

        return GraphSnapshot(
            fingerprint = PlanFingerprint(fp),
            nodes = decodeNodes(nodesJson),
            edges = decodeEdges(edgesJson),
        )
    }

    private fun encodeNodes(nodes: Set<GraphNode>): String {
        if (nodes.isEmpty()) return "[]"
        return buildString {
            append("[")
            nodes.sortedBy { node -> node.toCanonical() }.mapIndexed { index, node ->
                if (index > 0) append(",")
                append(encodeNode(node))
            }
            append("]")
        }
    }

    private fun encodeNode(node: GraphNode): String {
        return when (node) {
            is GraphNode.Project -> "{\"kind\":\"Project\",\"id\":\"${node.id.canonicalForm}\"}"
            is GraphNode.Component -> "{\"kind\":\"Component\",\"id\":\"${node.id.canonicalForm}\",\"owner\":\"${node.owner.canonicalForm}\"}"
            is GraphNode.PipelineProfile -> "{\"kind\":\"PipelineProfile\",\"id\":\"${node.id.canonicalForm}\"}"
            is GraphNode.ConfigSource -> "{\"kind\":\"ConfigSource\",\"path\":\"${escape(node.path.toString())}\",\"contentHash\":\"${node.contentHash}\"}"
            is GraphNode.ResolvedPipelinePlan -> "{\"kind\":\"ResolvedPipelinePlan\",\"projectId\":\"${node.projectId.canonicalForm}\",\"planDigest\":\"${node.planDigest}\"}"
        }
    }

    private fun GraphNode.toCanonical(): String = when (this) {
        is GraphNode.Project -> "Project:${id.canonicalForm}"
        is GraphNode.Component -> "Component:${id.canonicalForm}:${owner.canonicalForm}"
        is GraphNode.PipelineProfile -> "PipelineProfile:${id.canonicalForm}"
        is GraphNode.ConfigSource -> "ConfigSource:${path}:${contentHash}"
        is GraphNode.ResolvedPipelinePlan -> "ResolvedPipelinePlan:${projectId.canonicalForm}:${planDigest}"
    }

    private fun encodeEdges(edges: Set<Edge>): String {
        if (edges.isEmpty()) return "[]"
        return buildString {
            append("[")
            edges.sortedBy { "${it.source.toCanonical()}|${it.target.toCanonical()}|${it.kind.name}" }.mapIndexed { index, edge ->
                if (index > 0) append(",")
                append("{\"source\":${encodeNode(edge.source)},\"target\":${encodeNode(edge.target)},\"kind\":\"${edge.kind.name}\"}")
            }
            append("]")
        }
    }

    private fun escape(s: String): String {
        return s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun decodeNodes(json: String): Set<GraphNode> {
        if (json == "[]") return emptySet()
        val content = json.trim().removePrefix("[").removeSuffix("]")
        if (content.isBlank()) return emptySet()

        val nodes = mutableListOf<GraphNode>()
        var i = 0
        var current = StringBuilder()
        var depth = 0
        var inString = false

        while (i < content.length) {
            val c = content[i]
            when {
                c == '"' && (i == 0 || content[i - 1] != '\\') -> {
                    inString = !inString
                    current.append(c)
                }
                c == '{' && !inString -> {
                    depth++
                    current.append(c)
                }
                c == '}' && !inString -> {
                    depth--
                    current.append(c)
                    if (depth == 0) {
                        nodes.add(decodeNode(current.toString()))
                        current = StringBuilder()
                        if (i + 1 < content.length && content[i + 1] == ',') i++
                    }
                }
                else -> current.append(c)
            }
            i++
        }

        return nodes.toSet()
    }

    private fun decodeEdges(json: String): Set<Edge> {
        if (json == "[]") return emptySet()
        val content = json.trim().removePrefix("[").removeSuffix("]")
        if (content.isBlank()) return emptySet()

        val edges = mutableListOf<Edge>()
        var i = 0
        var current = StringBuilder()
        var depth = 0
        var inString = false

        while (i < content.length) {
            val c = content[i]
            when {
                c == '"' && (i == 0 || content[i - 1] != '\\') -> {
                    inString = !inString
                    current.append(c)
                }
                c == '{' && !inString -> {
                    depth++
                    current.append(c)
                }
                c == '}' && !inString -> {
                    depth--
                    current.append(c)
                    if (depth == 0) {
                        edges.add(decodeEdge(current.toString()))
                        current = StringBuilder()
                        if (i + 1 < content.length && content[i + 1] == ',') i++
                    }
                }
                else -> current.append(c)
            }
            i++
        }

        return edges.toSet()
    }

    private fun decodeNode(json: String): GraphNode {
        val kind = json.substringAfter("\"kind\":\"").substringBefore('"')
        return when (kind) {
            "Project" -> {
                val id = json.substringAfter("\"id\":\"").substringBefore('"')
                GraphNode.Project(ResourceRef.parse(id))
            }
            "Component" -> {
                val id = json.substringAfter("\"id\":\"").substringBefore('"')
                val owner = json.substringAfter("\"owner\":\"").substringBefore('"')
                GraphNode.Component(ResourceRef.parse(id), ResourceRef.parse(owner))
            }
            "PipelineProfile" -> {
                val id = json.substringAfter("\"id\":\"").substringBefore('"')
                GraphNode.PipelineProfile(ResourceRef.parse(id))
            }
            "ConfigSource" -> {
                val path = json.substringAfter("\"path\":\"").substringBefore('"')
                val contentHash = json.substringAfter("\"contentHash\":\"").substringBefore('"')
                GraphNode.ConfigSource(Path.of(path), contentHash)
            }
            "ResolvedPipelinePlan" -> {
                val projectId = json.substringAfter("\"projectId\":\"").substringBefore('"')
                val planDigest = json.substringAfter("\"planDigest\":\"").substringBefore('"')
                GraphNode.ResolvedPipelinePlan(ResourceRef.parse(projectId), planDigest)
            }
            else -> throw IllegalArgumentException("Unknown node kind: $kind")
        }
    }

    private fun decodeEdge(json: String): Edge {
        val sourceJson = json.substringAfter("\"source\":").substringBefore("\"target\":")
        val targetJson = json.substringAfter("\"target\":").substringBefore("\"kind\":")
        val kindStr = json.substringAfter("\"kind\":\"").substringBefore('"')
        val kind = EdgeKind.valueOf(kindStr)
        return Edge(
            source = decodeNode(sourceJson.trim()),
            target = decodeNode(targetJson.trim()),
            kind = kind
        )
    }
}
