plugins {
    id("pipelattice.kotlin-jvm")
}

dependencies {
    api(projects.foundation)
    api(projects.graphProjection)
    testImplementation(projects.testkit)
    testImplementation(kotlin("test"))
}
