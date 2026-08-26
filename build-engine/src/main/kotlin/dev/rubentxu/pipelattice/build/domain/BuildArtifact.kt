package dev.rubentxu.pipelattice.build.domain

/**
 * Identifies a built artifact produced by the build system.
 *
 * Mirrors the Maven GAV (Group, Artifact, Version) coordinate model.
 * [classifier] is optional and may be used to distinguish variants such as
 * `sources`, `javadoc`, or platform-specific classifiers.
 *
 * ## Example
 * ```kotlin
 * val artifact = BuildArtifact(
 *     group = "dev.rubentxu.pipelattice",
 *     name = "my-library",
 *     version = "1.0.0",
 *     classifier = "sources"
 * )
 * ```
 *
 * @property group Maven group id (reverse-domain package name).
 * @property name Maven artifact id.
 * @property version Semantic version string.
 * @property classifier Optional classifier for variant artifacts.
 */
public data class BuildArtifact(
    public val group: String,
    public val name: String,
    public val version: String,
    public val classifier: String? = null,
)
