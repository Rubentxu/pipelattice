plugins {
    id("pipelattice.kotlin-jvm")
}

dependencies {
    api(projects.foundation)
    api(projects.buildEngine)
    api(projects.resourceModel)
    testImplementation(projects.testkit)
    testImplementation(projects.buildEngine)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.core)
}
