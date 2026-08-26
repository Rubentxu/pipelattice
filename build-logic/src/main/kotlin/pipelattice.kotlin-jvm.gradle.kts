import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

group = "dev.rubentxu.pipelattice"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    explicitApi()

    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        // Bootstrap rule: no compiler warning without justification (see pipelattice-spec/docs/14_BOOTSTRAP.md).
        allWarningsAsErrors = true
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Inner-loop speed: distribute tests across JVMs (TDD iterations stay fast as suites grow).
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    // Anti-hang net: no test blocks the suite forever (integration/process tests later on).
    systemProperty("junit.jupiter.execution.timeout.default", "1m")
    testLogging {
        events("failed")
        showExceptions = true
        showStackTraces = true
    }
}

// Tag contract: unit tests are untagged and run in the default `test` task;
// slow/integration tests MUST be tagged "slow" and run via `slowTest`.
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("slow")
    }
}

tasks.register<Test>("slowTest") {
    useJUnitPlatform {
        includeTags("slow")
    }
    testLogging {
        events("passed", "failed")
    }
}
