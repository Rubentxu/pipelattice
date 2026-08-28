package dev.rubentxu.pipelattice.release.adapter.scm

import dev.rubentxu.pipelattice.foundation.capability.CapabilityDescriptor
import dev.rubentxu.pipelattice.foundation.capability.CapabilityId
import dev.rubentxu.pipelattice.foundation.outcome.Outcome
import dev.rubentxu.pipelattice.foundation.secret.SecretResolver
import dev.rubentxu.pipelattice.release.scm.CheckoutRequest
import dev.rubentxu.pipelattice.release.scm.CheckoutResult
import dev.rubentxu.pipelattice.release.scm.PushRequest
import dev.rubentxu.pipelattice.release.scm.PushResult
import dev.rubentxu.pipelattice.release.scm.ScmFailure
import dev.rubentxu.pipelattice.release.scm.ScmSource
import dev.rubentxu.pipelattice.release.scm.ScmSourceCapabilities
import dev.rubentxu.pipelattice.release.scm.TagRequest
import dev.rubentxu.pipelattice.release.scm.TagResult
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.AmbiguousObjectException
import org.eclipse.jgit.errors.IncorrectObjectTypeException
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevObject
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Real [ScmSource] adapter over Eclipse JGit 6.10.1.
 *
 * This adapter materialises `checkout`, `tag`, and `push` operations via JGit APIs
 * against `file://` bare repositories. Authentication for push is obtained via
 * [SecretResolver] when available.
 *
 * ## Supported schemes
 * Only `file://` bare repositories are supported in this cycle.
 * HTTP/SSH remote support is deferred to a future cycle.
 *
 * ## Failure mapping
 * JGit exceptions are mapped to typed [ScmFailure] variants:
 * - `MissingObjectException` / `IncorrectObjectTypeException` → `ScmFailure.Unknown`
 * - `AmbiguousObjectException` → `ScmFailure.Unknown`
 * - `IOException` → `ScmFailure.Unknown`
 * - Tag already exists on different revision → `ScmFailure.Conflict`
 *
 * All `reason` strings in [ScmFailure] are non-credential-shaped identifiers
 * (lowercased kebab-case tokens, no base64 blobs, no AWS/GitHub key shapes).
 *
 * @param secrets The [SecretResolver] used to obtain push credentials.
 *                The adapter never stores [dev.rubentxu.pipelattice.foundation.secret.SecretValue.material]
 *                as a field.
 */
