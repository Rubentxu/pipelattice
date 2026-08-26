package dev.rubentxu.pipelattice.foundation.outcome

/**
 * A discriminated union representing the result of an operation that may succeed or fail.
 *
 * This type is the foundational error-handling abstraction for the Pipelattice platform.
 * It models the common pattern where an operation can produce a value [S] or fail with
 * a reason [F], making error handling explicit and composable.
 *
 * ## Variants
 * - [Success] wraps the successful value produced by the operation.
 * - [Failure] wraps the reason why the operation could not complete.
 *
 * ## Usage
 * ```kotlin
 * fun parseConfig(input: String): Outcome<Config, String> {
 *     return if (input.isNotBlank()) {
 *         Outcome.Success(Config(input))
 *     } else {
 *         Outcome.Failure("Config input must not be blank")
 *     }
 * }
 *
 * when (val result = parseConfig("my-config")) {
 *     is Outcome.Success -> println("Loaded: ${result.value}")
 *     is Outcome.Failure -> println("Failed: ${result.reason}")
 * }
 * ```
 *
 * ## Extension functions
 * [map] transforms the success value while preserving failure.
 * [getOrNull] returns the value or null on failure.
 * [getOrElse] returns the value or a default on failure.
 * [onSuccess] executes an action only on success.
 * [onFailure] executes an action only on failure.
 * [fold] reduces the outcome to a single value.
 */
public sealed interface Outcome<out S, out F> {

    /**
     * Represents a successful outcome, carrying the produced value [s].
     */
    public data class Success<out S>(public val value: S) : Outcome<S, Nothing>

    /**
     * Represents a failed outcome, carrying the failure reason [reason].
     */
    public data class Failure<out F>(public val reason: F) : Outcome<Nothing, F>
}

/**
 * Transforms the success value of this outcome using [transform].
 *
 * If this outcome is [Outcome.Success], returns a new [Outcome.Success] containing
 * `transform(value)`. If it is [Outcome.Failure], returns it unchanged.
 *
 * ```kotlin
 * Outcome.Success(21).map { it * 2 }  // Outcome.Success(42)
 * Outcome.Failure("err").map { it * 2 } // Outcome.Failure("err")
 * ```
 */
public fun <S, F, R> Outcome<S, F>.map(transform: (S) -> R): Outcome<R, F> =
    when (this) {
        is Outcome.Success -> Outcome.Success(transform(value))
        is Outcome.Failure -> this
    }

/**
 * Returns the success value or `null` if this is a [Outcome.Failure].
 */
public fun <S, F> Outcome<S, F>.getOrNull(): S? =
    when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> null
    }

/**
 * Returns the success value or [default] if this is a [Outcome.Failure].
 */
public fun <S, F> Outcome<S, F>.getOrElse(default: S): S =
    when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> default
    }

/**
 * Executes [action] if this outcome is a [Outcome.Success], then returns this outcome unchanged.
 * Allows chaining side-effects on success without altering the outcome.
 */
public inline fun <S, F> Outcome<S, F>.onSuccess(action: (S) -> Unit): Outcome<S, F> {
    if (this is Outcome.Success) {
        action(value)
    }
    return this
}

/**
 * Executes [action] if this outcome is a [Outcome.Failure], then returns this outcome unchanged.
 * Allows chaining side-effects on failure without altering the outcome.
 */
public inline fun <S, F> Outcome<S, F>.onFailure(action: (F) -> Unit): Outcome<S, F> {
    if (this is Outcome.Failure) {
        action(reason)
    }
    return this
}

/**
 * Reduces this outcome to a single value by applying [onSuccess] to the success value
 * and [onFailure] to the failure reason.
 *
 * ```kotlin
 * Outcome.Success(21).fold({ it * 2 }, { -1 })  // 42
 * Outcome.Failure("err").fold({ it * 2 }, { -1 }) // -1
 * ```
 */
public inline fun <S, F, R> Outcome<S, F>.fold(
    onSuccess: (S) -> R,
    onFailure: (F) -> R,
): R = when (this) {
    is Outcome.Success -> onSuccess(value)
    is Outcome.Failure -> onFailure(reason)
}
