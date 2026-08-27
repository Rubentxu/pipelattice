package dev.rubentxu.pipelattice.release.artifact

import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Integration-lite tests for [FakeArtifactRepository].
 */
class FakeArtifactRepositoryTest {

    @Test
    fun `publish roundtrip preserves identity`() = runBlocking {
        val repo = FakeArtifactRepository()
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        val result = PublishResult(coord, "sha256:abc123...")
        repo.enqueuePublishSuccess(result)

        val outcome = repo.publish(
            PublishRequest(coord, Path.of("/tmp/lib-1.0.0.jar"))
        )

        assertIs<Outcome.Success<PublishResult>>(outcome)
        assertEquals(coord, outcome.value.coordinate)
        assertEquals("sha256:abc123...", outcome.value.digest)
        assertEquals(1, repo.invocations().size)
    }

    @Test
    fun `resolve roundtrip preserves identity`() = runBlocking {
        val repo = FakeArtifactRepository()
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        val result = ResolveResult(coord, "sha256:abc123...", 12345L)
        repo.enqueueResolveSuccess(result)

        val outcome = repo.resolve(ResolveRequest(coord))

        assertIs<Outcome.Success<ResolveResult>>(outcome)
        assertEquals(coord, outcome.value.coordinate)
        assertEquals(12345L, outcome.value.sizeBytes)
    }

    @Test
    fun `download roundtrip preserves identity`() = runBlocking {
        val repo = FakeArtifactRepository()
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        val result = DownloadResult(coord, Path.of("/tmp/lib.jar"), 12345L)
        repo.enqueueDownloadSuccess(result)

        val outcome = repo.download(DownloadRequest(coord, Path.of("/tmp/lib.jar")))

        assertIs<Outcome.Success<DownloadResult>>(outcome)
        assertEquals(coord, outcome.value.coordinate)
        assertEquals(12345L, outcome.value.sizeBytes)
    }

    @Test
    fun `publish then resolve then download via fake`() = runBlocking {
        val repo = FakeArtifactRepository()
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")

        repo.enqueuePublishSuccess(PublishResult(coord, "sha256:abc"))
        repo.enqueueResolveSuccess(ResolveResult(coord, "sha256:abc", 999L))
        repo.enqueueDownloadSuccess(DownloadResult(coord, Path.of("/tmp/lib.jar"), 999L))

        val r1 = repo.publish(PublishRequest(coord, Path.of("/tmp/lib.jar")))
        val r2 = repo.resolve(ResolveRequest(coord))
        val r3 = repo.download(DownloadRequest(coord, Path.of("/tmp/lib.jar")))

        assertIs<Outcome.Success<PublishResult>>(r1)
        assertIs<Outcome.Success<ResolveResult>>(r2)
        assertIs<Outcome.Success<DownloadResult>>(r3)
        assertEquals(3, repo.invocations().size)
    }

    @Test
    fun `reset clears queue and invocations`() = runBlocking {
        val repo = FakeArtifactRepository()
        val coord = ArtifactCoordinate("dev.example", "lib", "1.0.0")
        repo.enqueuePublishSuccess(PublishResult(coord, "sha256:abc"))

        repo.publish(PublishRequest(coord, Path.of("/tmp/lib.jar")))
        repo.reset()

        assertTrue(repo.invocations().isEmpty())
    }
}
