[English](README.md) | [Español](README.es.md)

# Pipelattice

[![CI](https://github.com/Rubentxu/pipelattice/actions/workflows/ci.yml/badge.svg)](https://github.com/Rubentxu/pipelattice/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF.svg)
![JVM](https://img.shields.io/badge/JVM-21-4c8e33.svg)

**Pipelattice is a GitOps configuration control-plane that compiles typed, versioned pipeline configuration into an immutable, explainable `ResolvedPipelinePlan`.**

It does **not** execute pipelines. Execution belongs to a runtime (such as [pipeline-kotlin](https://github.com/Rubentxu/pipeline-kotlin)) that consumes the compiled plan through a small versioned protocol adapter.

## The problem it solves

Enterprise CI/CD platforms tend to accumulate corporate decisions inside build-tool classes and shared libraries until thousands of pipelines become rigid, unauditable, and scary to change. Pipelattice turns that problem into **compilation of typed GitOps configuration**:

```text
Git (local repos + central catalog)
        │
        ▼
Configuration Compiler ──► Configuration Graph ──► Fleet queries (impact, drift)
        │
        ▼
ResolvedPipelinePlan (fingerprinted, provenance-tracked)
        │
        ▼
Runtime adapter (execution stays outside)
```

A company maintains a central catalog of profiles, policies, providers, environments and defaults. Each repository declares only its local intent — a happy-path `pipeline.yaml` fits in under 40 lines:

```yaml
apiVersion: pipelattice.dev/v1alpha1
kind: PipelineDefinition
metadata:
  name: payments-api
spec:
  profile:
    ref: catalog://profiles/java-maven-container@stable
  parameters:
    javaVersion: 21
```

## Design principles

- **Git is the only truth.** The graph and plans are rebuildable projections; there is no mutable database of configuration.
- **Typed end to end.** Errors surface at compile time; no `Map<String, Any?>` in public APIs.
- **Governance built in.** Central configuration can be `default` (overridable), `guardrail` (bounded) or `mandatory` (non-removable).
- **Explainable.** Every compiled value carries provenance: which file, which revision, which import put it there.
- **Capabilities, not tools.** Maven, Gradle or Helm are providers of capabilities (`project.build`, `image.build`, …); workflows compose capabilities.
- **Runtime-neutral core.** Usable from CLI, servers, IDEs, agents and runtimes — the domain never imports execution machinery.

## Project status

The project follows progressive milestones with explicit exit criteria (architecture harness first, functionality second).

| Milestone | Scope | Status |
|---|---|---|
| **M0 — Foundation & Architecture Harness** | Multi-module build, convention plugins, `explicitApi`, warnings-as-errors, identity types, diagnostics model, ArchUnit fitness rules, CI | ✅ Done |
| **M1 — Typed Resource Model** | Resource envelope, first resources, YAML parser adapter with positional diagnostics | ✅ Done |
| **M2 — Composition Compiler (slice)** | Imports resolution + parameter overlay + replace-style merge + provenance + `explain(path)`. Subset §4 (3/9 capas) + §7 (3/6 reglas). UAT-001/002/015 covered | ✅ Done |
| M3+ | Policy engine, build vertical slice, reactive graph, fleet diff, bridge to runtime | ⏳ Planned |

## Building from source

Requirements: **JDK 21** (Gradle toolchain pins it) — everything else comes through the wrapper.

```bash
git clone https://github.com/Rubentxu/pipelattice.git
cd pipelattice
./gradlew build          # compile + unit tests + architecture fitness tests
```

Local verification is the gate: `./gradlew build` runs the same checks CI does.

## Modules

| Module | Purpose |
|---|---|
| `build-logic` | Convention plugins: JVM 21 toolchain, `explicitApi`, warnings-as-errors, JUnit platform |
| `foundation` | Identity value types and the diagnostics model with stable error codes |
| `testkit` | Shared test utilities (diagnostic sinks, future fakes) |
| `architecture-tests` | Architecture fitness rules enforced with ArchUnit |
| `resource-model` | Typed resource envelope, value classes for identity, governance and parameter types |
| `config-compiler` | YAML parse adapter (SnakeYAML Engine); only module that knows about YAML |
| `pipeline-compose` | Composition engine: imports resolution + parameter overlay + replace-style merge + provenance + `explain(path)`. Subset §4 (3/9 capas) + §7 (3/6 reglas). UAT-001/002/015 cubiertos. Enforces FARCH-011 (no YAML/JSON/GIT/serialization deps; bridges M1 via `ResourceParser` port only) |

Future modules (`workflow-model`, `capabilities-api`, `policy-engine`, `graph-projection`, providers, `cli`) follow the milestone order and are only registered when they contain real content.

## Documentation

- [`docs/adr/`](docs/adr/) — accepted architecture decision records (project boundary, Git-as-truth, layered configuration, capabilities, governance, provenance, secrets…)
- Engineering conventions live in the build logic itself: every PR requires tests, architecture tests, stable diagnostic codes for new failures, zero unjustified compiler warnings, and an ADR whenever a boundary changes.

## License

[MIT](LICENSE) © Rubentxu
