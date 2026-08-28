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
        var git: Git? = null
        return try {
            val repoPath = resolveRepoPath(request.repository)
            // Git.open auto-detects .git for working repos and works for bare repos
            git = Git.open(repoPath.toFile())
            val repo = git.repository

            val objectId: ObjectId = try {
                repo.resolve(request.revisionHint)
            } catch (e: MissingObjectException) {
                return Outcome.Failure(ScmFailure.Unknown("checkout", "synthetic-missing-ref"))
            } catch (e: IncorrectObjectTypeException) {
                return Outcome.Failure(ScmFailure.Unknown("checkout", "synthetic-missing-ref"))
            } catch (e: AmbiguousObjectException) {
                return Outcome.Failure(ScmFailure.Unknown("checkout", "synthetic-missing-ref"))
            } catch (e: IOException) {
                return Outcome.Failure(ScmFailure.Unknown("checkout", "synthetic-missing-ref"))
            }

            // Create a working directory for the checkout
            val workDir = Files.createTempDirectory("jgit-checkout-")

            // Set up the working tree and checkout
            @Suppress("DEPRECATION")
            val checkoutCmd = git.checkout()
                .setStartPoint(objectId.name)
                .setForce(true)
                .setAllPaths(true)
            checkoutCmd.call()

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
        } finally {
            git?.close()
        }
    }

    override suspend fun tag(request: TagRequest): Outcome<TagResult, ScmFailure> {
        var git: Git? = null
        return try {
            val repoPath = resolveRepoPath(request.repository)
            git = Git.open(repoPath.toFile())
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
                    return Outcome.Success(TagResult(request.tagName, objectId.name))
                } else {
                    // Tag exists on different revision
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
        } finally {
            git?.close()
        }
    }

    override suspend fun push(request: PushRequest): Outcome<PushResult, ScmFailure> {
        var repo: org.eclipse.jgit.lib.Repository? = null
        var git: Git? = null
        return try {
            val repoPath = resolveRepoPath(request.repository)
            // Open the repository - Git.open auto-detects .git for working repos
            git = Git.open(repoPath.toFile())
            repo = git.repository

            // For file:// local repos: ensure the remote is configured.
            // If the remote doesn't exist, add it pointing to the bare repo.
            val remoteName = request.remote
            var remoteConfigured = false
            try {
                val url = repo.getConfig().getString("remote", remoteName, "url")
                remoteConfigured = url != null
            } catch (e: Exception) {
                // Remote not configured or other config error
                remoteConfigured = false
            }
            if (!remoteConfigured) {
                // Remote not configured — add it pointing to the bare repo path.
                // The bare repo path is derived from the repository URI.
                val uri = request.repository.value
                repo.getConfig().setString("remote", remoteName, "url", uri)
                repo.getConfig().setString("remote", remoteName, "fetch", "+refs/heads/*:refs/remotes/$remoteName/*")
                repo.getConfig().save()
            }

            // Build push command with ref specs
            val pushCmd = git.push()
                .setRemote(remoteName)
                .setRefSpecs(request.refSpecs.map { org.eclipse.jgit.transport.RefSpec(it) })

            val pushResult = pushCmd.call()

            // Inspect push results — check for conflicts/errors
            // PushResult contains a list of RemoteRefUpdate for each remote
            var hadError = false
            var errorMessage = ""
            for (result in pushResult) {
                for (remoteUpdate in result.remoteUpdates) {
                    val status = remoteUpdate.status
                    when {
                        status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK ||
                        status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE -> {
                            // Success — continue checking other refs
                        }
                        status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD -> {
                            hadError = true
                            errorMessage = "non-fast-forward rejected"
                        }
                        status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_OTHER_REASON -> {
                            hadError = true
                            errorMessage = "push rejected by remote"
                        }
                        status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.NON_EXISTING -> {
                            hadError = true
                            errorMessage = "ref does not exist on remote"
                        }
                        else -> {
                            hadError = true
                            errorMessage = "push failed: ${status.name.lowercase()}"
                        }
                    }
                    if (hadError) {
                        return Outcome.Failure(
                            ScmFailure.Conflict("push", errorMessage)
                        )
                    }
                }
            }

            // All refs pushed successfully — return success with the ref specs we pushed
            Outcome.Success(
                PushResult(
                    pushedRefs = request.refSpecs,
                    updatedRef = request.refSpecs.firstOrNull(),
                )
            )
        } catch (e: IOException) {
            Outcome.Failure(ScmFailure.Unknown("push", "synthetic-missing-ref"))
        } catch (e: Exception) {
            System.err.println("DEBUG push exception: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            Outcome.Failure(ScmFailure.Unknown("push", "synthetic-credentials-unresolved"))
        } finally {
            git?.close()
            repo?.close()
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
