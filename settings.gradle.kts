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

// M0 — Foundation & Architecture Harness.
// New modules are added here as they gain real content (see docs/adr and pipelattice-spec/docs/08_ROADMAP.md).
include(":foundation")
include(":testkit")
include(":architecture-tests")
