# ADR-0027: SecretValue.marker privacy-positive deviation

## Status

Accepted (draft — pending vault sync)

## Date

2026-08-28 (drafted in cycle `[[CYC-p-4c8272c9e7dcdfa2/m18-m8-release-engine-adapters]]`)

## Context

`SecretValue` in `:foundation` was designed in m17 v1 (`REQ-SecretValue-NonRendering.md`) to have `public val marker: String` — an opaque identifier used for `equals`/`hashCode`, NOT the material. The spec literal said:

> `public class SecretValue(public val marker: String)` in `dev.rubentxu.pipelattice.foundation.secret`.

The **acceptance #5** of spec m17 v1 (verify-report-r3, line 200) verified via reflection: "no other public member of `SecretValue` exposes the material (reflection on declared members returns zero public `val`s/`var`s of type `String` besides `marker` and the `material()` method)".

**What was actually implemented** (m17 v1 → main, commit `3197d39`, `foundation/src/main/kotlin/dev/rubentxu/pipelattice/foundation/secret/SecretValue.kt:26-29`):

```kotlin
public class SecretValue private constructor(
    private val marker: String,   // <-- PRIVATE, not public
    private val material: String,
) {
    public fun material(): String = material
    override fun toString(): String = REDACTION_MARKER  // "<redacted:SecretValue>"
    // equals / hashCode derive from marker (private but accessible internally)
}
```

**The impl is strictly stronger than the spec literal**:

| Aspect | Spec literal v1 | Impl real | Spec amendment v2 (this ADR) |
|--------|----------------|-----------|------------------------------|
| `marker` visibility | `public val` | `private val` | "private" (impl wins) |
| Public `String`-typed members besides `material()` | exactly 1 (`marker`) | **exactly 0** | "exactly 0" (impl wins) |
| `material()` access | yes | yes | yes |
| `toString()` redaction | yes | yes | yes |
| `equals`/`hashCode` on marker | yes | yes | yes |
| Construction via `SecretValue.of(...)` only | yes | yes | yes |

The verify-report-r3 (m17) classified this as **LOW-D2 (still open, documented)**:

> [LOW-D2] `SecretValue.marker` is private (privacy-positive deviation). STILL OPEN, DOCUMENTED. `SecretValue private constructor(private val marker, private val material)`; spec said `public val marker`. Privacy-positive (zero public String members); spec acceptance #5 reflection check moot. **Recommend documenting in ADR.**

This ADR is the closure.

## Decision

**Keep the impl with `private val marker`**. Amend the spec `REQ-SecretValue-NonRendering` v1 → v2 with a privacy-positive deviation note that:

1. Explicitly acknowledges the impl is **stronger than the spec literal**.
2. Replaces the spec acceptance #5 (which verified "exactly 1 public String member besides `material()`" — `marker`) with a **stronger acceptance** ("zero public String members besides `material()`" — verifiable by reflection over declared public members).
3. Captures the decision evidence in this ADR (irreversible architectural decision with real trade-off).
4. Does NOT revert the code — the privacy-positive invariant survives by construction, not by scrubbing.

**The test `SecretValueTest.privacy_positive_reflection_guard` (S25)** verifies via reflection that `SecretValue`'s declared public surface contains exactly one `String`-typed accessor: `material()`. No public `val`/`var` of type `String`. Spec v2 replaces m17 v1 acceptance `material_is_the_only_accessor` with `privacy_positive_reflection_guard` — the guarantee is strictly stronger.

## Criteria for Future Reversal

If a future cycle requires programmatic access to `marker` from outside the class (trigger: real need for serialization or telemetry of marker without going through `material()` or `toString()`):

1. Create a cycle `mx-secretvalue-marker-exposure` with justified scope.
2. Add a getter `public fun marker(): String` or equivalent (NOT `public val` — that would reintroduce the field-level leak via reflection over fields).
3. Replace the acceptance `privacy_positive_reflection_guard` with an acceptance that allows `marker()` in addition to `material()`.
4. ADR of that cycle supersedes ADR-0027.

**Why NOT revert now**: the guarantee `zero public String members besides material()` is **strictly better than** `one public String member besides material()` for FARCH-018 secret isolation. Any reversion should come with explicit justification of why the marker needs to be public — and that has not emerged.

## Test Evidence (S25, S26)

**S25 — `privacy_positive_reflection_guard`**: reflection on `SecretValue` declared public members returns exactly one `String`-typed accessor (`material()`), zero public `val`/`var` of type `String`.

**S26 — `equals_hashcode_unchanged_after_deviation`**: `SecretValue.of("env-var", "p-A").equals(SecretValue.of("env-var", "p-B"))` is `true`; `hashCode` derived from `marker`; `material()` returns per-instance value. The equality invariant does not change by making marker private — Kotlin's internal comparison accesses private fields without restriction from within the class itself.

Both tests are **GREEN** with the current impl — the deviation requires no code change.

## Debt Findings Closed

- **LOW-D2 (m17 verify-report-r3)** — CLOSED by ADR-0027 + spec v2 amendment + S25/S26 test evidence.

## Explicit Carry (NOT closed in this ADR)

- **NEW-3 (m17 verify-report-r3 line 217) — `failure.toString()` not sanitized**: the `marker` privacy-positive is one of the mitigations (being private, it cannot appear via accidental `marker.toString()`), but `failure.reason` field is still `public String` and may carry credentials. The sanitization of `failure.toString()` rendering is deferred to `m19-promote-policy` or a dedicated cycle. NOT closed in this ADR.

## Changelog

- 2026-08-28 | created | status=draft | cycle=m18-m8-release-engine-adapters | vault sync pending
