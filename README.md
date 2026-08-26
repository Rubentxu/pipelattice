# Pipelattice

Configuration/control-plane that compiles typed, versioned GitOps configuration into an
immutable, explainable `ResolvedPipelinePlan`. It does not execute pipelines; execution
belongs to `pipeline-kotlin` behind a plan-protocol adapter (ADR-0001, ADR-0006).

The complete specification lives in [`../pipelattice-spec`](../pipelattice-spec/README.md).
Accepted architecture decisions are tracked under [`docs/adr/`](docs/adr/).

## Requirements

- JDK 21 (`java temurin-21.0.8+9.0.LTS` via asdf, see `.tool-versions`)
- Gradle wrapper (8.14.5), Kotlin 2.4.10

## Build & test (local CI is the gate)

```bash
./gradlew build          # compile + tests + architecture fitness
./gradlew :foundation:test
```

## Modules (M0 — Foundation & Architecture Harness)

| Module | Purpose |
|---|---|
| `build-logic` | Convention plugins: JVM 21 toolchain, `explicitApi`, warnings-as-errors, JUnit platform |
| `foundation` | Identity types (`ProjectId`, `ResourceRef`) and diagnostics model with stable error codes |
| `testkit` | Shared test utilities (`CollectingDiagnosticSink`, later fakes per spec §12.8) |
| `architecture-tests` | FARCH fitness rules enforced with ArchUnit |

Modules are added to `settings.gradle.kts` as they gain real content, one milestone slice at a
time (pipelattice-spec/docs/01_ARCHITECTURE.md §4).

## Definition of Done for every PR

- tests; architecture tests; stable diagnostic code for each new failure;
- no compiler warning without justification; ADR if a boundary changes;
- benchmark when touching a hot path (pipelattice-spec/docs/14_BOOTSTRAP.md §9).

`main` stays green; features are small; spikes live on disposable branches and are promoted
as decisions (ADRs), not necessarily as code.
