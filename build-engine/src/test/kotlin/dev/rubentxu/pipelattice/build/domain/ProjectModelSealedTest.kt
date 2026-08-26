package dev.rubentxu.pipelattice.build.domain

import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the [ProjectModel] sealed hierarchy invariants.
 *
 * These tests ensure that:
 * - [ProjectModel] is a sealed interface with exactly 3 variants
 * - Each variant can be instantiated with its canonical fields
 * - Runtime type discrimination works via `is` checks
 * - Exhaustive [when] expressions compile without an `else` branch
 */
class ProjectModelSealedTest {

    @Test
    fun `ProjectModel is a sealed interface`() {
        val sealedClass = ProjectModel::class
        assertTrue(sealedClass.isSealed, "ProjectModel must be sealed")
    }

    @Test
    fun `ProjectModel has exactly three sealed subclasses`() {
        // Direct subclasses of a sealed interface are declared as nested classes
        val nestedClasses = ProjectModel::class.java.declaredClasses
        assertEquals(3, nestedClasses.size, "ProjectModel must have exactly 3 sealed subclasses")
    }

    @Test
    fun `ProjectModel Generic variant preserves fields`() {
        val generic = ProjectModel.Generic(
            id = "test-id",
            description = "Test description",
        )
        assertEquals("test-id", generic.id)
        assertEquals("Test description", generic.description)
    }

    @Test
    fun `ProjectModel Maven variant preserves fields`() {
        val maven = ProjectModel.Maven(
            id = "com.example:my-artifact",
            groupId = "com.example",
            artifactId = "my-artifact",
            version = "1.0.0",
        )
        assertEquals("com.example:my-artifact", maven.id)
        assertEquals("com.example", maven.groupId)
        assertEquals("my-artifact", maven.artifactId)
        assertEquals("1.0.0", maven.version)
    }

    @Test
    fun `ProjectModel Gradle variant preserves fields`() {
        val gradle = ProjectModel.Gradle(
            id = "com.example:my-project",
            group = "com.example",
            name = "my-project",
            version = "1.0.0",
        )
        assertEquals("com.example:my-project", gradle.id)
        assertEquals("com.example", gradle.group)
        assertEquals("my-project", gradle.name)
        assertEquals("1.0.0", gradle.version)
    }

    @Test
    @Suppress("USELESS_IS_CHECK")
    fun `ProjectModel Gradle is instance of ProjectModel`() {
        val gradle = ProjectModel.Gradle(
            id = "test",
            group = "group",
            name = "name",
            version = "1.0",
        )
        assertTrue(gradle is ProjectModel, "Gradle variant must be a ProjectModel")
    }

    @Test
    @Suppress("USELESS_IS_CHECK")
    fun `ProjectModel Generic is instance of ProjectModel`() {
        val generic = ProjectModel.Generic(id = "test", description = null)
        assertTrue(generic is ProjectModel, "Generic variant must be a ProjectModel")
    }

    @Test
    @Suppress("USELESS_IS_CHECK")
    fun `ProjectModel Maven is instance of ProjectModel`() {
        val maven = ProjectModel.Maven(
            id = "g:a:v",
            groupId = "g",
            artifactId = "a",
            version = "v",
        )
        assertTrue(maven is ProjectModel, "Maven variant must be a ProjectModel")
    }

    @Test
    fun `when-expression on ProjectModel is exhaustive without else`() {
        fun describe(model: ProjectModel): String = when (model) {
            is ProjectModel.Generic -> "Generic: ${model.id}"
            is ProjectModel.Maven -> "Maven: ${model.groupId}:${model.artifactId}"
            is ProjectModel.Gradle -> "Gradle: ${model.group}:${model.name}"
        }

        val generic = ProjectModel.Generic("id", null)
        val maven = ProjectModel.Maven("g:a:v", "g", "a", "v")
        val gradle = ProjectModel.Gradle("g:n:v", "g", "n", "v")

        assertEquals("Generic: id", describe(generic))
        assertEquals("Maven: g:a", describe(maven))
        assertEquals("Gradle: g:n", describe(gradle))
    }
}
