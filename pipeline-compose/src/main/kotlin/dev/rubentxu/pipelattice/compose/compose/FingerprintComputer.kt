package dev.rubentxu.pipelattice.compose.compose

import dev.rubentxu.pipelattice.compose.domain.Provenance
import dev.rubentxu.pipelattice.resource.ParameterValue
import java.security.MessageDigest
import java.util.LinkedHashMap

/**
 * Internal object for computing stable fingerprints of resolved parameters.
 *
 * Uses SHA-256 to produce a deterministic 64-character hex string that changes
 * when any parameter value or provenance changes.
 *
 * FARCH-011: Uses java.security.MessageDigest (JVM standard) for SHA-256,
 * not Jackson or other serialization libraries.
 */
internal object FingerprintComputer {

    /**
     * Computes a SHA-256 fingerprint of the given parameters and provenance.
     *
     * The fingerprint is computed from a canonical JSON representation that:
     * - Uses LinkedHashMap to preserve key iteration order
     * - Sorts keys at each level for deterministic output
     * - Includes provenance location to detect upstream changes
     *
     * @param parameters The resolved parameter map.
     * @param provenance The provenance map for change detection.
     * @return Lowercase 64-character hex SHA-256 hash.
     */
    fun compute(
        parameters: Map<String, ParameterValue>,
        provenance: Map<String, List<Provenance>>,
    ): String {
        val canonicalJson = buildCanonicalJson(parameters, provenance)
        return sha256Hex(canonicalJson)
    }

    /**
     * Builds a canonical JSON string from parameters and provenance.
     *
     * The canonical form:
     * - Objects are represented as LinkedHashMap with sorted keys
     * - Arrays are represented as JSON arrays
     * - Strings are quoted, numbers are not
     * - Keys are sorted lexicographically at each level
     */
    private fun buildCanonicalJson(
        parameters: Map<String, ParameterValue>,
        provenance: Map<String, List<Provenance>>,
    ): String {
        val root = LinkedHashMap<String, Any?>()

        // Parameters section with sorted keys
        val paramsMap = LinkedHashMap<String, Any?>()
        for ((key, value) in parameters.toSortedMap()) {
            paramsMap[key] = parameterValueToCanonical(value)
        }
        root["parameters"] = paramsMap

        // Provenance section with sorted keys
        val provMap = LinkedHashMap<String, Any?>()
        for ((key, provList) in provenance.toSortedMap()) {
            val provArray = provList.map { prov ->
                val provObj = LinkedHashMap<String, Any?>()
                provObj["key"] = prov.key
                provObj["layer"] = prov.layer.name
                provObj["location"] = prov.source.location.path
                provObj["effectiveValue"] = prov.effectiveValue?.let { parameterValueToCanonical(it) }
                provObj
            }
            provMap[key] = provArray
        }
        root["provenance"] = provMap

        return renderValue(root)
    }

    /**
     * Converts a ParameterValue to its canonical representation.
     */
    private fun parameterValueToCanonical(value: ParameterValue): Any = when (value) {
        is ParameterValue.IntValue -> mapOf("type" to "integer", "value" to value.value)
        is ParameterValue.BoolValue -> mapOf("type" to "boolean", "value" to value.value)
        is ParameterValue.StringValue -> mapOf("type" to "string", "value" to value.value)
    }

    /**
     * Renders a value as a canonical JSON string.
     */
    private fun renderValue(value: Any?): String = when (value) {
        null -> "null"
        is Map<*, *> -> {
            val entries = value.entries.map { (k, v) ->
                "\"$k\":${renderValue(v)}"
            }
            "{${entries.joinToString(",")}}"
        }
        is List<*> -> {
            val items = value.map { renderValue(it) }
            "[${items.joinToString(",")}]"
        }
        is String -> "\"$value\""
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> "\"$value\""
    }

    /**
     * Computes SHA-256 hash and returns as lowercase 64-character hex string.
     */
    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
