plugins {
    id("pipelattice.kotlin-jvm")
}

/**
 * Production adapters for `:release-engine` ports.
 *
 * Provides real implementations of the three `:release-engine` ports:
 * - [dev.rubentxu.pipelattice.release.adapter.scm.JGitScmSource] — JGit-based ScmSource
 * - [dev.rubentxu.pipelattice.release.adapter.secret.EnvSecretResolver] — Environment-variable SecretResolver
 * - [dev.rubentxu.pipelattice.release.adapter.artifact.LocalFSArtifactRepository] — Local-filesystem ArtifactRepository
 * - [dev.rubentxu.pipelattice.release.adapter.release.GitTagBasedReleaseManager] — Git-tag-based ReleaseManager
 *
 * ## Architecture
 * This module is the vendor adapter layer. It depends on `:release-engine` (port interfaces)
 * and `:foundation` (SecretResolver, SecretRef, Outcome). It uses JGit 6.10.1 directly
 * (not via `:fleet-diff` — FARCH-017 forbids that dependency).
 *
 * ## FARCH-017 / FARCH-018
 * This module is NOT subject to `releaseEngineDefensiveScan` (which forbids `jgit` in
 * `:release-engine`). Instead it carries `releaseEngineAdaptersDefensiveScan` with a
 * reduced token list. FARCH-018 source-scan is broadened to cover this module's sources.
 *
 * ## Wiring
 * The [dev.rubentxu.pipelattice.release.adapter.wiring.ReleaseEngineWiring] object
 * provides the composition root for the four adapters.
 */
kotlin {
    // explicitApi is inherited from pipelattice.kotlin-jvm convention plugin
}

dependencies {
    api(projects.releaseEngine)
    api(projects.foundation)
    implementation(libs.jgit)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(projects.releaseEngine)
    testImplementation(projects.testkit)
    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
}
