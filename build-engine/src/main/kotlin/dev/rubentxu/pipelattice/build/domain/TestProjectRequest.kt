package dev.rubentxu.pipelattice.build.domain

import java.nio.file.Path

/**
 * Request to run the test suite for a project.
 *
 * A-min is an empty placeholder; real fields (test filter, parallelism) arrive
 * in A-lite with the first provider implementation.
 *
 * @property testFilter Optional glob or regex to select a subset of tests.
 */
public data class TestProjectRequest(
    public val workingDirectory: Path,
    public val environment: Map<EnvironmentKey, String>,
    public val testFilter: String? = null,
)
