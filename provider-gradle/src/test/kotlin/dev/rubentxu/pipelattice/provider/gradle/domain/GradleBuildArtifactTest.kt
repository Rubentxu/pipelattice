package dev.rubentxu.pipelattice.provider.gradle.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class GradleBuildArtifactTest {

    @Test
    fun `GradleBuildArtifact preserves GAV coordinates`() {
        val artifact = GradleBuildArtifact(
            group = "dev.rubentxu",
            name = "core",
            version = "1.2.3",
            classifier = "sources",
        )

        assertEquals("dev.rubentxu", artifact.group)
        assertEquals("core", artifact.name)
        assertEquals("1.2.3", artifact.version)
        assertEquals("sources", artifact.classifier)
    }

    @Test
    fun `GradleBuildArtifact defaults classifier to null`() {
        val artifact = GradleBuildArtifact(
            group = "dev.rubentxu",
            name = "my-lib",
            version = "0.1.0",
        )

        assertEquals(null, artifact.classifier)
    }
}
