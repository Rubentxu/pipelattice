package dev.rubentxu.pipelattice.build.domain

/**
 * Result of a completed command execution.
 *
 * This sealed interface captures the two terminal states of a subprocess:
 * - [Success] when the process exited with code 0.
 * - [Failed] when the process exited with a non-zero code or was unable to start.
 *
 * ## Note
 * `durationMs` and `signal` fields are deferred to A-lite, when a real provider
 * requires them (see spec 03 §5, decision Q3).
 */
public sealed interface CommandResult {

    /**
     * Captures the output of a successful command execution.
     *
     * @property stdout Standard output of the subprocess.
     * @property stderr Standard error of the subprocess.
     */
    public data class Success(
        public val stdout: String,
        public val stderr: String,
    ) : CommandResult

    /**
     * Captures the output and exit code of a failed command execution.
     *
     * @property exitCode The numeric exit code of the subprocess.
     * @property stdout Standard output of the subprocess.
     * @property stderr Standard error of the subprocess.
     */
    public data class Failed(
        public val exitCode: Int,
        public val stdout: String,
        public val stderr: String,
    ) : CommandResult
}
