# Roadmap

Execution roadmap for SDDK cycles. Canonical milestone definitions live in
`../pipelattice-spec/docs/08_ROADMAP.md`; this file only tracks active/completed cycles.

## Active Milestones

_None — next milestone (M3+) will be defined when triggered by user request._

## Recently Closed Milestones

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
