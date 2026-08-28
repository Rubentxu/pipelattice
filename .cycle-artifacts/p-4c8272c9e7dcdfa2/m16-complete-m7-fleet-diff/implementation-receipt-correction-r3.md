# Implementation Receipt — Correction Round 3
# Cycle: p-4c8272c9e7dcdfa2/m16-complete-m7-fleet-diff

## Root Cause

The composition pass inside `GitSnapshotRepository.runCompositionPass()` had TWO distinct bugs:

### Bug 1: Empty SourceDocument content in SimpleRepositoryCatalogSource (S1 root cause)
**File**: `fleet-diff/src/main/kotlin/dev/rubentxu/pipelattice/fleet/diff/repository/GitSnapshotRepository.kt`
**Lines**: ~240-256 (runCompositionPass catalog building)

The original code built `profileRefs` map using `SourceDocument("catalog://${it.metadata.name}", "")` — an EMPTY content string. When `ImportResolver.resolve()` called `parser.parse(source)` on these empty documents, the YAML parser failed and returned null, breaking the entire import chain.

**Fix**: Build the catalog from the ORIGINAL SourceDocument content (already parsed during the first pass). Derive catalog ref from source path: `"profiles/java.yaml"` → `"catalog://profiles/java"`.

```kotlin
// OLD (broken):
.associate {
    ResourceRef.parse("catalog://${it.metadata.name}") to
        SourceDocument("catalog://${it.metadata.name}", "")
}

// NEW (fixed):
val sourceByMetadataName = mutableMapOf<String, SourceDocument>()
for (source in sources) {
    val result = resourceParser.parse(source)
    for (resource in result.resources) {
        if (resource is PipelineProfileResource) {
            sourceByMetadataName[resource.metadata.name] = source
        }
    }
}
val profileRefs = parsedSources
    .filterIsInstance<PipelineProfileResource>()
    .mapNotNull { profile ->
        val originalSource = sourceByMetadataName[profile.metadata.name] ?: return@mapNotNull null
        val catalogRef = ResourceRef.parse(
            "catalog://${originalSource.path.removeSuffix(".yaml").removeSuffix(".yml")}"
        )
        catalogRef to originalSource
    }
    .toMap()
```

### Bug 2: Missing scheme validation in cache lookup (S6 root cause)
**File**: `fleet-diff/src/main/kotlin/dev/rubentxu/pipelattice/fleet/diff/repository/GitSnapshotRepository.kt`
**Lines**: ~168-171 (cache hit path)

The implementation returned cached snapshots without validating the fingerprint scheme. A v1 cache file would be returned even when v2 was expected, because v1 and v2 use the SAME cache key (`sha + inputHash`) but different fingerprint values.

**Fix**: On cache hit, verify the cached fingerprint matches expected v2 fingerprint. If not, treat as cache miss and recompute v2.

```kotlin
val cached = cache.get(cacheKey)
if (cached != null) {
    val expectedV2Fingerprint = sha256Hex("graph-content/v2:${sha}:${SnapshotDiskCache.computeInputHash(sources)}")
    if (cached.fingerprint.value == expectedV2Fingerprint) {
        return LoadedSnapshot(cached, sources)
    }
    // Cache miss: stale v1 cache or fingerprint mismatch — recompute v2
}
```

### Bug 3: Test YAML used PipelineProfile-style parameter declarations in PipelineDefinition (S1 test fix)
**File**: `fleet-diff/src/test/kotlin/dev/rubentxu/pipelattice/fleet/diff/repository/GitSnapshotRepositoryEdgesTest.kt`
**Lines**: ~59-72

The test YAML used parameter declarations (`type: integer, default: 21`) which are valid for PipelineProfile, but PipelineDefinition uses raw ParameterValues (`javaVersion: 21`).

**Fix**: Changed PipelineDefinition parameters to raw values.

---

## Fix Files

| File | Change |
|------|--------|
| `fleet-diff/src/main/kotlin/.../repository/GitSnapshotRepository.kt` | Fixed catalog ref derivation from source path; added scheme validation on cache hit; added `sha256Hex()` helper |
| `fleet-diff/src/test/kotlin/.../repository/GitSnapshotRepositoryEdgesTest.kt` | Fixed test YAML to use correct parameter syntax |
| `fleet-diff/src/test/kotlin/.../cli/CliRealYamlEdgesTest.kt` | NEW: S16 test verifying edges from real YAML |

---

## Tests Landed

| Test | Method | Status |
|------|--------|--------|
| S1 | `GitSnapshotRepositoryEdgesTest.load_emits_graph_with_edges_from_real_yamls` | ✅ PASS |
| S6 | `GitSnapshotRepositoryCacheInvalidationV2Test.v1_cache_files_are_not_returned_in_v2` | ✅ PASS |
| S16 | `CliRealYamlEdgesTest.e2e_real_yaml_pair_edges_in_json` | ✅ PASS |

---

## Test Results

```
:fleet-diff:test      — 75+ tests, all passing
:pipeline-compose:test — 115+ tests, all passing
:testkit:test         — all passing
:architecture-tests:test — all passing
```

---

## Author Forensics

```
82e096a rubentxu rubentxu@pipelattice.local
e09a73c rubentxu rubentxu@pipelattice.local
```

Both commits by `rubentxu <rubentxu@pipelattice.local>` — clean, no Co-Authored-By.

---

## Commits on Branch

```
c34d9cb feat(pipeline-compose): add createCompositionEngine factory for production use  (base)
82e096a fix(fleet-diff): validate fingerprint scheme on cache hit to auto-invalidate v1  (this round)
e09a73c test(fleet-diff): add S16 e2e test for edges from real YAML via repository  (this round)
```

Working tree: clean (`git status --porcelain` empty).

---

## Verification Command

```bash
./gradlew :fleet-diff:test :pipeline-compose:test :testkit:test :architecture-tests:test
# BUILD SUCCESSFUL
```
