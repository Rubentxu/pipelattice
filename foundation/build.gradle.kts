plugins {
    id("pipelattice.kotlin-jvm")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.core)
}
