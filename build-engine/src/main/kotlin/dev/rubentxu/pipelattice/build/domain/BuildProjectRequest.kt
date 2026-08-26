package dev.rubentxu.pipelattice.build.domain

import java.nio.file.Path

/**
 * Request to build a project and produce one or more artifacts.
 *
 * A-min is an empty placeholder; real fields (goals, parallelization) arrive
 * in A-lite with the first provider implementation.
 *
 * @property goals The build goals to execute (e.g., `clean`, `verify`, `publish`).
 */
public data class BuildProjectRequest(
    public val workingDirectory: Path,
    public val environment: Map<EnvironmentKey, String>,
    public val goals: List<Argument>,
)
