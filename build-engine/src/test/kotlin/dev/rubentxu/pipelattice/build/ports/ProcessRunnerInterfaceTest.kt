package dev.rubentxu.pipelattice.build.ports

import dev.rubentxu.pipelattice.build.domain.BuildArtifact
import dev.rubentxu.pipelattice.build.domain.BuildProjectRequest
import dev.rubentxu.pipelattice.build.domain.InspectProjectRequest
import dev.rubentxu.pipelattice.build.domain.ProjectModel
import dev.rubentxu.pipelattice.build.domain.TestProjectRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Verifies that the build ports are properly declared and can be compiled against.
 *
 * These compile-time interface tests validate that:
 * - All port interfaces are resolvable
 * - Generic type parameters are correctly declared on ProjectInspector and ProjectBuilder
 *
 * The `suspend` modifier on all port methods is enforced by the Kotlin compiler;
 * any non-suspend `run`/`inspect`/`test`/`build` method will produce a type error
 * when any caller attempts to call it with `suspend` context.
 */
class ProcessRunnerInterfaceTest {

    @Test
    fun `ProcessRunner interface is resolvable`() {
        val clazz = ProcessRunner::class.java
        assertNotNull(clazz)
    }

    @Test
    fun `ProjectInspector interface is resolvable`() {
        val clazz = ProjectInspector::class.java
        assertNotNull(clazz)
    }

    @Test
    fun `ProjectInspector has type parameter P constrained to ProjectModel`() {
        val typeParams = (ProjectInspector::class.java as Class<*>).typeParameters
        assertEquals(1, typeParams.size)
        assertEquals("P", typeParams[0].name)
        assertEquals(ProjectModel::class.java, typeParams[0].bounds.firstOrNull())
    }

    @Test
    fun `ProjectTester interface is resolvable`() {
        val clazz = ProjectTester::class.java
        assertNotNull(clazz)
    }

    @Test
    fun `ProjectBuilder interface is resolvable`() {
        val clazz = ProjectBuilder::class.java
        assertNotNull(clazz)
    }

    @Test
    fun `ProjectBuilder has type parameter A constrained to BuildArtifact`() {
        val typeParams = (ProjectBuilder::class.java as Class<*>).typeParameters
        assertEquals(1, typeParams.size)
        assertEquals("A", typeParams[0].name)
        assertEquals(BuildArtifact::class.java, typeParams[0].bounds.firstOrNull())
    }
}
