# ADR-0021: YAML Adapter Engine — SnakeYAML Engine

## Status
Accepted

## Date
2026-08-26

## Context

M1 introduces the first parse-phase adapter that reads YAML configuration files.
We evaluated three libraries for the role:

| Library | Pros | Cons |
|---------|------|------|
| **SnakeYAML Engine 3.x** (`org.snakeyaml:snakeyaml-engine`) | Pure Java 8+, no external native deps, stable API, actively maintained | Slightly higher memory footprint than the 1.x branch |
| Jackson (with YAML extension) | Familiar JSON+ YAML | Adds a second parsing backend, heavier, complicates the dependency tree |
| KAML (Kotlin DSL) | Idiomatic Kotlin, good interop | Experimental, requires Kotlin 2.x serialization metadata |

## Decision

We adopt **SnakeYAML Engine 3.x** as the single YAML parsing backend.

The `snakeyaml-engine` library is declared **only** in `config-compiler/build.gradle.kts` and is the
sole consumer of its public API. The `resource-model` module has **no YAML dependency** (FARCH-010).

Rationale:
- SnakeYAML Engine is a dedicated YAML library with a stable API surface.
- It provides precise `Mark` positions (line/column) needed for actionable diagnostics.
- The dependency is encapsulated behind the adapter port (`ResourceParser`), keeping the domain
  model YAML-free.

## Consequences

### Positive
- Precise error locations in diagnostics.
- Single, focused dependency for the parse phase.
- No transitive coupling from `resource-model` to any YAML library.

### Negative
- The `config-compiler` module carries the full SnakeYAML Engine as an implementation detail.

## References
- FARCH-010: YAML isolation rule (`architecture-tests`)
- `config-compiler/src/main/kotlin/dev/rubentxu/pipelattice/compiler/parse/YamlResourceParser.kt`
