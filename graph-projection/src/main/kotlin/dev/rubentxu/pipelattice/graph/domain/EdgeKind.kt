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
}
