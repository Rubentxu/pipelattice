package dev.rubentxu.pipelattice.build.domain

import java.nio.file.Path

/**
 * Request to inspect a project's model (e.g., list of modules, dependencies, properties).
 *
 * A-min is an empty placeholder; real fields (project path, requested model type)
 * arrive in A-lite with the first provider implementation.
 */
public data class InspectProjectRequest(
    public val workingDirectory: Path,
    public val environment: Map<EnvironmentKey, String>,
)
