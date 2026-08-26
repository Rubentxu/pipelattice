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
    testImplementation(projects.policyEngine)
    testImplementation(projects.buildEngine)
    testImplementation(projects.providerGradle)
    testImplementation(projects.graphProjection)
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

/**
 * FARCH-012 defensive scan: verify policy-engine/build.gradle.kts does not contain
 * forbidden dependency tokens (snakeyaml, kaml, jackson, gson, jgit, serialization).
 * This guards against reintroducing YAML/JSON/GIT/serialization library deps
 * at the build level, complementary to the ArchUnit bytecode check.
 */
val farch012DefensiveScan by tasks.registering {
    val buildFile = rootProject.file("policy-engine/build.gradle.kts")
    inputs.file(buildFile)
    doLast {
        val content = buildFile.readText()
        val forbiddenTokens = listOf("snakeyaml", "kaml", "jackson", "gson", "jgit", "serialization")
        val foundTokens = forbiddenTokens.filter { content.lowercase().contains(it) }
        check(foundTokens.isEmpty()) {
            "FARCH-012 DEFENSIVE SCAN FAILED: policy-engine/build.gradle.kts must not contain " +
                "forbidden dependency tokens: ${foundTokens.joinToString()}. " +
                "FARCH-012 requires policy-engine to be YAML/JSON/GIT/serialization-library-agnostic."
        }
        println("FARCH-012 defensive scan PASSED: no forbidden tokens in policy-engine/build.gradle.kts")
    }
}

tasks.named("check") {
    dependsOn(farch012DefensiveScan)
}

/**
 * FARCH-013 defensive scan: verify build-engine/build.gradle.kts does not contain
 * forbidden dependency tokens (processbuilder, runtime.exec, exitprocess, getenv).
 * This guards against reintroducing process-execution deps at the build level,
 * complementary to the ArchUnit bytecode check.
 */
val farch013DefensiveScan by tasks.registering {
    val buildFile = rootProject.file("build-engine/build.gradle.kts")
    inputs.file(buildFile)
    doLast {
        val content = buildFile.readText()
        val forbiddenTokens = listOf("processbuilder", "runtime.exec", "exitprocess", "getenv")
        val foundTokens = forbiddenTokens.filter { content.lowercase().contains(it) }
        check(foundTokens.isEmpty()) {
            "FARCH-013 DEFENSIVE SCAN FAILED: build-engine/build.gradle.kts must not contain " +
                "forbidden dependency tokens: ${foundTokens.joinToString()}. " +
                "FARCH-013 requires build-engine to be process-execution-agnostic."
        }
        println("FARCH-013 defensive scan PASSED: no forbidden tokens in build-engine/build.gradle.kts")
    }
}

tasks.named("check") {
    dependsOn(farch013DefensiveScan)
}

/**
 * FARCH-014 defensive scan: verify provider-gradle/build.gradle.kts does not contain
 * forbidden dependency tokens (processbuilder, runtime.exec, exitprocess, getenv).
 * This guards against reintroducing process-execution deps at the build level,
 * complementary to the ArchUnit bytecode check.
 */
val farch014DefensiveScan by tasks.registering {
    val buildFile = rootProject.file("provider-gradle/build.gradle.kts")
    inputs.file(buildFile)
    doLast {
        val content = buildFile.readText()
        val forbiddenTokens = listOf("processbuilder", "runtime.exec", "exitprocess", "getenv")
        val foundTokens = forbiddenTokens.filter { content.lowercase().contains(it) }
        check(foundTokens.isEmpty()) {
            "FARCH-014 DEFENSIVE SCAN FAILED: provider-gradle/build.gradle.kts must not contain " +
                "forbidden dependency tokens: ${foundTokens.joinToString()}. " +
                "FARCH-014 requires :provider-gradle to consume ProcessRunner from :build-engine."
        }
        println("FARCH-014 defensive scan PASSED: no forbidden tokens in provider-gradle/build.gradle.kts")
    }
}

tasks.named("check") {
    dependsOn(farch014DefensiveScan)
}

/**
 * FARCH-015 defensive scan: verify graph-projection/build.gradle.kts does not contain
 * forbidden dependency tokens (jgrapht, neo4j, hibernate, room, orientdb, jpa).
 * This guards against reintroducing graph-database or ORM deps at the build level,
 * complementary to the ArchUnit bytecode check.
 */
val farch015DefensiveScan by tasks.registering {
    val buildFile = rootProject.file("graph-projection/build.gradle.kts")
    inputs.file(buildFile)
    doLast {
        val content = buildFile.readText()
        val forbiddenTokens = listOf(
            "jgrapht", "neo4j", "tinkerpop", "orientdb", "orient",
            "hibernate", "room", "jpa", "persistence",
        )
        val foundTokens = forbiddenTokens.filter { content.lowercase().contains(it) }
        check(foundTokens.isEmpty()) {
            "FARCH-015 DEFENSIVE SCAN FAILED: graph-projection/build.gradle.kts must not " +
                "contain forbidden dependency tokens: ${foundTokens.joinToString()}. " +
                "FARCH-015 requires :graph-projection to be in-memory only (V1, ADR-0014)."
        }
        println("FARCH-015 defensive scan PASSED: no forbidden tokens in graph-projection/build.gradle.kts")
    }
}

tasks.named("check") {
    dependsOn(farch015DefensiveScan)
}
