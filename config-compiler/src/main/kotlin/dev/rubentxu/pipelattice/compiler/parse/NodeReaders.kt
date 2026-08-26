package dev.rubentxu.pipelattice.compiler.parse

import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticCode
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.NodeTuple
import org.snakeyaml.engine.v2.nodes.ScalarNode

/**
 * Stable error codes emitted by the YAML source adapter. They are part of the public CLI
 * contract and must never be renamed or reused (pipelattice-spec/docs/12 §7).
 */
internal object ParseErrorCodes {
    val SYNTAX = DiagnosticCode("RESOURCE-YAML-001")
    val API_VERSION = DiagnosticCode("RESOURCE-APIVERSION-001")
    val KIND = DiagnosticCode("RESOURCE-KIND-001")
    val UNKNOWN_FIELD = DiagnosticCode("RESOURCE-FIELD-001")
    val MISSING = DiagnosticCode("RESOURCE-SCHEMA-001")
    val TYPE = DiagnosticCode("RESOURCE-SCHEMA-002")
    val INVALID_REF = DiagnosticCode("REF-INVALID-001")
}

/** Collects diagnostics while binding one document; keeps node→value rules in one place. */
internal class ReaderContext(private val path: String) {

    val diagnostics = mutableListOf<Diagnostic>()

    fun hasErrors(): Boolean = diagnostics.any { it.severity == DiagnosticSeverity.ERROR }

    fun error(code: DiagnosticCode, node: Node?, message: String, hint: String? = null) {
        diagnostics += Diagnostic(
            code = code,
            severity = DiagnosticSeverity.ERROR,
            message = message,
            location = startLocation(node),
            remediationHint = hint,
        )
    }

    fun startLocation(node: Node?): SourceLocation? =
        node?.startMark
            ?.map { SourceLocation(path = path, line = it.line + 1, column = it.column + 1) }
            ?.orElse(null)

    fun keyText(tuple: NodeTuple): String =
        (tuple.keyNode as? ScalarNode)?.value ?: "<non-scalar key>"

    fun expectMapping(what: String, node: Node): MappingNode? =
        node as? MappingNode ?: run {
            error(ParseErrorCodes.TYPE, node, "$what must be a mapping")
            null
        }

    fun scalarText(what: String, node: Node): String? =
        (node as? ScalarNode)?.value ?: run {
            error(ParseErrorCodes.TYPE, node, "$what must be a scalar value")
            null
        }

    fun find(entries: List<NodeTuple>, key: String): NodeTuple? =
        entries.firstOrNull { keyText(it) == key }

    fun checkUnknownFields(what: String, entries: List<NodeTuple>, allowed: Set<String>) {
        for (tuple in entries) {
            val key = keyText(tuple)
            if (key !in allowed) {
                error(
                    ParseErrorCodes.UNKNOWN_FIELD,
                    tuple.keyNode,
                    "unknown property '$key' in $what",
                    hint = "allowed properties: ${allowed.sorted().joinToString(", ")}",
                )
            }
        }
    }
}
