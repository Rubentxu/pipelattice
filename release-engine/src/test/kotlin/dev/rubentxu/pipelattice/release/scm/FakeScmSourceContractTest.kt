package dev.rubentxu.pipelattice.release.scm

import dev.rubentxu.pipelattice.release.contract.ScmSourceContract

/**
 * Minimal TCK shim for FakeScmSource.
 * Inherits all 6 invariant @Test methods from ScmSourceContract.
 */
class FakeScmSourceContractTest : ScmSourceContract() {
    override fun newSubject(): ScmSource = FakeScmSource()
    override suspend fun setupCheckoutSuccess(result: CheckoutResult) { (subject() as FakeScmSource).enqueueCheckoutSuccess(result) }
    override suspend fun setupCheckoutFailure(failure: ScmFailure) { (subject() as FakeScmSource).enqueueCheckoutFailure(failure) }
    override suspend fun setupTagSuccess(result: TagResult) { (subject() as FakeScmSource).enqueueTagSuccess(result) }
    override suspend fun setupTagFailure(failure: ScmFailure) { (subject() as FakeScmSource).enqueueTagFailure(failure) }
    override suspend fun setupPushSuccess(result: PushResult) { (subject() as FakeScmSource).enqueuePushSuccess(result) }
    override fun invocations(): List<Any> = (subject() as FakeScmSource).invocations()
}
