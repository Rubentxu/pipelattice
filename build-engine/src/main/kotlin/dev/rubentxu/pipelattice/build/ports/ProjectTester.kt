package dev.rubentxu.pipelattice.build.ports

import dev.rubentxu.pipelattice.build.domain.TestProjectRequest
import dev.rubentxu.pipelattice.foundation.outcome.Outcome

/**
 * Report produced by a test execution.
 *
 * A-min is a placeholder typealias to `Any`. A concrete report structure
 * (passed/failed counts, duration, output location) arrives in A-lite.
 */
public typealias TestReport = Any

/**
 * Failure reasons for test execution.
 *
 * A-min is a placeholder sealed interface. Real variants (compilation-error,
 * test-crash, timeout) arrive in A-lite.
 */
public sealed interface TestFailure {
    public data class TestRunFailed(public val reason: String) : TestFailure
}

/**
 * Port for running a project's test suite.
 *
 * Implementations translate the native test tool output into a [TestReport]
 * and surface failures via [TestFailure].
 */
public interface ProjectTester {
    /**
     * Runs the test suite for the project described by [request].
     *
     * @param request The test request containing project location, environment,
     *               and optional test filter.
     * @return [Outcome.Success] containing the test report, or [Outcome.Failure]
     *         with a [TestFailure] reason.
     */
    public suspend fun test(request: TestProjectRequest): Outcome<TestReport, TestFailure>
}
