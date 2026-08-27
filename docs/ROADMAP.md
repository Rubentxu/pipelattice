# Roadmap

Execution roadmap for SDDK cycles. Canonical milestone definitions live in
`../pipelattice-spec/docs/08_ROADMAP.md`; this file only tracks active/completed cycles.

## Active Milestones

_None — next milestone (M8+ slices) will be defined when triggered by user request._

## Recently Closed Milestones

- **m11-git-snapshot-repository** — Status: completed — Closed cycle
  `p-4c8272c9e7dcdfa2/m11-git-snapshot-repository`, sequence=100..106,
  ledger=106 events, runtime CLOSED on 2026-08-27. Path: **A-lite**
  (coherence 97/100 · verify PASS_WITH_WARNINGS · debt PASS_WITH_WARNINGS
  standard depth). Tag `v0.8.0-m11-git-snapshot-repository` peels to
  `5ae22df` on main (4 commits over base `396ffae`, workspace 361/0/0 tests
  +4 slow-tier skipped-by-design, FARCH-011..016 active — 016 NEW:
  :fleet-diff process-isolation mirror of FARCH-014 with dual defense and
  fire-tested defensive scan).
  New capability per spec 09 §S-009/S-017 + spec 04 §7 + spec 17 §2:
  - `GitSnapshotRepository` adapter (port `SnapshotRepository` impl) resolving
    branch/tag/SHA refs through a git process port (`GitCommand` +
    `GitRefResolution` + `DefaultProcessRunner` facade en :build-engine);
    zero new external deps.
  - PlanFingerprint domain-tag scheme frozen:
    `sha256("git-ref-only/v1:" + resolvedSha)` — GraphSnapshot shape untouched.
  - CLI real plumbing: `--baseline/--candidate/--repo(default .)` resolve
    against live repo; unknown ref → exit 2 with failing ref in stderr;
    m10 exit-code contract preserved byte-for-byte (CliMainExitCodesTest 6/6).
  - Slow tier `@Tag("slow")` + @TempDir real-git seeding via slowTest task.
  - Scope PLUMBING-ONLY: graph content population deferred M12+ (ruling Q1);
    LOC ~800 vs 470 forecast (+70%, W1) explained by DUP-001/002 test
    verbosity (~205 LOC); carry-ins INC-008..011 (incl. FARCH-016 v2 target:
    add java.lang.System.exit to forbidden list — bytecode evasion of W2).
  Cycle artifacts in
  `~/.local/share/sddk/projects/p-4c8272c9e7dcdfa2/cycle-artifacts/p-4c8272c9e7dcdfa2/m11-git-snapshot-repository/`.

- **m10-debt-cleanup** — Status: completed — Closed cycle
  `p-4c8272c9e7dcdfa2/m10-debt-cleanup`, sequence=96, ledger=97 events,
  runtime CLOSED on 2026-08-27. Path: **B-direct** (hotfix-style cleanup, 124
  net LOC). Tag `v0.7.1-m10-debt-cleanup` peels to `a74807c` on main
  (2 commits over base `dfd9234`, 344/0/0 tests workspace +6 new in
  `:fleet-diff/cli`, 12/12 arch tests, verify PASS, debt N/A on B-direct).
  **Closed all 3 outstanding warnings from m9**:
  (W1) `:fleet-diff/build.gradle.kts` gains `application` plugin + `mainClass`
  → `./gradlew :fleet-diff:run` task now available;
  (W2) `cli/Main.kt` refactor extracts `run(args): Int` from `main(args)`,
  adds `MissingArgumentException`, and maps CLI errors to spec exit codes
  per `pipelattice-spec/docs/17_CLI_CONTROL_PLANE.md` §4 (0 success / 2
  validation failure / 10 internal error / 64 EX_USAGE per BSD sysexits.h);
  (W3) LOC envelope 124 vs ~140 budget — within tolerance, no impl bloat.
  6 new tests in `CliMainExitCodesTest` cover all four exit codes.
  Cycle artifacts in
  `~/.local/share/sddk/projects/p-4c8272c9e7dcdfa2/cycle-artifacts/p-4c8272c9e7dcdfa2/m10-debt-cleanup/`.

