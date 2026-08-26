plugins {
    id("pipelattice.kotlin-jvm")
}

// Parse phase home (pipelattice-spec/docs/01_ARCHITECTURE.md §5).
// This is an adapter module: it is the ONLY place that knows SnakeYAML.
dependencies {
    implementation(projects.foundation)
    implementation(projects.resourceModel)
    implementation(libs.snakeyaml.engine)

    testImplementation(kotlin("test"))
    testImplementation(projects.testkit)
}
