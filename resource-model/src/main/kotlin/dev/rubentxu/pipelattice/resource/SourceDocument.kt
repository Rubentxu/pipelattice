package dev.rubentxu.pipelattice.resource

/** Raw text of one configuration document plus its logical path (used in diagnostics). */
public data class SourceDocument(
    public val path: String,
    public val content: String,
) {
    init {
        require(path.isNotBlank()) { "SourceDocument.path must not be blank" }
    }
}
