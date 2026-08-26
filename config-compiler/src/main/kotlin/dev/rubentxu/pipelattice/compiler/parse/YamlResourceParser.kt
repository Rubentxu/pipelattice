package dev.rubentxu.pipelattice.compiler.parse

import dev.rubentxu.pipelattice.foundation.ResourceRef
import dev.rubentxu.pipelattice.foundation.diagnostics.Diagnostic
import dev.rubentxu.pipelattice.foundation.diagnostics.DiagnosticSeverity
import dev.rubentxu.pipelattice.foundation.diagnostics.SourceLocation
import dev.rubentxu.pipelattice.resource.ApiVersion
import dev.rubentxu.pipelattice.resource.Constraints
import dev.rubentxu.pipelattice.resource.Governance
import dev.rubentxu.pipelattice.resource.GovernanceMode
import dev.rubentxu.pipelattice.resource.Metadata
import dev.rubentxu.pipelattice.resource.ParameterDeclaration
import dev.rubentxu.pipelattice.resource.ParameterType
import dev.rubentxu.pipelattice.resource.ParameterValue
import dev.rubentxu.pipelattice.resource.ParseResult
import dev.rubentxu.pipelattice.resource.ParsedResource
import dev.rubentxu.pipelattice.resource.PipelineDefinitionResource
import dev.rubentxu.pipelattice.resource.PipelineDefinitionSpec
import dev.rubentxu.pipelattice.resource.PipelineProfileResource
import dev.rubentxu.pipelattice.resource.PipelineProfileSpec
import dev.rubentxu.pipelattice.resource.ResourceKind
import dev.rubentxu.pipelattice.resource.ResourceParser
import dev.rubentxu.pipelattice.resource.SourceDocument
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.NodeTuple
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode
import org.snakeyaml.engine.v2.nodes.Tag

/**
 * YAML adapter of the [ResourceParser] port (S-002 decision, ADR-0021).
 *
 * One document per file; multi-document streams arrive with the catalog loader (M2).
 * When any ERROR diagnostic is produced the [ParseResult] carries no resources.
 */
public class YamlResourceParser : ResourceParser {

    override fun parse(document: SourceDocument): ParseResult {
        val ctx = ReaderContext(document.path)

        val root = readRootNode(ctx, document) ?: return ParseResult.failed(ctx.diagnostics)
        val rootEntries = ctx.expectMapping("document", root)?.value
            ?: return ParseResult.failed(ctx.diagnostics)

        ctx.checkUnknownFields("document", rootEntries, ENVELOPE_FIELDS)

        val apiVersion = bindApiVersion(ctx, rootEntries)
        val kind = bindKind(ctx, rootEntries)
        val metadata = bindMetadata(ctx, rootEntries)

        if (apiVersion == null || kind == null || metadata == null || ctx.hasErrors()) {
            return ParseResult.failed(ctx.diagnostics)
        }

        val specEntries = bindSpecEntries(ctx, rootEntries) ?: return ParseResult.failed(ctx.diagnostics)

        val resource: ParsedResource? = when (kind) {
            ResourceKind.PIPELINE_DEFINITION ->
                bindPipelineDefinition(ctx, specEntries)?.let {
                    PipelineDefinitionResource(apiVersion, metadata, it)
                }

            ResourceKind.PIPELINE_PROFILE ->
                bindPipelineProfile(ctx, specEntries)?.let {
                    PipelineProfileResource(apiVersion, metadata, it)
                }
        }

        return if (resource != null && !ctx.hasErrors()) {
            ParseResult(resources = listOf(resource), diagnostics = ctx.diagnostics)
        } else {
            ParseResult.failed(ctx.diagnostics)
        }
    }

    // --- document -----------------------------------------------------------

    private fun readRootNode(ctx: ReaderContext, document: SourceDocument): Node? =
        try {
            val loaded = Load(LoadSettings.builder().build()).loadFromString(document.content)
            loaded as? Node ?: run {
                ctx.error(ParseErrorCodes.MISSING, null, "document is empty")
                null
            }
        } catch (marked: MarkedYamlEngineException) {
            reportSyntax(ctx, document.path, marked)
            null
        } catch (e: RuntimeException) {
            reportSyntaxWithoutLocation(ctx, e)
            null
        }

    private fun reportSyntax(ctx: ReaderContext, path: String, e: MarkedYamlEngineException) {
        ctx.diagnostics += Diagnostic(
            code = ParseErrorCodes.SYNTAX,
            severity = DiagnosticSeverity.ERROR,
            message = "invalid YAML: ${e.message}",
            location = e.problemMark.orElse(null)?.let {
                SourceLocation(path = path, line = it.line + 1, column = it.column + 1)
            },
        )
    }