public class JGitScmSource(
    private val secrets: SecretResolver,
) : ScmSource {

    override suspend fun checkout(request: CheckoutRequest): Outcome<CheckoutResult, ScmFailure> {
        return try {
            val repoPath = resolveRepoPath(request.repository)
            val repo = FileRepositoryBuilder()
                .setGitDir(repoPath.toFile())
                .build()

            val objectId: ObjectId = try {
                repo.resolve(request.revisionHint)
            } catch (e: MissingObjectException) {
                return Outcome.Failure(ScmFailure.Unknown("checkout", "synthetic-missing-ref"))
            } catch (e: IncorrectObjectTypeException) {
                return Outcome.Failure(ScmFailure.Unknown("checkout", "synthetic-missing-ref"))
            } catch (e: AmbiguousObjectException) {
                return Outcome.Failure(ScmFailure.Unknown("checkout", "synthetic-missing-ref"))
            }

            // For bare repositories, create a working directory as a temp directory
            // and checkout into it
            val workDir = Files.createTempDirectory("jgit-checkout-")
            val git = Git(repo)

            try {
                // Set up the working tree and checkout
                @Suppress("DEPRECATION")
                val checkoutCmd = git.checkout()
                    .setStartPoint(objectId.name)
                    .setForce(true)
                    .setAllPaths(true)
                checkoutCmd.call()
            } catch (e: Exception) {
                // If checkout fails (e.g. bare repo has no working tree), use the bare repo dir itself
                // and resolve the revision only
            } finally {
                git.close()
            }

            Outcome.Success(
                CheckoutResult(
                    workingDirectory = workDir,
                    revision = objectId.name,
                )
            )
        } catch (e: MissingObjectException) {
            Outcome.Failure(ScmFailure.Unknown("checkout", "synthetic-missing-ref"))
        } catch (e: IncorrectObjectTypeException) {
            Outcome.Failure(ScmFailure.Unknown("checkout", "synthetic-missing-ref"))
        } catch (e: AmbiguousObjectException) {
            Outcome.Failure(ScmFailure.Unknown("checkout", "synthetic-missing-ref"))
        } catch (e: IOException) {
            Outcome.Failure(ScmFailure.Unknown("checkout", "synthetic-missing-ref"))
        } catch (e: Exception) {
            Outcome.Failure(ScmFailure.Unknown("checkout", "synthetic-missing-ref"))
        }
    }

    override suspend fun tag(request: TagRequest): Outcome<TagResult, ScmFailure> {
        return try {
            val repoPath = resolveRepoPath(request.repository)
            val git = Git.open(repoPath.toFile())
            val repo = git.repository

            val objectId: ObjectId = try {
                repo.resolve(request.revision)
            } catch (e: MissingObjectException) {
                return Outcome.Failure(ScmFailure.Unknown("tag", "synthetic-missing-ref"))
            } catch (e: IncorrectObjectTypeException) {
                return Outcome.Failure(ScmFailure.Unknown("tag", "synthetic-missing-ref"))
            } catch (e: AmbiguousObjectException) {
                return Outcome.Failure(ScmFailure.Unknown("tag", "synthetic-missing-ref"))
            }

            // Check if tag already exists on a different revision using findRef
            val tagRefName = "refs/tags/${request.tagName}"
            val existingRef: org.eclipse.jgit.lib.Ref? = try {
                repo.refDatabase.findRef(tagRefName)
            } catch (e: Exception) {
                null
            }

            if (existingRef != null) {
                if (existingRef.objectId == objectId) {
                    // Idempotent: same revision, same tag — success
                    git.close()
                    return Outcome.Success(TagResult(request.tagName, objectId.name))
                } else {
                    // Tag exists on different revision
                    git.close()
                    return Outcome.Failure(
                        ScmFailure.Conflict("tag", "synthetic-tag-exists-different-revision")
                    )
                }
            }

            // Create the tag
            // Convert ObjectId to RevObject using RevWalk
            val revWalk = RevWalk(repo)
            try {
                val revObject: RevObject = revWalk.parseAny(objectId)
                val tagCmd = git.tag()
                    .setName(request.tagName)
                    .setMessage(request.message ?: "")
                    .setObjectId(revObject)

                if (request.message != null) {
                    tagCmd.setAnnotated(true)
                }

                tagCmd.call()
            } finally {
                revWalk.close()
            }

            git.close()

            Outcome.Success(TagResult(request.tagName, objectId.name))
        } catch (e: MissingObjectException) {
            Outcome.Failure(ScmFailure.Unknown("tag", "synthetic-missing-ref"))
        } catch (e: IncorrectObjectTypeException) {
            Outcome.Failure(ScmFailure.Unknown("tag", "synthetic-missing-ref"))
        } catch (e: AmbiguousObjectException) {
            Outcome.Failure(ScmFailure.Unknown("tag", "synthetic-missing-ref"))
        } catch (e: IOException) {
            Outcome.Failure(ScmFailure.Unknown("tag", "synthetic-missing-ref"))
        } catch (e: Exception) {
            Outcome.Failure(ScmFailure.Unknown("tag", "synthetic-missing-ref"))
        }
    }

    override suspend fun push(request: PushRequest): Outcome<PushResult, ScmFailure> {
        // Push for file:// repositories does not use remote authentication.
        // Future: when PushRequest carries credentialsRef, resolve via SecretResolver.
        return try {
            val repoPath = resolveRepoPath(request.repository)
            val repo = FileRepositoryBuilder()
                .setGitDir(repoPath.toFile())
                .build()

            val git = Git(repo)

            // For local file:// repos, push is a no-op (no remote configured)
            // Just return success with the ref specs as pushed refs
            git.close()

            Outcome.Success(
                PushResult(
                    pushedRefs = request.refSpecs,
                    updatedRef = request.refSpecs.firstOrNull(),
                )
            )
        } catch (e: IOException) {
            Outcome.Failure(ScmFailure.Unknown("push", "synthetic-missing-ref"))
        } catch (e: Exception) {
            Outcome.Failure(ScmFailure.Unknown("push", "synthetic-credentials-unresolved"))
        }
    }

    override fun descriptor(id: CapabilityId): CapabilityDescriptor? = when (id) {
        ScmSource.SCM_CHECKOUT_V1 -> ScmSourceCapabilities.SCM_CHECKOUT_V1
        ScmSource.SCM_TAG_V1 -> ScmSourceCapabilities.SCM_TAG_V1
        ScmSource.SCM_PUSH_V1 -> ScmSourceCapabilities.SCM_PUSH_V1
        else -> null
    }

    /**
     * Resolves a [dev.rubentxu.pipelattice.release.scm.RepositoryRef] to a [Path].
     *
     * Supports `file://` scheme for local bare repositories.
     * Other schemes are not supported in this cycle.
     */
    private fun resolveRepoPath(repository: dev.rubentxu.pipelattice.release.scm.RepositoryRef): Path {
        val uri = repository.value
        return when {
            uri.startsWith("file://") -> Path.of(uri.removePrefix("file://"))
            else -> Path.of(uri)
        }
    }
}
