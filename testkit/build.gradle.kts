plugins {
    id("pipelattice.kotlin-jvm")
}

dependencies {
    api(projects.foundation)
    api(projects.resourceModel)
    testImplementation(kotlin("test"))
}
