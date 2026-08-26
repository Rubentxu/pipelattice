package dev.rubentxu.pipelattice.build.domain

/**
 * Placeholder typealias for a project model.
 *
 * A-min uses `Any` as a safe placeholder. The sealed hierarchy of project models
 * (Maven, Gradle, etc.) arrives in A-lite alongside the first real provider.
 *
 * @see ProjectInspector
 */
public typealias ProjectModel = Any