- **m9-fleet-candidate-diff** — Status: completed — Closed cycle
  `p-4c8272c9e7dcdfa2/m9-fleet-candidate-diff`, sequence=90, ledger=91 events,
  runtime CLOSED on 2026-08-26. Path: **A-lite** (3 chained slices, ~1090 LOC).
  Tag `v0.7.0-m9-fleet-candidate-diff` peels to `bea4665` on main (4 commits
  over base `4b55aed`, 338/0/0 tests workspace, 11/11 REQ COMPLIANT, FARCH-011/
  013/015 active, verify PASS_WITH_WARNINGS 280/300, debt PASS_WITH_WARNINGS
  0/0/5/17). 3 chained slices stacked-to-main:
  - **Slice 1 (~290 LOC)**: foundations — `:graph-projection` domain gains
    9 new EdgeKinds (DERIVED_FROM, USES, REQUIRES, PROVIDES, GOVERNED_BY,
    TARGETS, PRODUCES, CONSUMES, COMPILES_TO → 14/14 complete); new
    `AffectedSubgraph.traverse(BFS, maxDepth=64, visited)` + `.blastRadius()`
    + `AdjacencyIndex` lazy O(1); `:build-engine/CommandResult.Failed` gains
    `signal: Int?` + `durationMs: Long`.
  - **Slice 2 (~313 LOC)**: ConfigurationCompiler decorator wiring — NEW
    `:pipeline-compose/translate/CompositionToGraphTranslator.kt` (internal
    pure mapper per Q8 mapping table: IMPORTS+EXTENDS for PROFILE_IMPORT,
    SELECTS for PROFILE, OVERRIDES for LOCAL) + NEW
    `GraphEmittingCompositionEngine(delegate, store)` decorator
    (`CompositionEngine by delegate` — does NOT modify M2 frozen
    `CompositionEngine` interface or `DefaultCompositionEngine` class; all
    M2 golden UAT tests green unmodified).
  - **Slice 3 (~487 LOC)**: NEW `:fleet-diff` module — `FleetCandidateDiff`
    orchestrator + `FleetDiffReport` (7 sections per spec §8: affectedProjects,
    effectiveChanges, invalidPlans, newPolicyViolations, resolvedPolicyViolations,
    providerChanges, localOverrides) + sealed `FleetDiffChange`
    (Added/Removed/Modified) + `SnapshotRepository` port +
    `InMemorySnapshotRepository` (ConcurrentHashMap thread-safe) +
    hand-written `FleetDiffJsonEncoder` (schema "fleet-diff/v1", no kotlinx
    dep per ADR-0021) + `cli/Main.kt` (stdlib arg parsing, --baseline +
    --candidate required). Module depends on `:foundation + :graph-projection`
    only (Q10: reusable across compilers).
  Closed **3 of 5 accumulated gaps**: (3) CommandResult.Failed metadata,
  (4) ConfigurationCompiler → GraphChangeSet wiring (HIGH), (5) Affected
  subgraph traversal. 3 warnings retained as post-release debt items:
  application plugin missing on :fleet-diff (W1), CLI exit-code contract
  deviation (W2), LOC envelope overrun 2795 vs 1090 planned driven by test
  granularity (W3). Cycle artifacts in
  `~/.local/share/sddk/projects/p-4c8272c9e7dcdfa2/cycle-artifacts/p-4c8272c9e7dcdfa2/m9-fleet-candidate-diff/`.

- **m8-debt-cleanup-2** — Status: completed — Closed cycle
  `p-4c8272c9e7dcdfa2/m8-debt-cleanup-2`, sequence=77, ledger=77 events,
  runtime CLOSED on 2026-08-26. Path: **A-min** (smoke debt-verify). Tag
  `v0.6.1-m8-debt-cleanup-2` peels to `1eff5d0` on main (3 commits over base
  `891cbe2`, 284/0/0 tests workspace, 33/0/0 in `:build-engine`, 4/4 REQ
  COMPLIANT, FARCH-011..015 still green). Closed **2 of 5 accumulated gaps**:
  (1) **FARCH docsync (LOW)**: `12_TESTING_FITNESS.md` §5 updated to reflect
  FARCH-011..015 actual state; ADR-0026 created documenting the drift; reserved
  FARCH-014/015/016 shifted to FARCH-017/018/019 for original M10/M11/M12 intent.
  (2) **ProjectModel sealed (MEDIUM)**: `:build-engine/domain/ProjectModel.kt`
  replaced `typealias ProjectModel = Any` with sealed interface (3 variants:
  Generic, Maven, Gradle); backwards compat via `@Deprecated typealias
  AnyProjectModel = ProjectModel.Generic` (1-release grace); `:provider-gradle/
  GradleProjectModel` now typealiases to `ProjectModel.Gradle`. **3 gaps
  deferred to M9 (A-lite)**: CommandResult.Failed metadata, ConfigurationCompiler
  → GraphChangeSet wiring (HIGH — touches M2 frozen), affected-subgraph traversal.
  Cycle artifacts in
  `~/.local/share/sddk/projects/p-4c8272c9e7dcdfa2/cycle-artifacts/p-4c8272c9e7dcdfa2/m8-debt-cleanup-2/`.

