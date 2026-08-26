package dev.rubentxu.pipelattice.foundation.outcome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutcomeTest {

    // --- map ---

    @Test
    fun `map on Success preserves value`() {
        val outcome: Outcome<Int, String> = Outcome.Success(42)
        val result = outcome.map { v -> v * 2 }
        assertIs<Outcome.Success<Int>>(result)
        assertEquals(84, result.value)
    }

    @Test
    fun `map on Failure leaves reason untouched`() {
        val outcome: Outcome<Int, String> = Outcome.Failure("err")
        val result = outcome.map { v: Int -> v * 2 }
        assertIs<Outcome.Failure<String>>(result)
        assertEquals("err", result.reason)
    }

    // --- getOrNull ---

    @Test
    fun `getOrNull returns value on Success`() {
        val outcome: Outcome<Int, String> = Outcome.Success(42)
        assertEquals(42, outcome.getOrNull())
    }

    @Test
    fun `getOrNull returns null on Failure`() {
        val outcome: Outcome<Int, String> = Outcome.Failure("err")
        assertNull(outcome.getOrNull())
    }

    // --- getOrElse ---

    @Test
    fun `getOrElse returns value on Success`() {
        val outcome: Outcome<Int, String> = Outcome.Success(42)
        assertEquals(42, outcome.getOrElse(-1))
    }

    @Test
    fun `getOrElse returns default on Failure`() {
        val outcome: Outcome<Int, String> = Outcome.Failure("err")
        assertEquals(-1, outcome.getOrElse(-1))
    }

    // --- onSuccess ---

    @Test
    fun `onSuccess executes action on Success`() {
        val outcome: Outcome<Int, String> = Outcome.Success(42)
        var called = false
        val result = outcome.onSuccess { called = true }
        assertTrue(called)
        assertIs<Outcome.Success<Int>>(result)
        assertEquals(42, result.value)
    }

    @Test
    fun `onSuccess does not execute action on Failure`() {
        val outcome: Outcome<Int, String> = Outcome.Failure("err")
        var called = false
        val result = outcome.onSuccess { called = true }
        assertFalse(called)
        assertIs<Outcome.Failure<String>>(result)
    }

    // --- onFailure ---

    @Test
    fun `onFailure executes action on Failure`() {
        val outcome: Outcome<Int, String> = Outcome.Failure("err")
        var capturedReason: String? = null
        val result = outcome.onFailure { capturedReason = it }
        assertEquals("err", capturedReason)
        assertIs<Outcome.Failure<String>>(result)
    }

    @Test
    fun `onFailure does not execute action on Success`() {
        val outcome: Outcome<Int, String> = Outcome.Success(42)
        var called = false
        val result = outcome.onFailure { called = true }
        assertFalse(called)
        assertIs<Outcome.Success<Int>>(result)
    }

    // --- fold ---

    @Test
    fun `fold on Success applies onSuccess branch`() {
        val outcome: Outcome<Int, String> = Outcome.Success(21)
        val result = outcome.fold({ it * 2 }, { -1 })
        assertEquals(42, result)
    }

    @Test
    fun `fold on Failure applies onFailure branch`() {
        val outcome: Outcome<Int, String> = Outcome.Failure("err")
        val result = outcome.fold({ it * 2 }, { -1 })
        assertEquals(-1, result)
    }

    // --- type inference helpers ---

    @Test
    fun `Success is typed as Outcome of Nothing on failure type`() {
        val success: Outcome.Success<Int> = Outcome.Success(42)
        val outcome: Outcome<Int, String> = success
        assertIs<Outcome.Success<Int>>(outcome)
    }

    @Test
    fun `Failure is typed as Outcome of Nothing on success type`() {
        val failure: Outcome.Failure<String> = Outcome.Failure("err")
        val outcome: Outcome<Int, String> = failure
        assertIs<Outcome.Failure<String>>(outcome)
    }
}
