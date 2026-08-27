package dev.rubentxu.pipelattice.fleet.diff.repository

import dev.rubentxu.pipelattice.resource.ParseResult
import dev.rubentxu.pipelattice.resource.ParsedResource
import dev.rubentxu.pipelattice.resource.ResourceParser
import dev.rubentxu.pipelattice.resource.SourceDocument

/**
 * A [ResourceParser] suitable for testing that tracks parse invocation counts
 * and returns empty successful results.
 *
 * @param parseCount Start at 0; increments by 1 each time [parse] is called.
 */
public class FakeResourceParser(
    public var parseCount: Int = 0,
) : ResourceParser {

    override fun parse(document: SourceDocument): ParseResult {
        parseCount++
        return ParseResult(
            resources = emptyList(),
            diagnostics = emptyList(),
        )
    }
}
