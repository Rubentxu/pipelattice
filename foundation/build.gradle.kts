plugins {
    id("pipelattice.kotlin-jvm")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
    testImplementation(libs.kotlinx.coroutines.core)
}
