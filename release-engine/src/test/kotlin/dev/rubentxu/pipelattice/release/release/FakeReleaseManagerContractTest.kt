package dev.rubentxu.pipelattice.release.release

import dev.rubentxu.pipelattice.release.contract.ReleaseManagerContract

/**
 * Minimal TCK shim for FakeReleaseManager.
 * Inherits all 6 invariant @Test methods from ReleaseManagerContract.
 * Override newSubject() to return cached FakeReleaseManager.
 */
class FakeReleaseManagerContractTest : ReleaseManagerContract() {
    override fun newSubject(): ReleaseManager = FakeReleaseManager()
    override suspend fun setupCalculateSuccess(result: CalculateResult) {
        (subject() as FakeReleaseManager).enqueueCalculateSuccess(result)
    }
    override suspend fun setupPromoteSuccess(result: PromoteResult) {
        (subject() as FakeReleaseManager).enqueuePromoteSuccess(result)
    }
    override suspend fun setupPromoteFailure(failure: ReleaseFailure) {
        (subject() as FakeReleaseManager).enqueuePromoteFailure(failure)
    }
    override fun invocations(): List<Any> = (subject() as FakeReleaseManager).invocations()
}
