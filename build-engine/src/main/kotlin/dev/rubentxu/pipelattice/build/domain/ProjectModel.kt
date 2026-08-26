package dev.rubentxu.pipelattice.build.domain

/**
 * Sealed hierarchy of project models produced by provider inspectors.
 *
 * The [ProjectInspector] port uses the type parameter bound `<P : ProjectModel>`
 * to ensure that every inspector returns a type that belongs to this sealed hierarchy.
 * This enables exhaustive [when] expressions over project-model variants in
 * application code without needing an explicit `kind` discriminator field.
 *
 * @see ProjectInspector
 */
public sealed interface ProjectModel {

    /**
     * Marker variant for providers that do not expose a structured model.
     *
     * @param id A human-readable identifier for the project (e.g., the directory name).
     * @param description An optional free-text description of the project.
     */
    public data class Generic(
        public val id: String,
        public val description: String?,
    ) : ProjectModel

    /**
     * Specialised variant for Maven projects (GAV coordinates).
     *
     * @param id The unique project identifier (usually [groupId]:[artifactId]).
     * @param groupId Maven group coordinate.
     * @param artifactId Maven artifact coordinate.
     * @param version Maven version string (may be unresolved for dynamic versions).
     */
    public data class Maven(
        public val id: String,
        public val groupId: String,
        public val artifactId: String,
        public val version: String,
    ) : ProjectModel

    /**
     * Specialised variant for Gradle projects.
     *
     * @param id The unique project identifier (usually [group]:[name]).
     * @param group Gradle group (maps to Maven groupId).
     * @param name Gradle project name.
     * @param version Gradle project version string.
     */
    public data class Gradle(
        public val id: String,
        public val group: String,
        public val name: String,
        public val version: String,
    ) : ProjectModel
}

/**
 * Backwards-compatibility alias for external consumers that still reference
 * [ProjectModel] as [Any].
 *
 * @deprecated Use the sealed [ProjectModel] variants directly. This typealias
 *             will be removed in the next release once all consumers have migrated
 *             to the sealed hierarchy.
 */
@Deprecated(
    message = "ProjectModel is now a sealed interface. Use ProjectModel.Generic, " +
        "ProjectModel.Maven, or ProjectModel.Gradle instead of Any.",
    replaceWith = ReplaceWith("ProjectModel"),
    DeprecationLevel.WARNING,
)
public typealias AnyProjectModel = ProjectModel.Generic
