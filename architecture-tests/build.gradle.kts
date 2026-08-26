plugins {
    id("pipelattice.kotlin-jvm")
}

// Architecture fitness harness: analysis-only module; nothing published from here.
kotlin {
    explicitApi = null
}

dependencies {
    testImplementation(projects.foundation)
    testImplementation(projects.testkit)
    testImplementation(projects.resourceModel)
    testImplementation(projects.pipelineCompose)
    testImplementation(libs.archunit.junit5)
}

/**
 * FARCH-010 defensive scan: verify resource-model/build.gradle.kts does not contain
 * snakeyaml as a dependency token. This guards against reintroducing YAML library deps
 * at the build level, complementary to the ArchUnit bytecode check.
 */
val farch010DefensiveScan by tasks.registering {
    val buildFile = rootProject.file("resource-model/build.gradle.kts")
    inputs.file(buildFile)
    doLast {
        val content = buildFile.readText()
        check(!content.lowercase().contains("snakeyaml")) {
            "FARCH-010 DEFENSIVE SCAN FAILED: resource-model/build.gradle.kts must not contain " +
                "'snakeyaml'. ADR-0021 requires resource-model to be YAML-library-agnostic."
        }
        println("FARCH-010 defensive scan PASSED: no snakeyaml token in resource-model/build.gradle.kts")
    }
}

tasks.named("check") {
    dependsOn(farch010DefensiveScan)
}

/**
 * FARCH-011 defensive scan: verify pipeline-compose/build.gradle.kts does not contain
 * forbidden dependency tokens (snakeyaml, kaml, jackson, gson, jgit, serialization).
 * This guards against reintroducing YAML/JSON/GIT/serialization library deps
 * at the build level, complementary to the ArchUnit bytecode check.
 */
val farch011DefensiveScan by tasks.registering {
    val buildFile = rootProject.file("pipeline-compose/build.gradle.kts")
    inputs.file(buildFile)
    doLast {
        val content = buildFile.readText()
        val forbiddenTokens = listOf("snakeyaml", "kaml", "jackson", "gson", "jgit", "serialization")
        val foundTokens = forbiddenTokens.filter { content.lowercase().contains(it) }
        check(foundTokens.isEmpty()) {
            "FARCH-011 DEFENSIVE SCAN FAILED: pipeline-compose/build.gradle.kts must not contain " +
                "forbidden dependency tokens: ${foundTokens.joinToString()}. " +
                "FARCH-011 requires pipeline-compose to be YAML/JSON/GIT/serialization-library-agnostic."
        }
        println("FARCH-011 defensive scan PASSED: no forbidden tokens in pipeline-compose/build.gradle.kts")
    }
}

tasks.named("check") {
    dependsOn(farch011DefensiveScan)
}
