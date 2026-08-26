pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "pipelattice"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// M0/M1 — Foundation, Architecture Harness and first resource slice.
// New modules are added here as they gain real content (see docs/adr and pipelattice-spec/docs/08_ROADMAP.md).
include(":foundation")
include(":testkit")
include(":architecture-tests")
include(":resource-model")
include(":config-compiler")
// M2 — Composition core slice (PR#1: scaffold + diagnostic codes snapshot).
include(":pipeline-compose")
// M4 — Policy engine module (A-min: domain types, port, no-op impl, FARCH-012).
include(":policy-engine")