    private fun reportSyntaxWithoutLocation(ctx: ReaderContext, e: RuntimeException) {
        ctx.diagnostics += Diagnostic(
            code = ParseErrorCodes.SYNTAX,
            severity = DiagnosticSeverity.ERROR,
            message = "invalid YAML: ${e.message}",
            location = null,
        )
    }

    /** Missing `spec` is valid (empty); a non-mapping `spec` reports and aborts. */
    private fun bindSpecEntries(ctx: ReaderContext, entries: List<NodeTuple>): List<NodeTuple>? =
        ctx.find(entries, "spec")?.let { ctx.expectMapping("spec", it.valueNode)?.value } ?: emptyList()

    // --- envelope -----------------------------------------------------------

    private fun bindApiVersion(ctx: ReaderContext, entries: List<NodeTuple>): ApiVersion? {
        val tuple = ctx.find(entries, "apiVersion") ?: run {
            ctx.error(ParseErrorCodes.MISSING, null, "required property 'apiVersion' is missing")
            return null
        }
        val text = ctx.scalarText("apiVersion", tuple.valueNode) ?: return null
        val apiVersion = try {
            ApiVersion(text)
        } catch (e: IllegalArgumentException) {
            ctx.error(ParseErrorCodes.TYPE, tuple.valueNode, e.message!!)
            return null
        }
        if (!apiVersion.isKnown) {
            ctx.error(
                ParseErrorCodes.API_VERSION,
                tuple.valueNode,
                "unsupported apiVersion '${apiVersion.value}'",
                hint = "supported: ${ApiVersion.KNOWN.value}",
            )
            return null
        }
        return apiVersion
    }

    private fun bindKind(ctx: ReaderContext, entries: List<NodeTuple>): ResourceKind? {
        val tuple = ctx.find(entries, "kind") ?: run {
            ctx.error(ParseErrorCodes.MISSING, null, "required property 'kind' is missing")
            return null
        }
        val text = ctx.scalarText("kind", tuple.valueNode) ?: return null
        return ResourceKind.fromWire(text) ?: run {
            ctx.error(
                ParseErrorCodes.KIND,
                tuple.valueNode,
                "unknown kind '$text'",
                hint = "known kinds: ${knownKinds()}",
            )
            null
        }
    }

    private fun bindMetadata(ctx: ReaderContext, entries: List<NodeTuple>): Metadata? {
        val tuple = ctx.find(entries, "metadata") ?: run {
            ctx.error(ParseErrorCodes.MISSING, null, "required property 'metadata' is missing")
            return null
        }
        val map = ctx.expectMapping("metadata", tuple.valueNode)?.value ?: return null
        ctx.checkUnknownFields("metadata", map, METADATA_FIELDS)

        val nameTuple = ctx.find(map, "name") ?: run {
            ctx.error(ParseErrorCodes.MISSING, tuple.valueNode, "required property 'metadata.name' is missing")
            return null
        }
        val name = ctx.scalarText("metadata.name", nameTuple.valueNode) ?: return null

        val version = ctx.find(map, "version")?.let { ctx.scalarText("metadata.version", it.valueNode) }

        val labels = mutableMapOf<String, String>()
        ctx.find(map, "labels")?.let { labelsTuple ->
            val labelMap = ctx.expectMapping("metadata.labels", labelsTuple.valueNode)?.value ?: return null
            for (entry in labelMap) {
                val key = ctx.keyText(entry)
                val value = ctx.scalarText("metadata.labels['$key']", entry.valueNode) ?: return null
                labels[key] = value
            }
        }
        return try {
            Metadata(name = name, version = version, labels = labels)
        } catch (e: IllegalArgumentException) {
            ctx.error(ParseErrorCodes.TYPE, nameTuple.valueNode, e.message!!)
            null
        }
    }

    // --- specs --------------------------------------------------------------