- **m7-reactive-graph** — Status: completed — Closed cycle
  `p-4c8272c9e7dcdfa2/m7-reactive-graph`, sequence=66, ledger=66 events,
  runtime CLOSED on 2026-08-26. Path: **A-min** (smoke debt-verify). Tag
  `v0.6.0-m7-reactive-graph` peels to `e4651f1` on main (6 commits over base
  `ef3a97e`, 274/0/0 tests workspace, 21 tests in `:graph-projection`,
  5/5 REQ COMPLIANT, FARCH-015 active with PROOF). New Gradle module
  `:graph-projection` delivers M6 Reactive Configuration Graph V1 shell only:
  domain types `GraphNode` (5 sealed variants: Project, Component,
  PipelineProfile, ConfigSource, ResolvedPipelinePlan) + `EdgeKind` (5 data
  objects: IMPORTS, EXTENDS, SELECTS, OVERRIDES, PATCHES — rest 9 deferred
  A-lite) + `Edge` + `GraphChangeSet` + `GraphSnapshot` + `PlanFingerprint`
  (SHA-256 value class, 64-char hex invariant) + `StructuralDiff` with
  companion `diff()`, port `GraphProjectionStore` (apply + snapshot per spec
  01 §6), impl `InMemoryGraphProjectionStore` (LinkedHashSet + canonical
  sort + remove-then-add last-writer-wins + deterministic SHA-256
  fingerprint). **M6 Decision Gate Verdict (V1): PASS** per spec 04 §6 —
  declared dependencies, in-memory persistence only, no read tracing (V3
  future), no configuration compiler integration (deferred A-lite). 5 gaps
  documented (1 LOW docsync, 3 MEDIUM A-lite deferrals, 1 HIGH A-lite when
  compiler integrates). Cycle artifacts in
  `~/.local/share/sddk/projects/p-4c8272c9e7dcdfa2/cycle-artifacts/p-4c8272c9e7dcdfa2/m7-reactive-graph/`.

- **m6-gradle-abstraction-proof** — Status: completed — Closed cycle
  `p-4c8272c9e7dcdfa2/m6-gradle-abstraction-proof`, sequence=57, ledger=57
  events, runtime CLOSED on 2026-08-26. Path: **A-min** (smoke debt-verify).
  Tag `v0.5.0-m6-gradle-abstraction-proof` peels to `671896c` on main (5 commits
  over base `ad3ba95`, 242/0/0 tests workspace, 9 tests in `:provider-gradle`,
  5/5 REQ COMPLIANT, FARCH-014 active with PROOF). New Gradle module
  `:provider-gradle` delivers shell only: domain `GradleBuildArtifact` +
  `GradleProjectModel`, fake `FakeGradleProvider` implementing
  `ProjectInspector` + `ProjectTester` + `ProjectBuilder`, contract test
  proving `:build-engine` abstraction holds for 2nd provider. **M5 Decision
  Gate Verdict: PASS** — abstraction holds, no capability redesign required.
  3 gaps documented in `gaps-report.md` (all defer/fix-now, no redesign): #1
  LOW FARCH numbering drift (014 used by impl, reserved by spec for M11),
  #2 MEDIUM `ProjectModel = typealias Any`, #3 MEDIUM `CommandResult.Failed`
  missing signal/durationMs. Real Maven provider implementation still pending
  (deferred to A-lite cycle). Cycle artifacts in
  `~/.local/share/sddk/projects/p-4c8272c9e7dcdfa2/cycle-artifacts/p-4c8272c9e7dcdfa2/m6-gradle-abstraction-proof/`.

- **m5-build-vertical-slice** — Status: completed — Closed cycle
  `p-4c8272c9e7dcdfa2/m5-build-vertical-slice`, sequence=49, ledger=49 events,
  runtime CLOSED on 2026-08-26. Path: **A-min** (smoke debt-verify). Tag
  `v0.4.0-m5-build-vertical-slice` peels to `ab9945b` on main (5 commits over
  base `1cbf3b5`, 242/0/0 tests workspace, 23 tests in `:build-engine`, 28 in
  `:foundation` (+16 for Outcome), 5/5 REQ COMPLIANT, FARCH-013 active with
  PROOF). New Gradle module `:build-engine` delivers shell only: domain types
  `Command` + `CommandResult` (sealed) + `Executable` + `Argument` +
  `EnvironmentKey` + `BuildArtifact` + `ProjectModel` + 3 request types, ports
  `ProcessRunner` (suspend) + `ProjectInspector<P>` + `ProjectTester` +
  `ProjectBuilder<A>`, test fixture `FakeProcessRunner` with FIFO scripted
  queue + invocations(). Transversal addition to `:foundation`: `Outcome<S,F>`
  sealed type with 6 extensions (map/getOrNull/getOrElse/onSuccess/onFailure/fold)
  — non-breaking enabler for future capability ports. Maven/Gradle provider
  implementations deferred to A-lite cycles. Cycle artifacts in
  `~/.local/share/sddk/projects/p-4c8272c9e7dcdfa2/cycle-artifacts/p-4c8272c9e7dcdfa2/m5-build-vertical-slice/`.

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
