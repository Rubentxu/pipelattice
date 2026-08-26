package dev.rubentxu.pipelattice.build.ports

import dev.rubentxu.pipelattice.build.domain.BuildArtifact
import dev.rubentxu.pipelattice.build.domain.BuildProjectRequest
import dev.rubentxu.pipelattice.foundation.outcome.Outcome

/**
 * Failure reasons for project build.
 *
 * A-min is a placeholder sealed interface. Real variants (compilation-error,
 * dependency-resolution-failure, timeout) arrive in A-lite.
 */
public sealed interface BuildFailure {
    public data class BuildFailed(public val reason: String) : BuildFailure
}

/**
 * Port for building a project and producing artifacts.
 *
 * Implementations (e.g., MavenBuilder, GradleBuilder) translate the native
 * build invocation into a [BuildArtifact] and surface errors via [BuildFailure].
 *
 * @param A The concrete artifact type produced by this builder.
 */
public interface ProjectBuilder<A : BuildArtifact> {
    /**
     * Builds the project described by [request] and returns the produced artifact.
     *
     * @param request The build request containing project location, environment,
     *               and build goals.
     * @return [Outcome.Success] containing the produced artifact, or [Outcome.Failure]
     *         with a [BuildFailure] reason.
     */
    public suspend fun build(request: BuildProjectRequest): Outcome<A, BuildFailure>
}
