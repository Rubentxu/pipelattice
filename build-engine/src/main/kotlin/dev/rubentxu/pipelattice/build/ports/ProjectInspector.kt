package dev.rubentxu.pipelattice.build.ports

import dev.rubentxu.pipelattice.build.domain.InspectProjectRequest
import dev.rubentxu.pipelattice.build.domain.ProjectModel
import dev.rubentxu.pipelattice.foundation.outcome.Outcome

/**
 * Failure reasons for project inspection.
 *
 * A-min uses a simple `UnknownProject` variant. A richer diagnostic hierarchy
 * (file-not-found, parse-error, unsupported-model) arrives in A-lite.
 */
public sealed interface InspectFailure {
    public data class UnknownProject(public val reason: String) : InspectFailure
}

/**
 * Port for inspecting a project's model and metadata.
 *
 * Implementations (e.g., MavenInspector, GradleInspector) translate the tool's
 * native project model into the Pipelattice [ProjectModel] type.
 *
 * @param P The concrete project-model type produced by this inspector.
 */
public interface ProjectInspector<P : ProjectModel> {
    /**
     * Inspects the project at the given [request] working directory.
     *
     * @param request The inspection request containing project location and environment.
     * @return [Outcome.Success] containing the project model, or [Outcome.Failure]
     *         with an [InspectFailure] reason.
     */
    public suspend fun inspect(request: InspectProjectRequest): Outcome<P, InspectFailure>
}