    private fun bindPipelineDefinition(
        ctx: ReaderContext,
        entries: List<NodeTuple>,
    ): PipelineDefinitionSpec? {
        val specEntries = bindSpecEntries(ctx, entries) ?: return null
        ctx.checkUnknownFields("spec", specEntries, PIPELINE_DEFINITION_FIELDS)

        val profile = ctx.find(specEntries, "profile")?.let { profileTuple ->
            val profileMap = ctx.expectMapping("spec.profile", profileTuple.valueNode)?.value ?: return null
            ctx.checkUnknownFields("spec.profile", profileMap, REF_FIELDS)
            refFromMapping(ctx, profileMap, "spec.profile.ref")
        }

        val parameters = mutableMapOf<String, ParameterValue>()
        ctx.find(specEntries, "parameters")?.let { paramsTuple ->
            val paramsMap = ctx.expectMapping("spec.parameters", paramsTuple.valueNode)?.value ?: return null
            for (entry in paramsMap) {
                val name = ctx.keyText(entry)
                parameters[name] = parameterValue(ctx, entry.valueNode) ?: return null
            }
        }
        return PipelineDefinitionSpec(profile = profile, parameters = parameters)
    }

    private fun bindPipelineProfile(
        ctx: ReaderContext,
        entries: List<NodeTuple>,
    ): PipelineProfileSpec? {
        val specEntries = bindSpecEntries(ctx, entries) ?: return null
        ctx.checkUnknownFields("spec", specEntries, PIPELINE_PROFILE_FIELDS)

        val imports = mutableListOf<ResourceRef>()
        ctx.find(specEntries, "imports")?.let { importsTuple ->
            val sequence = importsTuple.valueNode as? SequenceNode ?: run {
                ctx.error(ParseErrorCodes.TYPE, importsTuple.valueNode, "spec.imports must be a list")
                return null
            }
            for (item in sequence.value) {
                val itemMap = ctx.expectMapping("spec.imports item", item)?.value ?: return null
                ctx.checkUnknownFields("spec.imports item", itemMap, REF_FIELDS)
                imports += refFromMapping(ctx, itemMap, "spec.imports[].ref") ?: return null
            }
        }

        val parameters = mutableMapOf<String, ParameterDeclaration>()
        ctx.find(specEntries, "parameters")?.let { paramsTuple ->
            val paramsMap = ctx.expectMapping("spec.parameters", paramsTuple.valueNode)?.value ?: return null
            for (entry in paramsMap) {
                val name = ctx.keyText(entry)
                parameters[name] = bindParameterDeclaration(ctx, name, entry.valueNode) ?: return null
            }
        }

        val workflowRef = ctx.find(specEntries, "workflow")?.let { workflowTuple ->
            val workflowMap = ctx.expectMapping("spec.workflow", workflowTuple.valueNode)?.value ?: return null
            ctx.checkUnknownFields("spec.workflow", workflowMap, REF_FIELDS)
            refFromMapping(ctx, workflowMap, "spec.workflow.ref")
        }

        return PipelineProfileSpec(imports = imports, parameters = parameters, workflowRef = workflowRef)
    }

    private fun bindParameterDeclaration(
        ctx: ReaderContext,
        name: String,
        node: Node,
    ): ParameterDeclaration? {
        val declaration = ctx.expectMapping("parameter '$name'", node)?.value ?: return null
        ctx.checkUnknownFields("parameter '$name'", declaration, PARAMETER_FIELDS)

        val typeTuple = ctx.find(declaration, "type") ?: run {
            ctx.error(ParseErrorCodes.MISSING, node, "parameter '$name' requires 'type'")
            return null
        }
        val typeText = ctx.scalarText("parameter type", typeTuple.valueNode) ?: return null
        val type = ParameterType.fromWire(typeText) ?: run {
            ctx.error(
                ParseErrorCodes.TYPE,
                typeTuple.valueNode,
                "unknown parameter type '$typeText' for parameter '$name'",
                hint = "known types: ${knownTypes()}",
            )
            return null
        }

        val default = ctx.find(declaration, "default")?.let { parameterValue(ctx, it.valueNode) }
        if (ctx.hasErrors()) return null

        val governance = ctx.find(declaration, "governance")?.let { bindGovernance(ctx, name, it.valueNode) }
        if (ctx.hasErrors()) return null

        return try {
            ParameterDeclaration(type = type, default = default, governance = governance ?: Governance())
        } catch (e: IllegalArgumentException) {
            ctx.error(ParseErrorCodes.TYPE, node, e.message!!)
            null
        }
    }

