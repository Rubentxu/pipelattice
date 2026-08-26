package dev.rubentxu.pipelattice.provider.gradle.domain

import dev.rubentxu.pipelattice.build.domain.BuildArtifact

/**
 * Build artifact for Gradle builds.
 *
 * Gradle uses GAV-like coordinates (group:name:version) for publications,
 * mapped to Maven repository layout by default. [GradleBuildArtifact] is a
 * specialization of [BuildArtifact] for Gradle-sourced artifacts.
 *
 * @property group Maven group id (reverse-domain package name).
 * @property name Maven artifact id.
 * @property version Semantic version string.
 * @property classifier Optional classifier for variant artifacts (e.g., `sources`, `javadoc`).
 */
public class GradleBuildArtifact(
    group: String,
    name: String,
    version: String,
    classifier: String? = null,
) : BuildArtifact(group, name, version, classifier)
