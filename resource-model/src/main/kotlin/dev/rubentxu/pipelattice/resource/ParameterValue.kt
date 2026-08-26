package dev.rubentxu.pipelattice.resource

/**
 * Closed set of parameter values accepted by the source schema.
 *
 * A closed hierarchy (instead of `Any?` or raw strings) keeps FARCH-008 true from day one:
 * untyped values never enter the model. YAML scalar tags decide the variant at parse time.
 */
public sealed interface ParameterValue {
    public data class IntValue(public val value: Long) : ParameterValue

    public data class BoolValue(public val value: Boolean) : ParameterValue

    public data class StringValue(public val value: String) : ParameterValue
}
