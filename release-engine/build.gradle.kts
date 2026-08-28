plugins {
    id("pipelattice.kotlin-jvm")
}

dependencies {
    api(projects.foundation)
    implementation(libs.kotlinx.coroutines.core)
    // Contracts contain @Test invariant methods; consumers need only the JUnit Jupiter API annotations
    api(libs.junit5.jupiter.api)
    testImplementation(projects.testkit)
    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
    testImplementation(libs.kotlinx.coroutines.core)
}
