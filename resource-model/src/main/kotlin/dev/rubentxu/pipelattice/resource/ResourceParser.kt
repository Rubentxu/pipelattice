package dev.rubentxu.pipelattice.resource

/**
 * Outbound port of the parse phase (pipelattice-spec/docs/01_ARCHITECTURE.md §5).
 *
 * Implementations are adapters (e.g. YAML); the resource model itself never knows the wire
 * format (FARCH-010).
 */
public fun interface ResourceParser {
    public fun parse(document: SourceDocument): ParseResult
}
