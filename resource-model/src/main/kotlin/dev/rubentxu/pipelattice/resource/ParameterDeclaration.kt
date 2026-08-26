package dev.rubentxu.pipelattice.resource

public enum class ParameterType(public val wireName: String) {
    INTEGER("integer"),
    BOOLEAN("boolean"),
    STRING("string"),
    ;

    public companion object {
        private val byWireName: Map<String, ParameterType> =
            entries.associateBy(ParameterType::wireName)

        public fun fromWire(name: String): ParameterType? = byWireName[name]
    }
}

/** Declaration of a typed parameter inside a profile. */
public data class ParameterDeclaration(
    public val type: ParameterType,
    public val default: ParameterValue? = null,
    public val governance: Governance = Governance(),
) {
    init {
        val matchesType = when (type) {
            ParameterType.INTEGER -> default == null || default is ParameterValue.IntValue
            ParameterType.BOOLEAN -> default == null || default is ParameterValue.BoolValue
            ParameterType.STRING -> default == null || default is ParameterValue.StringValue
        }
        require(matchesType) {
            "parameter default $default does not match declared type '${type.wireName}'"
        }
    }
}
