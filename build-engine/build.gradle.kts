plugins {
    id("pipelattice.kotlin-jvm")
}

dependencies {
    api(projects.foundation)
    api(projects.resourceModel)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(projects.testkit)
    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
    testImplementation(libs.kotlinx.coroutines.core)
}
