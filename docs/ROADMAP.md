# Roadmap

Execution roadmap for SDDK cycles. Canonical milestone definitions live in
`../pipelattice-spec/docs/08_ROADMAP.md`; this file only tracks active/completed cycles.

## Active Milestones

- **m2-composition-core** — Status: pending_proposal — Composition compiler slice:
  imports + merge + provenance for the central/profile/override scenario. Canonical
  scope lives in `../pipelattice-spec/docs/08_ROADMAP.md` (§ M2) and
  `../pipelattice-spec/docs/02_CONFIGURATION_SPEC.md` (§ merge, patches, provenance).

## Recently Closed Milestones

- **m1-resource-yaml-slice** — Status: completed — Closed cycle
  `p-4c8272c9e7dcdfa2/m1-resource-yaml-slice`, sequence=12, ledger=12 events,
  runtime CLOSED on 2026-08-26. Tag `v0.1.0-m1` peels to `a1b4dc1` on main
  (17 commits over base `00065f1`, 86/0/0 tests, 9/9 REQ COMPLIANT, FARCH-010
  double defense). Cycle artifacts in
  `~/.local/share/sddk/projects/p-4c8272c9e7dcdfa2/cycle-artifacts/p-4c8272c9e7dcdfa2/m1-resource-yaml-slice/`.

## Completed Milestones

- M0 — Foundation & Architecture Harness (bootstrap, pre-SDDK commits up to `c9dea35`).
