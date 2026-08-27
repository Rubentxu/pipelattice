package dev.rubentxu.pipelattice.release.testing

import kotlin.random.Random

/**
 * Shared TCK test utilities for secret-exclusion verification.
 *
 * Provides a [SecretProbe] that carries a unique synthetic marker (PROBE-SECRET-MATERIAL-<suffix>).
 * The marker is:
 * - NOT credential-shaped (does not trigger FARCH-018 patterns)
 * - UNIQUE per test invocation (prevents cross-test contamination)
 * - EASILY SEARCHABLE (TCK can verify both positive presence and negative exclusion)
 *
 * The probe is used as a POSITIVE CONTROL:
 * - The marker IS present in [SecretProbe.marker] (proves the probe is real)
 * - The marker must NOT appear in any rendered surface (toString, diagnostics,
 *   exception messages, invocation snapshots) — proving the exclusion works.
 *
 * The distinction between "credential-shaped" (FARCH-018 scanner) and "unique marker" (TCK)
 * is intentional: FARCH-018 is enforced by the architecture scan; the TCK verifies
 * that the fake's surfaces (invocations, toString) do not expose any marker that was
 * injected as part of the test.
 */
public object SecretProbeFactory {

    /**
     * Generates a unique synthetic probe marker that does NOT match credential patterns.
     * Uses a prefix that is clearly synthetic and a random suffix for uniqueness.
     *
     * The suffix is a 16-character uppercase hex string, providing 2^64 possible values.
     */
    public fun generateProbe(): SecretProbe {
        val suffix = Random.nextLong(0, Long.MAX_VALUE).toString(16).uppercase().padStart(16, '0')
        val marker = "PROBE-SECRET-MATERIAL-$suffix"
        return SecretProbe(marker)
    }

    /**
     * Simple probe container for TCK positive control.
     *
     * The [marker] is the unique identifier that the TCK verifies:
     * - IS present in the probe (positive control)
     * - is NOT present in any rendered surface (negative exclusion)
     */
    public class SecretProbe(public val marker: String)
}
