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
    api(projects.buildEngine)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(projects.testkit)
    testImplementation(kotlin("test"))
}
