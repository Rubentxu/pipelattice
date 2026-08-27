plugins {
    id("pipelattice.kotlin-jvm")
}

dependencies {
    api(projects.foundation)
    testImplementation(kotlin("test"))
}
