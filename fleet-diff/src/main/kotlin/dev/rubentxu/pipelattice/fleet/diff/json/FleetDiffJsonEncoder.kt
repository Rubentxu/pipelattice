package dev.rubentxu.pipelattice.fleet.diff.json

import dev.rubentxu.pipelattice.fleet.diff.domain.FleetDiffChange
import dev.rubentxu.pipelattice.fleet.diff.domain.FleetDiffReport
import dev.rubentxu.pipelattice.fleet.diff.domain.PlanReference
import dev.rubentxu.pipelattice.fleet.diff.domain.PolicyViolation
import dev.rubentxu.pipelattice.graph.domain.EdgeKind
import dev.rubentxu.pipelattice.graph.domain.GraphNode
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Hand-written JSON encoder for [FleetDiffReport].
 *
 * Produces "fleet-diff/v1" schema JSON without kotlinx-serialization.
 * Provides ~30 LOC overhead vs kotlinx, but avoids the dependency.
 */
public object FleetDiffJsonEncoder {

    private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT

    /**
     * Encodes [report] to a JSON string.
     */
    public fun encode(report: FleetDiffReport): String {
        return buildString {
            append("{")
        append("\"schema\":\"${report.schema}\",")
        append("\"generatedAt\":\"${ISO_FORMATTER.format(report.generatedAt)}\",")
        append("\"affectedProjects\":${encodeNodes(report.affectedProjects)},")
        append("\"effectiveChanges\":${encodeChanges(report.effectiveChanges)},")
        append("\"invalidPlans\":${encodeInvalidPlans(report.invalidPlans)},")
        append("\"newPolicyViolations\":${encodeViolations(report.newPolicyViolations)},")
        append("\"resolvedPolicyViolations\":${encodeViolations(report.resolvedPolicyViolations)},")
        append("\"providerChanges\":${encodeChanges(report.providerChanges)},")
        append("\"localOverrides\":${encodeChanges(report.localOverrides)}")
        append("}")
        }
    }

    private fun encodeNodes(nodes: Set<GraphNode>): String {
        if (nodes.isEmpty()) return "[]"
        return buildString {
            append("[")
            nodes.mapIndexed { index, node ->
                if (index > 0) append(",")
                append(encodeNode(node))
            }
            append("]")
        }
    }

    private fun encodeNode(node: GraphNode): String {
        return when (node) {
            is GraphNode.Project -> "{\"type\":\"Project\",\"id\":\"${node.id.canonicalForm}\"}"
            is GraphNode.Component -> "{\"type\":\"Component\",\"id\":\"${node.id.canonicalForm}\",\"owner\":\"${node.owner.canonicalForm}\"}"
            is GraphNode.PipelineProfile -> "{\"type\":\"PipelineProfile\",\"id\":\"${node.id.canonicalForm}\"}"
            is GraphNode.ConfigSource -> "{\"type\":\"ConfigSource\",\"path\":\"${node.path}\",\"contentHash\":\"${node.contentHash}\"}"
            is GraphNode.ResolvedPipelinePlan -> "{\"type\":\"ResolvedPipelinePlan\",\"projectId\":\"${node.projectId.canonicalForm}\",\"planDigest\":\"${node.planDigest}\"}"
        }
    }

    private fun encodeChanges(changes: List<FleetDiffChange>): String {
        if (changes.isEmpty()) return "[]"
        return buildString {
            append("[")
            changes.mapIndexed { index, change ->
                if (index > 0) append(",")
                append(encodeChange(change))
            }
            append("]")
        }
    }

    private fun encodeChange(change: FleetDiffChange): String {
        return when (change) {
            is FleetDiffChange.Added -> "{\"type\":\"Added\",\"source\":${encodeNode(change.source)},\"target\":${encodeNode(change.target)},\"kind\":\"${edgeKindName(change.kind)}\"}"
            is FleetDiffChange.Removed -> "{\"type\":\"Removed\",\"source\":${encodeNode(change.source)},\"target\":${encodeNode(change.target)},\"kind\":\"${edgeKindName(change.kind)}\"}"
            is FleetDiffChange.Modified -> "{\"type\":\"Modified\",\"source\":${encodeNode(change.source)},\"target\":${encodeNode(change.target)},\"kind\":\"${edgeKindName(change.kind)}\"}"
        }
    }

    private fun edgeKindName(kind: EdgeKind): String {
        return when (kind) {
            EdgeKind.IMPORTS -> "IMPORTS"
            EdgeKind.EXTENDS -> "EXTENDS"
            EdgeKind.SELECTS -> "SELECTS"
            EdgeKind.OVERRIDES -> "OVERRIDES"
            EdgeKind.PATCHES -> "PATCHES"
            EdgeKind.DERIVED_FROM -> "DERIVED_FROM"
            EdgeKind.USES -> "USES"
            EdgeKind.REQUIRES -> "REQUIRES"
            EdgeKind.PROVIDES -> "PROVIDES"
            EdgeKind.GOVERNED_BY -> "GOVERNED_BY"
            EdgeKind.TARGETS -> "TARGETS"
            EdgeKind.PRODUCES -> "PRODUCES"
            EdgeKind.CONSUMES -> "CONSUMES"
            EdgeKind.COMPILES_TO -> "COMPILES_TO"
        }
    }

    private fun encodeInvalidPlans(plans: Set<PlanReference>): String {
        if (plans.isEmpty()) return "[]"
        return buildString {
            append("[")
            plans.mapIndexed { index, plan ->
                if (index > 0) append(",")
                append("{\"projectId\":\"${plan.projectId.canonicalForm}\",\"planDigest\":\"${plan.planDigest}\"")
                if (plan.diagnosticCode != null) {
                    append(",\"diagnosticCode\":\"${plan.diagnosticCode}\"")
                }
                append("}")
            }
            append("]")
        }
    }

    private fun encodeViolations(violations: List<PolicyViolation>): String {
        if (violations.isEmpty()) return "[]"
        return buildString {
            append("[")
            violations.mapIndexed { index, v ->
                if (index > 0) append(",")
                append("{\"projectId\":\"${v.projectId.canonicalForm}\",\"rule\":\"${escapeJson(v.rule)}\",\"message\":\"${escapeJson(v.message)}\"}")
            }
            append("]")
        }
    }

    private fun escapeJson(s: String): String {
        return s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
