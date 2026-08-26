package dev.rubentxu.pipelattice.resource

/** Identity block shared by every resource envelope. */
public data class Metadata(
    public val name: String,
    public val version: String? = null,
    public val labels: Map<String, String> = emptyMap(),
) {
    init {
        require(name.isNotBlank()) { "metadata.name must not be blank" }
    }
}
