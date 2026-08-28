package dev.rubentxu.pipelattice.release.artifact

import dev.rubentxu.pipelattice.release.contract.ArtifactRepositoryContract

/**
 * Minimal TCK shim for FakeArtifactRepository.
 * Inherits all 6 invariant @Test methods from ArtifactRepositoryContract.
 */
class FakeArtifactRepositoryContractTest : ArtifactRepositoryContract() {
    override fun newSubject(): ArtifactRepository = FakeArtifactRepository()
    override suspend fun setupPublishSuccess(result: PublishResult) { (subject() as FakeArtifactRepository).enqueuePublishSuccess(result) }
    override suspend fun setupResolveSuccess(result: ResolveResult) { (subject() as FakeArtifactRepository).enqueueResolveSuccess(result) }
    override suspend fun setupDownloadSuccess(result: DownloadResult) { (subject() as FakeArtifactRepository).enqueueDownloadSuccess(result) }
    override suspend fun setupResolveFailure(failure: ArtifactFailure) { (subject() as FakeArtifactRepository).enqueueResolveFailure(failure) }
    override suspend fun setupDownloadFailure(failure: ArtifactFailure) { (subject() as FakeArtifactRepository).enqueueDownloadFailure(failure) }
    override fun invocations(): List<Any> = (subject() as FakeArtifactRepository).invocations()
}
