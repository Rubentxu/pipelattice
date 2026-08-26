package dev.rubentxu.pipelattice.provider.gradle.fake

import dev.rubentxu.pipelattice.build.domain.Argument
import dev.rubentxu.pipelattice.build.domain.BuildProjectRequest
import dev.rubentxu.pipelattice.build.domain.Command
import dev.rubentxu.pipelattice.build.domain.Executable
import dev.rubentxu.pipelattice.build.domain.InspectProjectRequest
import dev.rubentxu.pipelattice.build.domain.TestProjectRequest
import dev.rubentxu.pipelattice.build.ports.ProcessRunner
import dev.rubentxu.pipelattice.build.ports.ProjectBuilder
import dev.rubentxu.pipelattice.build.ports.ProjectInspector
import dev.rubentxu.pipelattice.build.ports.ProjectTester
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.provider.gradle.domain.GradleBuildArtifact
import dev.rubentxu.pipelattice.provider.gradle.domain.GradleProjectModel

/**
 * Fake Gradle provider that implements all three [:build-engine] ports.
 *
 * This fixture proves the [:build-engine] abstraction holds for a second provider
 * (Gradle) without modifying the build-engine itself. All execution is routed
 * through the injected [ProcessRunner] — no direct subprocess API is used.
 *
 * ## Usage
 * ```kotlin
 * val runner = FakeProcessRunner()
 * runner.enqueue(CommandResult.Success("version: 8.5", ""))
 * val provider = FakeGradleProvider(runner)
 * val result = provider.inspect(InspectProjectRequest(Path.of("/repo"), emptyMap()))
 * ```
 *
 * @param processRunner The injected process-runner port (FakeProcessRunner in tests).
 */
public class FakeGradleProvider(
    private val processRunner: ProcessRunner,
) : ProjectInspector<GradleProjectModel>,
    ProjectTester,
    ProjectBuilder<GradleBuildArtifact> {

    override suspend fun inspect(request: InspectProjectRequest): Outcome<GradleProjectModel, dev.rubentxu.pipelattice.build.ports.InspectFailure> {
        val command = Command(
            executable = Executable("gradle"),
            arguments = listOf(Argument("properties")),
            workingDirectory = request.workingDirectory,
            environment = request.environment,
        )
        return when (val result = processRunner.run(command)) {
            is dev.rubentxu.pipelattice.build.domain.CommandResult.Success -> {
                Outcome.Success(
                    GradleProjectModel(
                        gradleVersion = "8.5",
                        rootDir = request.workingDirectory,
                    ),
                )
            }
            is dev.rubentxu.pipelattice.build.domain.CommandResult.Failed -> {
                Outcome.Failure(dev.rubentxu.pipelattice.build.ports.InspectFailure.UnknownProject(result.stderr))
            }
        }
    }

    override suspend fun test(request: TestProjectRequest): Outcome<dev.rubentxu.pipelattice.build.ports.TestReport, dev.rubentxu.pipelattice.build.ports.TestFailure> {
        val command = Command(
            executable = Executable("gradle"),
            arguments = listOf(Argument("test")),
            workingDirectory = request.workingDirectory,
            environment = request.environment,
        )
        return when (val result = processRunner.run(command)) {
            is dev.rubentxu.pipelattice.build.domain.CommandResult.Success -> {
                Outcome.Success(Unit)
            }
            is dev.rubentxu.pipelattice.build.domain.CommandResult.Failed -> {
                Outcome.Failure(dev.rubentxu.pipelattice.build.ports.TestFailure.TestRunFailed(result.stderr))
            }
        }
    }

    override suspend fun build(request: BuildProjectRequest): Outcome<GradleBuildArtifact, dev.rubentxu.pipelattice.build.ports.BuildFailure> {
        val command = Command(
            executable = Executable("gradle"),
            arguments = listOf(Argument("assemble")) + request.goals,
            workingDirectory = request.workingDirectory,
            environment = request.environment,
        )
        return when (val result = processRunner.run(command)) {
            is dev.rubentxu.pipelattice.build.domain.CommandResult.Success -> {
                Outcome.Success(
                    GradleBuildArtifact(
                        group = "dev.rubentxu.pipelattice",
                        name = "fake-gradle-output",
                        version = "0.0.0-fake",
                    ),
                )
            }
            is dev.rubentxu.pipelattice.build.domain.CommandResult.Failed -> {
                Outcome.Failure(dev.rubentxu.pipelattice.build.ports.BuildFailure.BuildFailed(result.stderr))
            }
        }
    }
}
