package dev.rubentxu.pipelattice.release

import dev.rubentxu.pipelattice.release.artifact.ArtifactCapabilities
import dev.rubentxu.pipelattice.release.artifact.ArtifactRepository
import dev.rubentxu.pipelattice.release.release.ReleaseCapabilities
import dev.rubentxu.pipelattice.release.release.ReleaseManager
import dev.rubentxu.pipelattice.release.scm.ScmSource
import dev.rubentxu.pipelattice.release.scm.ScmSourceCapabilities
import kotlin.test.Test
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
class CapabilityDescriptorRegistryTest {

    private val allM8Constants: List<Pair<String, dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor?>> = listOf(
        // SCM
        "SCM_CHECKOUT_V1" to ScmSourceCapabilities.SCM_CHECKOUT_V1,
        "SCM_TAG_V1" to ScmSourceCapabilities.SCM_TAG_V1,
        "SCM_PUSH_V1" to ScmSourceCapabilities.SCM_PUSH_V1,
        // Artifact
        "ARTIFACT_PUBLISH_V1" to ArtifactCapabilities.ARTIFACT_PUBLISH_V1,
        "ARTIFACT_RESOLVE_V1" to ArtifactCapabilities.ARTIFACT_RESOLVE_V1,
        "ARTIFACT_DOWNLOAD_V1" to ArtifactCapabilities.ARTIFACT_DOWNLOAD_V1,
        // Release
        "RELEASE_CALCULATE_V1" to ReleaseCapabilities.RELEASE_CALCULATE_V1,
        "RELEASE_PROMOTE_V1" to ReleaseCapabilities.RELEASE_PROMOTE_V1,
    )

    @Test
    fun `every M8 constant has a non-null descriptor`() {
        for ((name, descriptor) in allM8Constants) {
            assertNotNull(
                descriptor,
                "MISSING-DESCRIPTOR-$name: descriptor must not be null"
            )
        }
    }

    @Test
    fun `every descriptor has non-blank inputSchemaId`() {
        for ((name, descriptor) in allM8Constants) {
            assertTrue(
                descriptor!!.inputSchemaId.value.isNotBlank(),
                "$name: inputSchemaId must not be blank"
            )
        }
    }

    @Test
    fun `every descriptor has non-blank outputSchemaId`() {
        for ((name, descriptor) in allM8Constants) {
            assertTrue(
                descriptor!!.outputSchemaId.value.isNotBlank(),
                "$name: outputSchemaId must not be blank"
            )
        }
    }

    @Test
    fun `every descriptor has at least one side-effect`() {
        for ((name, descriptor) in allM8Constants) {
            assertTrue(
                descriptor!!.sideEffects.isNotEmpty(),
                "$name: sideEffects must not be empty"
            )
        }
    }

    @Test
    fun `every descriptor has a typed failure model`() {
        for ((name, descriptor) in allM8Constants) {
            assertTrue(
                descriptor!!.failureModel is dev.rubentxu.pipelattice.foundation.capability.FailureModel.Typed,
                "$name: failureModel must be Typed"
            )
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
            assertTrue(
                desc.id == id,
                "Descriptor id ${desc.id.value} must match constant ${id.value}"
            )
        }
    }
}
