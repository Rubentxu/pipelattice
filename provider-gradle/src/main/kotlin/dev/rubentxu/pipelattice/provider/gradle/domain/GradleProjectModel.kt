package dev.rubentxu.pipelattice.provider.gradle.domain

import java.nio.file.Path

/**
 * Project model for Gradle builds.
 *
 * Specialization of the [dev.rubentxu.pipelattice.build.domain.ProjectModel] placeholder
 * (which is `typealias ProjectModel = Any`). A-lite will add full Gradle structure
 * (settings.gradle.kts, multi-project hierarchy); A-min carries only the Gradle
 * version and root directory.
 *
 * ## Gap #2 note
 * `ProjectModel` is a `typealias = Any`, not a sealed hierarchy. This means
 * `GradleProjectModel` cannot explicitly declare `: ProjectModel` in its signature
 * (data class cannot extend `Any` explicitly). The relationship holds at runtime.
 * See gaps-report.md § Gap #2.
 *
 * @property gradleVersion The Gradle version string (e.g., `8.5`).
 * @property rootDir The root directory of the Gradle project.
 */
public data class GradleProjectModel(
    public val gradleVersion: String,
    public val rootDir: Path,
)
