package io.github.linreal.cascade.editor.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure-logic coverage for the ZWSP-sentinel-guard classifier inside
 * `BackspaceAwareTextField`. The classifier decides whether a buffer change
 * represents a real Backspace-at-start (ZWSP was deleted) or an accidental
 * insertion before the sentinel (cursor was at raw position 0 when the user
 * typed). Misclassifying the second case used to convert the focused code
 * block to a paragraph the moment the user typed any character after a
 * "failed" Backspace press at the start.
 */
class SentinelGuardClassificationTest {

    private val zwsp = "\u200B"

    @Test
    fun `unchanged buffer with sentinel is no-op`() {
        val original = "${zwsp}hello"
        val new = "${zwsp}hello"

        assertEquals(SentinelGuardAction.NoOp, classifySentinelChange(original, new))
    }

    @Test
    fun `mid-text edit preserving sentinel is no-op`() {
        val original = "${zwsp}hello"
        val new = "${zwsp}heXllo"

        assertEquals(SentinelGuardAction.NoOp, classifySentinelChange(original, new))
    }

    @Test
    fun `sentinel deleted by backspace at start is DeletionAtStart`() {
        val original = "${zwsp}hello"
        val new = "hello"

        assertEquals(SentinelGuardAction.DeletionAtStart, classifySentinelChange(original, new))
    }

    @Test
    fun `selection across sentinel replaced by shorter text is DeletionAtStart`() {
        val original = "${zwsp}hello"
        val new = "x"

        assertEquals(SentinelGuardAction.DeletionAtStart, classifySentinelChange(original, new))
    }

    @Test
    fun `empty result after deleting sentinel-only buffer is DeletionAtStart`() {
        val original = zwsp
        val new = ""

        assertEquals(SentinelGuardAction.DeletionAtStart, classifySentinelChange(original, new))
    }

    @Test
    fun `single character typed before sentinel returns RestoreSentinel at index 1`() {
        val original = "${zwsp}hello"
        val new = "x${zwsp}hello"

        assertEquals(
            SentinelGuardAction.RestoreSentinel(zwspIndex = 1),
            classifySentinelChange(original, new),
        )
    }

    @Test
    fun `multi-character paste before sentinel returns RestoreSentinel at correct index`() {
        val original = "${zwsp}hello"
        val new = "abc${zwsp}hello"

        assertEquals(
            SentinelGuardAction.RestoreSentinel(zwspIndex = 3),
            classifySentinelChange(original, new),
        )
    }

    @Test
    fun `insertion before sentinel into empty content returns RestoreSentinel`() {
        val original = zwsp
        val new = "x$zwsp"

        assertEquals(
            SentinelGuardAction.RestoreSentinel(zwspIndex = 1),
            classifySentinelChange(original, new),
        )
    }

    @Test
    fun `text length grew but sentinel missing entirely returns RestoreSentinel with -1 index`() {
        val original = ""
        val new = "x"

        assertEquals(
            SentinelGuardAction.RestoreSentinel(zwspIndex = -1),
            classifySentinelChange(original, new),
        )
    }

    @Test
    fun `same length but no sentinel falls into RestoreSentinel branch`() {
        // Edge case: replace operation that swaps the sentinel for another char
        // without changing length. Treat as restore (no spurious onBackspaceAtStart).
        val original = "${zwsp}hello"
        val new = "yhello"

        assertEquals(
            SentinelGuardAction.RestoreSentinel(zwspIndex = -1),
            classifySentinelChange(original, new),
        )
    }
}
