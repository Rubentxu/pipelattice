# Roadmap

Execution roadmap for SDDK cycles. Canonical milestone definitions live in
`../pipelattice-spec/docs/08_ROADMAP.md`; this file only tracks active/completed cycles.

## Active Milestones

_None — next milestone (M4+ slices) will be defined when triggered by user request._

## Recently Closed Milestones

- **m4-policy-engine** — Status: completed — Closed cycle
  `p-4c8272c9e7dcdfa2/m4-policy-engine`, sequence=42, ledger=42 events,
  runtime CLOSED on 2026-08-26. Path: **A-min** (smoke debt-verify). Tag
  `v0.3.0-m4-policy-engine` peels to `e4423bc` on main (5 commits over base
  `0d6de34`, 204/0/0 tests workspace, 8/0/0 tests in `:policy-engine`, 5/5 REQ
  COMPLIANT, FARCH-012 active with PROOF). New Gradle module `:policy-engine`
  delivers shell only: domain types `Policy` + `Rule` (sealed, 3 variants) +
  `Decision` + `Severity` + `Violation`, port `PolicyEngine`, no-op impl
  `DefaultPolicyEngine`, 3 frozen `DiagnosticCode`s (POLICY-RULE-001/002/003).
  Integration with `:pipeline-compose` deferred to A-lite. Cycle artifacts in
  `~/.local/share/sddk/projects/p-4c8272c9e7dcdfa2/cycle-artifacts/p-4c8272c9e7dcdfa2/m4-policy-engine/`.

- **m3-debt-cleanup** — Status: completed — Closed cycle
  `p-4c8272c9e7dcdfa2/m3-debt-cleanup`, sequence=35, ledger=35 events,
  runtime CLOSED on 2026-08-26. Path: **A-min** (smoke debt-verify). Tag
  `v0.2.1-m3-cleanup` peels to `b394590` on main (4 commits over base
  `cdb63b9`, 195/0/0 tests workspace, 108/0/0 tests in `:pipeline-compose`,
  6/6 REQ COMPLIANT, debt verdict PASS — 0 introduced, 3 pre-existing
  remediated, FIND-000007 deferred to future cycle). Removed unused
  `diagnostics: ProvenanceSink` param (FIND-000001), deleted unused
  `ImportResolver.ResolveResult` (FIND-000002), decomposed `compose()` 195→55
  LOC into 4 named helpers (FIND-000003), centralized wireName literals
  (FIND-000005), inlined `flattenParams` pass-through (FIND-000006). Added
  `/.sddk/` to `.gitignore` for ADR-0011 defense. Cycle artifacts in
  `~/.local/share/sddk/projects/p-4c8272c9e7dcdfa2/cycle-artifacts/p-4c8272c9e7dcdfa2/m3-debt-cleanup/`.

- **m2-composition-core** — Status: completed — Closed cycle
  `p-4c8272c9e7dcdfa2/m2-composition-core`, sequence=28, ledger=28 events,
  runtime CLOSED on 2026-08-26. Path: **A-full**. Tag `v0.2.0-m2` peels to
  `cdb63b9` on main (20 commits over base `15c4887`, 194/0/0 tests workspace,
  107/0/0 tests in `:pipeline-compose`, 7/7 REQ COMPLIANT, FARCH-011 double
  defense verified with PROOF, debt verdict PASS_WITH_WARNINGS — 3 introduced
  MEDIUM + 4 LOW deferred to backlog, chained-PR strategy executed 6
  stacked-to-main PRs). Module `:pipeline-compose` delivers imports resolution
  + parameter overlay + replace-style merge + provenance + `explain(path)`
  (subset §4 3/9 capas + §7 3/6 reglas; UAT-001/002/015 golden tests). Cycle
  artifacts in
  `~/.local/share/sddk/projects/p-4c8272c9e7dcdfa2/cycle-artifacts/p-4c8272c9e7dcdfa2/m2-composition-core/`.

- **m1-resource-yaml-slice** — Status: completed — Closed cycle
  `p-4c8272c9e7dcdfa2/m1-resource-yaml-slice`, sequence=12, ledger=12 events,
  runtime CLOSED on 2026-08-26. Tag `v0.1.0-m1` peels to `a1b4dc1` on main
  (17 commits over base `00065f1`, 86/0/0 tests, 9/9 REQ COMPLIANT, FARCH-010
  double defense). Cycle artifacts in
  `~/.local/share/sddk/projects/p-4c8272c9e7dcdfa2/cycle-artifacts/p-4c8272c9e7dcdfa2/m1-resource-yaml-slice/`.

## Completed Milestones

- M0 — Foundation & Architecture Harness (bootstrap, pre-SDDK commits up to `c9dea35`).
