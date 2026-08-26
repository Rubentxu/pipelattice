package dev.rubentxu.pipelattice.graph.domain

/**
 * Typed edges in the reactive configuration graph.
 *
 * Mirrors the 14 edge kinds defined in spec 04 §4. A-min covers 5 core variants
 * (the V1 invalidation set per spec §6: imports, refs/extends, selectors, overrides, patches);
 * the remaining 9 (DERIVED_FROM, USES, REQUIRES, PROVIDES, GOVERNED_BY, TARGETS,
 * PRODUCES, CONSUMES, COMPILES_TO) are deferred to A-lite.
 *
 * Strings only in serialization (spec §4). Use [data object] for type discrimination
 * and exhaustive `when` branches.
 */
public sealed interface EdgeKind {

    public data object IMPORTS : EdgeKind

    public data object EXTENDS : EdgeKind

    public data object SELECTS : EdgeKind

    public data object OVERRIDES : EdgeKind

    public data object PATCHES : EdgeKind

    // A-lite additions (spec 04 §4)
    public data object DERIVED_FROM : EdgeKind

    public data object USES : EdgeKind

    public data object REQUIRES : EdgeKind

    public data object PROVIDES : EdgeKind

    public data object GOVERNED_BY : EdgeKind

    public data object TARGETS : EdgeKind

    public data object PRODUCES : EdgeKind

    public data object CONSUMES : EdgeKind

    public data object COMPILES_TO : EdgeKind

    public companion object {
        public fun all(): Set<EdgeKind> = setOf(
            IMPORTS,
            EXTENDS,
            SELECTS,
            OVERRIDES,
            PATCHES,
            DERIVED_FROM,
            USES,
            REQUIRES,
            PROVIDES,
            GOVERNED_BY,
            TARGETS,
            PRODUCES,
            CONSUMES,
            COMPILES_TO,
        )
    }
}