    private fun bindGovernance(ctx: ReaderContext, paramName: String, node: Node): Governance? {
        val gov = ctx.expectMapping("governance of '$paramName'", node)?.value ?: return null
        ctx.checkUnknownFields("governance of '$paramName'", gov, GOVERNANCE_FIELDS)

        val modeTuple = ctx.find(gov, "mode")
        val mode = modeTuple?.let {
            val text = ctx.scalarText("governance.mode", it.valueNode) ?: return null
            GovernanceMode.fromWire(text) ?: run {
                ctx.error(
                    ParseErrorCodes.TYPE,
                    it.valueNode,
                    "unknown governance mode '$text' for parameter '$paramName'",
                    hint = "known modes: ${knownModes()}",
                )
                return null
            }
        } ?: GovernanceMode.DEFAULT

        var constraints: Constraints? = null
        ctx.find(gov, "constraints")?.let { constraintsTuple ->
            val map = ctx.expectMapping("governance.constraints", constraintsTuple.valueNode)?.value ?: return null
            ctx.checkUnknownFields("governance.constraints", map, CONSTRAINT_FIELDS)

            // Absent field -> null (no error). Present but invalid -> diagnostic recorded,
            // then the ctx.hasErrors() gate aborts binding.
            fun optionalLong(name: String): Long? {
                val tuple = ctx.find(map, name) ?: return null
                val text = ctx.scalarText(name, tuple.valueNode) ?: return null
                return text.toLongOrNull() ?: run {
                    ctx.error(ParseErrorCodes.TYPE, tuple.valueNode, "'$name' must be an integer, got '$text'")
                    null
                }
            }

            val min = optionalLong("min")
            val max = optionalLong("max")
            if (ctx.hasErrors()) return null

            constraints = try {
                Constraints(min = min, max = max)
            } catch (e: IllegalArgumentException) {
                ctx.error(ParseErrorCodes.TYPE, constraintsTuple.valueNode, e.message!!)
                return null
            }
        }

        return try {
            Governance(mode = mode, constraints = constraints)
        } catch (e: IllegalArgumentException) {
            ctx.error(ParseErrorCodes.TYPE, node, e.message!!)
            null
        }
    }

    // --- scalar helpers -------------------------------------------------------

    private fun parameterValue(ctx: ReaderContext, node: Node): ParameterValue? {
        val scalar = node as? ScalarNode ?: run {
            ctx.error(ParseErrorCodes.TYPE, node, "parameter value must be a scalar")
            return null
        }
        return when (scalar.tag) {
            Tag.INT -> scalar.value.toLongOrNull()?.let(ParameterValue::IntValue)
                ?: run { ctx.error(ParseErrorCodes.TYPE, node, "'${scalar.value}' is not an integer"); null }

            Tag.BOOL -> scalar.value.toBooleanStrictOrNull()?.let(ParameterValue::BoolValue)
                ?: run { ctx.error(ParseErrorCodes.TYPE, node, "'${scalar.value}' is not a boolean"); null }

            Tag.STR -> ParameterValue.StringValue(scalar.value)

            Tag.NULL -> run {
                ctx.error(ParseErrorCodes.TYPE, node, "parameter value must not be null")
                null
            }

            else -> run {
                ctx.error(ParseErrorCodes.TYPE, node, "unsupported scalar tag '${scalar.tag}'")
                null
            }
        }
    }

    private fun refFromMapping(ctx: ReaderContext, mapping: List<NodeTuple>, path: String): ResourceRef? {
        val tuple = ctx.find(mapping, "ref") ?: run {
            ctx.error(ParseErrorCodes.MISSING, null, "$path requires 'ref'")
            return null
        }
        val text = ctx.scalarText(path, tuple.valueNode) ?: return null
        return try {
            ResourceRef.parse(text)
        } catch (e: IllegalArgumentException) {
            ctx.error(ParseErrorCodes.INVALID_REF, tuple.valueNode, "${e.message} (at $path)")
            null
        }
    }

    private fun knownKinds(): String = ResourceKind.entries.map { it.wireName }.sorted().joinToString(", ")

    private fun knownTypes(): String = ParameterType.entries.map { it.wireName }.sorted().joinToString(", ")

    private fun knownModes(): String = GovernanceMode.entries.map { it.wireName }.sorted().joinToString(", ")

    private companion object {
        val ENVELOPE_FIELDS = setOf("apiVersion", "kind", "metadata", "spec")
        val METADATA_FIELDS = setOf("name", "version", "labels")
        val PIPELINE_DEFINITION_FIELDS = setOf("profile", "parameters")
        val PIPELINE_PROFILE_FIELDS = setOf("imports", "parameters", "workflow")
        val PARAMETER_FIELDS = setOf("type", "default", "governance")
        val GOVERNANCE_FIELDS = setOf("mode", "constraints")
        val CONSTRAINT_FIELDS = setOf("min", "max")
        val REF_FIELDS = setOf("ref")
    }
}
