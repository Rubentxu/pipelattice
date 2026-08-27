package dev.rubentxu.pipelattice.testkit

/**
 * Generates a deterministic synthetic fleet of YAML source documents.
 *
 * Produces [projectCount] projects, each with [profilesPerProject] profiles,
 * where each profile has [importsPerProfile] imports. Generation is deterministic
 * based on the [seed], ensuring byte-identical output across runs.
 *
 * ## Output structure
 * - `projects/project-{N}/pipeline.yaml` — PipelineDefinitionResource
 * - `projects/project-{N}/profiles/profile-{M}.yaml` — PipelineProfileResource
 * - Each profile imports `projects/project-{K}/profiles/profile-{L}.yaml` based on deterministic selection
 *
 * @param seed Random seed for deterministic generation.
 * @param profilesPerProject Number of profiles per project.
 * @param importsPerProfile Number of imports per profile.
 */
public class SyntheticFleetGenerator(
    public val seed: Long = 42L,
    public val profilesPerProject: Int = 2,
    public val importsPerProfile: Int = 3,
) {

    /**
     * Generates [projectCount] projects as [SourceDocument]s.
     *
     * @param projectCount The number of projects to generate.
     * @return A list of [SourceDocument] representing the generated fleet.
     */
    public fun generate(projectCount: Int): List<SourceDocument> {
        val documents = mutableListOf<SourceDocument>()
        val random = kotlin.random.Random(seed)

        for (projectIdx in 0 until projectCount) {
            val projectPath = "projects/project-$projectIdx"

            // Generate pipeline definition
            val pipelineContent = """
                |apiVersion: pipelattice.io/v1
                |kind: PipelineDefinition
                |metadata:
                |  name: pipeline-$projectIdx
                |spec:
                |  profile: $projectPath/profiles/profile-0.yaml
                |  parameters:
                |    version:
                |      type: string
                |      default: "1.0.0"
                |""".trimMargin()

            documents.add(SourceDocument("$projectPath/pipeline.yaml", pipelineContent))

            // Generate profiles
            for (profileIdx in 0 until profilesPerProject) {
                val profilePath = "$projectPath/profiles/profile-$profileIdx.yaml"

                // Select imports deterministically
                val imports = mutableListOf<String>()
                for (importIdx in 0 until importsPerProfile) {
                    // Select a random project (could be same project)
                    val importedProject = random.nextInt(projectCount)
                    val importedProfile = random.nextInt(profilesPerProject)
                    imports.add("projects/project-$importedProject/profiles/profile-$importedProfile.yaml")
                }

                val profileContent = buildString {
                    appendLine("apiVersion: pipelattice.io/v1")
                    appendLine("kind: PipelineProfile")
                    appendLine("metadata:")
                    appendLine("  name: profile-$projectIdx-$profileIdx")
                    appendLine("spec:")
                    if (imports.isNotEmpty()) {
                        appendLine("  imports:")
                        for (imp in imports) {
                            appendLine("    - $imp")
                        }
                    }
                    appendLine("  parameters:")
                    appendLine("    env:")
                    appendLine("      type: string")
                    appendLine("      default: \"dev\"")
                }

                documents.add(SourceDocument(profilePath, profileContent))
            }
        }

        return documents
    }
}

/**
 * A source document with a path and content.
 * This is a local type in testkit to avoid architecture coupling with resource-model.
 * The content is valid YAML that parses via YamlResourceParser.
 */
public data class SourceDocument(
    public val path: String,
    public val content: String,
)
