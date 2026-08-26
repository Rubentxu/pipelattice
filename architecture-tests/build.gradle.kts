plugins {
    id("pipelattice.kotlin-jvm")
}

// Architecture fitness harness: analysis-only module; nothing published from here.
kotlin {
    explicitApi = null
}

dependencies {
    testImplementation(projects.foundation)
    testImplementation(projects.testkit)
    testImplementation(libs.archunit.junit5)
}
