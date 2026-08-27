plugins {
    id("pipelattice.kotlin-jvm")
    application
}

application {
    mainClass.set("dev.rubentxu.pipelattice.fleet.diff.cli.MainKt")
}

dependencies {
    api(projects.foundation)
    api(projects.graphProjection)
    testImplementation(projects.testkit)
    testImplementation(kotlin("test"))
}
