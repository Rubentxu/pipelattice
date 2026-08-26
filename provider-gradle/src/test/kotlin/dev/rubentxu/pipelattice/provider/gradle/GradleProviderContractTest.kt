package dev.rubentxu.pipelattice.provider.gradle

import dev.rubentxu.pipelattice.build.domain.Argument
import dev.rubentxu.pipelattice.build.domain.BuildProjectRequest
import dev.rubentxu.pipelattice.build.domain.CommandResult
import dev.rubentxu.pipelattice.build.domain.Executable
import dev.rubentxu.pipelattice.build.domain.InspectProjectRequest
import dev.rubentxu.pipelattice.build.domain.TestProjectRequest
import dev.rubentxu.pipelattice.build.fake.FakeProcessRunner
import dev.rubentxu.pipelattice.build.ports.ProjectBuilder
import dev.rubentxu.pipelattice.build.ports.ProjectInspector
import dev.rubentxu.pipelattice.build.ports.ProjectTester
import dev.rubentxu.pipelattice.provider.gradle.fake.FakeGradleProvider
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contract smoke test proving [:build-engine] abstraction holds for a second provider.
 *
 * This test verifies that:
 * 1. FakeGradleProvider satisfies all three port contracts at runtime.
 * 2. All operations route through the injected ProcessRunner (no ProcessBuilder bypass).
 * 3. gradle executable is used as the Command target.
 */
class GradleProviderContractTest {

    @Test
    fun `provider satisfies all three port contracts at runtime`() {
        val runner = FakeProcessRunner()
        val provider: Any = FakeGradleProvider(runner)

        val asInspector = provider as? ProjectInspector<*>
        val asTester = provider as? ProjectTester
        val asBuilder = provider as? ProjectBuilder<*>

        assertNotNull(asInspector, "Provider must implement ProjectInspector")
        assertNotNull(asTester, "Provider must implement ProjectTester")
        assertNotNull(asBuilder, "Provider must implement ProjectBuilder")
    }

    @Test
    fun `all three operations route through ProcessRunner with gradle executable`() = runBlocking {
        val runner = FakeProcessRunner()
        runner.enqueue(CommandResult.Success("OK", ""))
        runner.enqueue(CommandResult.Success("OK", ""))
        runner.enqueue(CommandResult.Success("OK", ""))
        val provider = FakeGradleProvider(runner)

        provider.inspect(InspectProjectRequest(Path.of("/repo"), emptyMap()))
        provider.test(TestProjectRequest(Path.of("/repo"), emptyMap()))
        provider.build(BuildProjectRequest(Path.of("/repo"), emptyMap(), emptyList()))

        assertEquals(3, runner.invocations().size, "All three operations must record invocations")
        runner.invocations().forEach { command ->
            assertEquals(
                Executable("gradle"),
                command.executable,
                "Every command must target the gradle executable",
            )
        }
    }

    @Test
    fun `test operation invokes ProcessRunner twice with same request yields two gradle commands`() = runBlocking {
        val runner = FakeProcessRunner()
        runner.enqueue(CommandResult.Success("OK", ""))
        runner.enqueue(CommandResult.Success("OK", ""))
        val provider = FakeGradleProvider(runner)

        val request = TestProjectRequest(Path.of("/repo"), emptyMap())
        provider.test(request)
        provider.test(request)

        val invocations = runner.invocations()
        assertEquals(2, invocations.size)
        assertEquals(Executable("gradle"), invocations[0].executable)
        assertEquals(listOf(Argument("test")), invocations[0].arguments)
        assertEquals(Executable("gradle"), invocations[1].executable)
        assertEquals(listOf(Argument("test")), invocations[1].arguments)
    }

    @Test
    fun `build operation produces Command with gradle assemble`() = runBlocking {
        val runner = FakeProcessRunner()
        runner.enqueue(CommandResult.Success("OK", ""))
        val provider = FakeGradleProvider(runner)

        provider.build(
            BuildProjectRequest(
                workingDirectory = Path.of("/repo"),
                environment = emptyMap(),
                goals = listOf(Argument("publish")),
            ),
        )

        val invocations = runner.invocations()
        assertEquals(1, invocations.size)
        val command = invocations[0]
        assertEquals(Executable("gradle"), command.executable)
        assertEquals(listOf(Argument("assemble"), Argument("publish")), command.arguments)
    }
}
