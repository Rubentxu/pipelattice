package dev.rubentxu.pipelattice.foundation.diagnostics

/**
 * Stable, machine-readable diagnostic code.
 *
 * Convention (see pipelattice-spec/docs/12_TESTING_FITNESS.md §7): every public failure carries
 * a stable code shaped `<AREA>-<CONCERN>-<NNN>`, e.g. `CONFIG-CONFLICT-023`. Codes are part of
 * the public contract and must never be renamed or reused for different semantics.
 */
@JvmInline
public value class DiagnosticCode(public val value: String) {
    init {
        require(CODE_PATTERN.matches(value)) {
            "DiagnosticCode '$value' must match '<AREA>-<CONCERN>-<NNN>' (uppercase, digits), e.g. CONFIG-CONFLICT-023"
        }
    }

    override fun toString(): String = value

    public companion object {
        // Requires a numeric tail so codes read as <AREA>[-<CONCERN>...]-<NNN>, e.g. REF-INVALID-001.
        private val CODE_PATTERN = Regex("""[A-Z]+(-[A-Z0-9]+)*-[0-9]+""")
    }
}
