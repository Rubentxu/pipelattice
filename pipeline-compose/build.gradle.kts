plugins {
    id("pipelattice.kotlin-jvm")
}

dependencies {
    api(projects.foundation)
    api(projects.graphProjection)
    api(projects.resourceModel)
    testImplementation(projects.testkit)
    testImplementation(kotlin("test"))
}
