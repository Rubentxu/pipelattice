package dev.rubentxu.pipelattice.provider.gradle.fake

import dev.rubentxu.pipelattice.build.domain.Argument
import dev.rubentxu.pipelattice.build.domain.BuildProjectRequest
import dev.rubentxu.pipelattice.build.domain.CommandResult
import dev.rubentxu.pipelattice.build.domain.EnvironmentKey
import dev.rubentxu.pipelattice.build.domain.Executable
import dev.rubentxu.pipelattice.build.domain.InspectProjectRequest
import dev.rubentxu.pipelattice.build.domain.TestProjectRequest
import dev.rubentxu.pipelattice.build.fake.FakeProcessRunner
import dev.rubentxu.pipelattice.build.ports.BuildFailure
import dev.rubentxu.pipelattice.build.ports.ProjectBuilder
import dev.rubentxu.pipelattice.build.ports.ProjectInspector
import dev.rubentxu.pipelattice.build.ports.ProjectTester
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FakeGradleProviderTest {

    @Test
    fun `inspect routes gradle properties through ProcessRunner`() = runBlocking {
        val runner = FakeProcessRunner()
        runner.enqueue(CommandResult.Success("version: 8.5", ""))
        val provider = FakeGradleProvider(runner)

        val result = provider.inspect(
            InspectProjectRequest(
                workingDirectory = Path.of("/repo"),
                environment = emptyMap(),
            ),
        )

        assertIs<Outcome.Success<*>>(result)
        val invocations = runner.invocations()
        assertEquals(1, invocations.size)
        assertEquals(Executable("gradle"), invocations[0].executable)
        assertEquals(listOf(Argument("properties")), invocations[0].arguments)
    }

    @Test
    fun `test invokes gradle command via ProcessRunner`() = runBlocking {
        val runner = FakeProcessRunner()
        runner.enqueue(CommandResult.Success("BUILD SUCCESSFUL", ""))
        val provider = FakeGradleProvider(runner)

        val result = provider.test(
            TestProjectRequest(
                workingDirectory = Path.of("/repo"),
                environment = emptyMap(),
            ),
        )

        assertIs<Outcome.Success<*>>(result)
        val invocations = runner.invocations()
        assertEquals(1, invocations.size)
        assertEquals(Executable("gradle"), invocations[0].executable)
        assertEquals(listOf(Argument("test")), invocations[0].arguments)
    }

    @Test
    fun `build produces GradleBuildArtifact via ProcessRunner`() = runBlocking {
        val runner = FakeProcessRunner()
        runner.enqueue(CommandResult.Success("BUILD SUCCESSFUL", ""))
        val provider = FakeGradleProvider(runner)

        val result = provider.build(
            BuildProjectRequest(
                workingDirectory = Path.of("/repo"),
                environment = emptyMap(),
                goals = emptyList(),
            ),
        )

        assertIs<Outcome.Success<*>>(result)
        val artifact = result.value
        assertTrue(artifact is dev.rubentxu.pipelattice.provider.gradle.domain.GradleBuildArtifact)
        assertEquals("dev.rubentxu.pipelattice", artifact.group)
        assertEquals("fake-gradle-output", artifact.name)
        assertEquals("0.0.0-fake", artifact.version)

        val invocations = runner.invocations()
        assertEquals(1, invocations.size)
        assertEquals(Executable("gradle"), invocations[0].executable)
        assertEquals(listOf(Argument("assemble")), invocations[0].arguments)
    }
}
