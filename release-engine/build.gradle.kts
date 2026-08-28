plugins {
    id("pipelattice.kotlin-jvm")
}

dependencies {
    api(projects.foundation)
    implementation(libs.kotlinx.coroutines.core)
    // Contracts contain @Test invariant methods; JUnit Jupiter is available in main sources
    implementation(libs.junit5.jupiter)
    testImplementation(projects.testkit)
    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
    testImplementation(libs.kotlinx.coroutines.core)
}
