package dev.rubentxu.pipelattice.provider.gradle.domain

import dev.rubentxu.pipelattice.build.domain.ProjectModel

/**
 * Project model for Gradle builds.
 *
 * Delegates to [ProjectModel.Gradle] to eliminate field duplication and
 * maintain a single source of truth for the Gradle project model hierarchy.
 *
 * @property gradleVersion The Gradle version string (e.g., `8.5`).
 * @property rootDir The root directory of the Gradle project.
 */
public typealias GradleProjectModel = ProjectModel.Gradle
