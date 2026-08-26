# Apply Phase Report — Cycle p-4c8272c9e7dcdfa2/m1-resource-yaml-slice

## Cycle Metadata
- **Branch**: `feat/m1-resource-yaml-slice`
- **HEAD**: `848af90`
- **Phase**: apply (correction round 1)
- **Date**: 2026-08-26

## Commits in Cycle (14 total)

| Sha | Message |
|-----|---------|
| 620b977 | feat(resource-model): land port + diagnostics wiring (M1 slice baseline) |
| 5e995c6 | test(fixtures): add positive golden fixtures for M1 parse |
| 4813d6b | test(fixtures): add negative fixtures for stable-codes regression |
| 0c6e670 | docs(adr): accept SnakeYAML Engine for YAML adapter (ADR-0021) |
| faf0bcc | test(resource-model): cover value-class invariants from REQ envelope + governance |
| 80c4efb | refactor(test): tighten resource-model assertions and group by REQ |
| 77cddc2 | test(config-compiler): cover yaml adapter REQ scenarios (M1 RED phase) |
| 47a5229 | feat(config-compiler): use Compose API instead of Load to get Node types |
| c9667e2 | test(config-compiler): fix YAML strings in adapter tests (use buildString) |
| 56190e6 | fix(config-compiler): correct bindSpecEntries call chain and handle YAML 1.1 null (~) |
| 80ee6b3 | fix(test): replace valid-YAML fixture with tab-indent that triggers RESOURCE-YAML-001 |
| 80b18a1 | feat(arch): add FARCH-010 resource-model adapter isolation rule |
| 1652169 | test(config-compiler): add FixtureDeterminismTest and DiagnosticCodesTest for REQ coverage |
| 848af90 | revert: undo temp FARCH-010 violation probe (resource-model independence preserved) |

## New Commits This Round (2)
- `1652169` — test(config-compiler): add FixtureDeterminismTest and DiagnosticCodesTest for REQ coverage
- `848af90` — revert: undo temp FARCH-010 violation probe (resource-model independence preserved)

## Correction Items Addressed

### 1. CRITICAL 9a85bdd4 — REQ-Golden-Fixture-Determinism
- **Status**: ✅ DONE
- **Artifact**: `config-compiler/src/test/kotlin/dev/rubentxu/pipelattice/compiler/parse/FixtureDeterminismTest.kt`
- **Tests**: 2 new tests parsing pipeline.yaml and java-maven-container-profile.yaml 100 times each
- **Verification**: Digest stable across all iterations, no diagnostics emitted

### 2. HIGH 7db0efc3 — REQ-Stable-Diagnostic-Codes
- **Status**: ✅ DONE
- **Artifact**: `config-compiler/src/test/kotlin/dev/rubentxu/pipelattice/compiler/parse/DiagnosticCodesTest.kt`
- **Coverage**: All 7 ParseErrorCodes exercised including RESOURCE-SCHEMA-001 (missing required property)
- **Tests**: 8 test cases covering all error code scenarios

### 3. MEDIUM 91019c7c — FARCH-010 RED Evidence
- **Status**: ✅ DONE
- **Artifact**: `.atl/cycles/.../farch010-red.txt`
- **Method**: Temporarily introduced snakeyaml dependency + usage in resource-model, captured FARCH-010 failure, then reverted
- **Evidence**: Rule correctly detects dependency violation (FAILED with java.lang.AssertionError)

### 4. CRITICAL e1476af44 — Close Apply Phase
- **Status**: ✅ DONE
- **Artifact**: `apply-checkpoint.json` and `apply-report.md`

## Test Summary
- **Total tests**: 17
- **Passed**: 17
- **Failed**: 0
- **Build status**: GREEN

## Deviations
- FARCH-010 RED capture required creating temporary production code (TempSnakeYamlUsage.kt) with actual snakeyaml import — `testImplementation` alone does not create bytecode dependencies that ArchUnit can detect
- The temporary code was reverted; rule unchanged

## Risks for Verify v2
1. **Fixture path sensitivity**: FixtureDeterminismTest relies on classpath resources at `/fixtures/positive/` — if fixtures are moved, tests will fail with clear error message
2. **Digest algorithm stability**: If ParseResult data class structure changes, digest will change — this is intentional as it detects any hidden state mutations
3. **ArchUnit classpath analysis**: FARCH-010 only detects actual bytecode dependencies — adding build dependencies without using them does not trigger the rule
