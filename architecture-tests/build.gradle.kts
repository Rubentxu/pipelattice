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
    testImplementation(projects.fleetDiff)
    testImplementation(projects.releaseEngine)
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

/**
 * FARCH-016 defensive scan: verify fleet-diff/build.gradle.kts does not contain
 * forbidden dependency tokens (processbuilder, runtime.exec, exitprocess, getenv).
 * This guards against reintroducing process-execution deps at the build level,
 * complementary to the ArchUnit bytecode check.
 */
val farch016DefensiveScan by tasks.registering {
    val buildFile = rootProject.file("fleet-diff/build.gradle.kts")
    inputs.file(buildFile)
    doLast {
        val content = buildFile.readText()
        val forbiddenTokens = listOf("processbuilder", "runtime.exec", "exitprocess", "getenv")
        val foundTokens = forbiddenTokens.filter { content.lowercase().contains(it) }
        check(foundTokens.isEmpty()) {
            "FARCH-016 DEFENSIVE SCAN FAILED: fleet-diff/build.gradle.kts must not contain " +
                "forbidden dependency tokens: ${foundTokens.joinToString()}. " +
                "FARCH-016 requires :fleet-diff to consume ProcessRunner from :build-engine."
        }
        println("FARCH-016 defensive scan PASSED: no forbidden tokens in fleet-diff/build.gradle.kts")
    }
}

tasks.named("check") {
    dependsOn(farch016DefensiveScan)
}

/**
 * FARCH-017 defensive scan: verify release-engine/build.gradle.kts does not contain
 * forbidden dependency tokens (processbuilder, runtime.exec, exitprocess, getenv,
 * snakeyaml, kaml, jackson, gson, jgit, serialization, etc.).
 * This guards against reintroducing forbidden deps at the build level.
 */
val releaseEngineDefensiveScan by tasks.registering {
    val buildFile = rootProject.file("release-engine/build.gradle.kts")
    inputs.file(buildFile)
    doLast {
        val content = buildFile.readText()
        val forbiddenTokens = listOf(
            "processbuilder", "runtime.exec", "exitprocess", "getenv",
            "snakeyaml", "kaml", "jackson", "gson", "jgit", "serialization",
            "com.google.inject", "org.springframework",
            "javax.inject", "jakarta.inject", "kotlin.reflect",
        )
        val foundTokens = forbiddenTokens.filter { content.lowercase().contains(it) }
        check(foundTokens.isEmpty()) {
            "FARCH-017 DEFENSIVE SCAN FAILED: release-engine/build.gradle.kts must not contain " +
                "forbidden dependency tokens: ${foundTokens.joinToString()}. " +
                "FARCH-017 requires :release-engine to be dependency-isolated."
        }
        println("FARCH-017 defensive scan PASSED: no forbidden tokens in release-engine/build.gradle.kts")
    }
}

tasks.named("check") {
    dependsOn(releaseEngineDefensiveScan)
}

/**
 * FARCH-018 secret isolation scan: walk release-engine/src/main/kotlin/**/*.kt files
 * and reject any string literal matching curated secret-shaped patterns.
 */
val releaseEngineSecretIsolationScan by tasks.registering {
    val sourceDir = rootProject.file("release-engine/src/main/kotlin")
    inputs.dir(sourceDir)
    doLast {
        val secretPatterns = listOf(
            Regex("AKIA[0-9A-Z]{16}"),           // AWS access key
            Regex("ghp_[A-Za-z0-9]{36}"),        // GitHub PAT
            Regex("[A-Za-z0-9+/]{40,}="),         // base64 credential blob
        )

        val ktFiles = sourceDir.walkTopDown().filter { it.extension == "kt" }
        var violations = 0
        for (file in ktFiles) {
            val lines = file.readLines()
            for ((lineNo, line) in lines.withIndex()) {
                // Skip synthetic test markers and example placeholders
                if (line.contains("synthetic") || line.contains("secret://example")) continue
                for (pattern in secretPatterns) {
                    if (pattern.containsMatchIn(line)) {
                        println("FARCH-018 VIOLATION in ${file.relativeTo(sourceDir)}:$lineNo: ${line.trim()}")
                        violations++
                    }
                }
            }
        }
        check(violations == 0) {
            "FARCH-018 SECRET ISOLATION SCAN FAILED: found $violations secret-shaped literals in " +
                "release-engine/src/main/kotlin/**/*.kt"
        }
        println("FARCH-018 secret isolation scan PASSED: no secret-shaped literals in production code")
    }
}

tasks.named("check") {
    dependsOn(releaseEngineSecretIsolationScan)
}
