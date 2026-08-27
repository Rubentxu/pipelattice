package dev.rubentxu.pipelattice.release

import dev.rubentxu.pipelattice.foundation.capability.FailureModel
import dev.rubentxu.pipelattice.foundation.capability.SideEffect
import dev.rubentxu.pipelattice.release.artifact.ArtifactCapabilities
import dev.rubentxu.pipelattice.release.artifact.ArtifactRepository
import dev.rubentxu.pipelattice.release.release.ReleaseCapabilities
import dev.rubentxu.pipelattice.release.release.ReleaseManager
import dev.rubentxu.pipelattice.release.scm.ScmSource
import dev.rubentxu.pipelattice.release.scm.ScmSourceCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Descriptor completeness test for all 8 M8 capability constants.
 *
 * Verifies that every shipped capability constant has a non-null
 * [dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor]
 * registered, with non-blank schemaIds and at least one side-effect
 * classification.
 */
class CapabilityDescriptorTest {

    private data class CapabilityFixture(
        val id: dev.rubentxu.pipelattice.foundation.capability.CapabilityId,
        val descriptor: dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor?,
        val expectedSideEffects: Set<SideEffect>,
    )

    private fun allM8CapabilityFixtures(): List<CapabilityFixture> = listOf(
        // SCM
        CapabilityFixture(
            ScmSource.SCM_CHECKOUT_V1,
            ScmSourceCapabilities.SCM_CHECKOUT_V1,
            setOf(SideEffect.READ_ONLY),
        ),
        CapabilityFixture(
            ScmSource.SCM_TAG_V1,
            ScmSourceCapabilities.SCM_TAG_V1,
            setOf(SideEffect.MUTATING, SideEffect.IDEMPOTENT),
        ),
        CapabilityFixture(
            ScmSource.SCM_PUSH_V1,
            ScmSourceCapabilities.SCM_PUSH_V1,
            setOf(SideEffect.MUTATING),
        ),
        // Artifact
        CapabilityFixture(
            ArtifactRepository.ARTIFACT_PUBLISH_V1,
            ArtifactCapabilities.ARTIFACT_PUBLISH_V1,
            setOf(SideEffect.MUTATING),
        ),
        CapabilityFixture(
            ArtifactRepository.ARTIFACT_RESOLVE_V1,
            ArtifactCapabilities.ARTIFACT_RESOLVE_V1,
            setOf(SideEffect.READ_ONLY),
        ),
        CapabilityFixture(
            ArtifactRepository.ARTIFACT_DOWNLOAD_V1,
            ArtifactCapabilities.ARTIFACT_DOWNLOAD_V1,
            setOf(SideEffect.READ_ONLY),
        ),
        // Release
        CapabilityFixture(
            ReleaseManager.RELEASE_CALCULATE_V1,
            ReleaseCapabilities.RELEASE_CALCULATE_V1,
            setOf(SideEffect.READ_ONLY),
        ),
        CapabilityFixture(
            ReleaseManager.RELEASE_PROMOTE_V1,
            ReleaseCapabilities.RELEASE_PROMOTE_V1,
            setOf(SideEffect.MUTATING),
        ),
    )

    @Test
    fun `descriptor completeness for every shipped capability`() {
        val fixtures = allM8CapabilityFixtures()
        val missing = fixtures.filter { it.descriptor == null }

        assertTrue(
            missing.isEmpty(),
            "Missing descriptors: ${missing.map { "MISSING-DESCRIPTOR-${it.id.value}" }}"
        )
    }

    @Test
    fun `every descriptor has non-blank schema ids`() {
        for (fixture in allM8CapabilityFixtures()) {
            val desc = fixture.descriptor!!
            assertTrue(desc.inputSchemaId.value.isNotBlank())
            assertTrue(desc.outputSchemaId.value.isNotBlank())
        }
    }

    @Test
    fun `every descriptor has at least one side-effect classification`() {
        for (fixture in allM8CapabilityFixtures()) {
            val desc = fixture.descriptor!!
            assertTrue(desc.sideEffects.isNotEmpty())
        }
    }

    @Test
    fun `side-effect classification matches expected capability class`() {
        for (fixture in allM8CapabilityFixtures()) {
            val desc = fixture.descriptor!!
            for (expected in fixture.expectedSideEffects) {
                assertTrue(
                    expected in desc.sideEffects,
                    "Capability ${fixture.id.value} expected $expected in sideEffects but got ${desc.sideEffects}"
                )
            }
        }
    }

    @Test
    fun `failure model is typed for all M8 capabilities`() {
        for (fixture in allM8CapabilityFixtures()) {
            val desc = fixture.descriptor!!
            val typedModel = desc.failureModel as FailureModel.Typed
            assertTrue(typedModel.code.isNotBlank())
        }
    }

    @Test
    fun `provider requirements are populated`() {
        for (fixture in allM8CapabilityFixtures()) {
            val desc = fixture.descriptor!!
            assertNotNull(desc.providerRequirements)
            assertNotNull(desc.providerRequirements.minProviderVersion)
            assertTrue(desc.providerRequirements.minProviderVersion.value.isNotBlank())
        }
    }

    @Test
    fun `descriptor id matches constant value`() {
        val allConstants = listOf(
            ScmSource.SCM_CHECKOUT_V1 to ScmSourceCapabilities.SCM_CHECKOUT_V1,
            ScmSource.SCM_TAG_V1 to ScmSourceCapabilities.SCM_TAG_V1,
            ScmSource.SCM_PUSH_V1 to ScmSourceCapabilities.SCM_PUSH_V1,
            ArtifactRepository.ARTIFACT_PUBLISH_V1 to ArtifactCapabilities.ARTIFACT_PUBLISH_V1,
            ArtifactRepository.ARTIFACT_RESOLVE_V1 to ArtifactCapabilities.ARTIFACT_RESOLVE_V1,
            ArtifactRepository.ARTIFACT_DOWNLOAD_V1 to ArtifactCapabilities.ARTIFACT_DOWNLOAD_V1,
            ReleaseManager.RELEASE_CALCULATE_V1 to ReleaseCapabilities.RELEASE_CALCULATE_V1,
            ReleaseManager.RELEASE_PROMOTE_V1 to ReleaseCapabilities.RELEASE_PROMOTE_V1,
        )

        for ((id, desc) in allConstants) {
            assertEquals(id, desc.id)
        }
    }
}
