package dev.rubentxu.pipelattice.build.domain

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CommandTest {

    @Test
    fun `Command preserves all four construction fields`() {
        val executable = Executable("mvn")
        val arguments = listOf(Argument("verify"))
        val workingDir = Path.of("/repo")
        val environment = mapOf(EnvironmentKey("HOME") to "/root")

        val command = Command(executable, arguments, workingDir, environment)

        assertEquals("mvn", command.executable.value)
        assertEquals(listOf(Argument("verify")), command.arguments)
        assertEquals(Path.of("/repo"), command.workingDirectory)
        assertEquals("/root", command.environment[EnvironmentKey("HOME")])
    }

    @Test
    fun `Command with empty arguments list is valid`() {
        val command = Command(
            executable = Executable("true"),
            arguments = emptyList(),
            workingDirectory = Path.of("/tmp"),
            environment = emptyMap(),
        )
        assertTrue(command.arguments.isEmpty())
    }

    @Test
    fun `Executable rejects blank string`() {
        val error = assertFailsWith<IllegalArgumentException> { Executable("") }
        assertTrue(error.message!!.contains("must not be blank"))
    }

    @Test
    fun `Executable rejects whitespace-only string`() {
        val error = assertFailsWith<IllegalArgumentException> { Executable("   ") }
        assertTrue(error.message!!.contains("must not be blank"))
    }

    @Test
    fun `Argument rejects blank string`() {
        val error = assertFailsWith<IllegalArgumentException> { Argument("") }
        assertTrue(error.message!!.contains("must not be blank"))
    }

    @Test
    fun `EnvironmentKey rejects blank string`() {
        val error = assertFailsWith<IllegalArgumentException> { EnvironmentKey("") }
        assertTrue(error.message!!.contains("must not be blank"))
    }

    @Test
    fun `EnvironmentKey rejects whitespace-only string`() {
        val error = assertFailsWith<IllegalArgumentException> { EnvironmentKey("  ") }
        assertTrue(error.message!!.contains("must not be blank"))
    }

    @Test
    fun `Executable toString returns raw value`() {
        assertEquals("mvn", Executable("mvn").toString())
    }

    @Test
    fun `Argument toString returns raw value`() {
        assertEquals("verify", Argument("verify").toString())
    }

    @Test
    fun `EnvironmentKey toString returns raw name`() {
        assertEquals("HOME", EnvironmentKey("HOME").toString())
    }
}
