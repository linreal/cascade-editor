package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.ui.utils.convertVisualGapToMoveBlocksIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for pure drag utility functions that don't depend on Compose UI types.
 *
 * Note: [calculateDropTargetIndex] and [calculateDropIndicatorY] depend on
 * [LazyListLayoutInfo] and require compose-foundation in test dependencies
 * to create fakes. They should be tested via UI/integration tests or after
 * adding the compose-foundation test dependency.
 */
class DragUtilsTest {

    // =========================================================================
    // convertVisualGapToMoveBlocksIndex
    // =========================================================================

    // List: [A(0), B(1), C(2), D(3), E(4)] — 5 items

    @Test
    fun `dropping at original position returns null`() {
        // Dragging item at index 2, dropping at gap 2 (before item 2) = same position
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 2, originalIndex = 2, totalItemCount = 5))
    }

    @Test
    fun `dropping immediately after original position returns null`() {
        // Dragging item at index 2, dropping at gap 3 (after item 2) = same position
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 3, originalIndex = 2, totalItemCount = 5))
    }

    @Test
    fun `dropping before original position returns gap directly`() {
        // Dragging item at index 3, dropping at gap 1 (between A and B)
        // After removing D: [A, B, C, E] → insert at index 1 → [A, D, B, C, E]
        assertEquals(1, convertVisualGapToMoveBlocksIndex(visualGap = 1, originalIndex = 3, totalItemCount = 5))
    }

    @Test
    fun `dropping at start returns 0`() {
        // Dragging item at index 3, dropping at gap 0 (before everything)
        assertEquals(0, convertVisualGapToMoveBlocksIndex(visualGap = 0, originalIndex = 3, totalItemCount = 5))
    }

    @Test
    fun `dropping after original position adjusts for removal`() {
        // Dragging item at index 1, dropping at gap 4 (between D and E)
        // After removing B: [A, C, D, E] → insert at index 3 → [A, C, D, B, E]
        assertEquals(3, convertVisualGapToMoveBlocksIndex(visualGap = 4, originalIndex = 1, totalItemCount = 5))
    }

    @Test
    fun `dropping at end after original position`() {
        // Dragging item at index 1, dropping at gap 5 (after everything)
        // After removing B: [A, C, D, E] → insert at index 4 → [A, C, D, E, B]
        assertEquals(4, convertVisualGapToMoveBlocksIndex(visualGap = 5, originalIndex = 1, totalItemCount = 5))
    }

    @Test
    fun `dropping at end when already last returns null`() {
        // Dragging last item (index 4), dropping at gap 5 (after everything) = same position
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 5, originalIndex = 4, totalItemCount = 5))
    }

    @Test
    fun `dropping at start when already first returns null`() {
        // Dragging first item (index 0), dropping at gap 0 = same position
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 0, originalIndex = 0, totalItemCount = 5))
    }

    @Test
    fun `dropping first item at gap 1 returns null`() {
        // Gap 1 is immediately after item 0 — no movement
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 1, originalIndex = 0, totalItemCount = 5))
    }

    @Test
    fun `dropping last item at gap N-1 returns null`() {
        // Dragging item 4, gap 4 = before item 4 = same position
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 4, originalIndex = 4, totalItemCount = 5))
    }

    @Test
    fun `two item list - swap forward`() {
        // [A(0), B(1)] — drag A to after B
        // Gap 2 (after B), original 0 → adjust: 2-1=1 → [B, A]
        assertEquals(1, convertVisualGapToMoveBlocksIndex(visualGap = 2, originalIndex = 0, totalItemCount = 2))
    }

    @Test
    fun `two item list - swap backward`() {
        // [A(0), B(1)] — drag B to before A
        // Gap 0, original 1 → no adjust: 0 → [B, A]
        assertEquals(0, convertVisualGapToMoveBlocksIndex(visualGap = 0, originalIndex = 1, totalItemCount = 2))
    }

    @Test
    fun `result is clamped to valid range`() {
        // Edge case: gap beyond total count
        val result = convertVisualGapToMoveBlocksIndex(visualGap = 10, originalIndex = 0, totalItemCount = 5)
        // 10 > 0, so adjusted = 9, clamped to 4 (totalItemCount - 1)
        assertEquals(4, result)
    }

    @Test
    fun `single item list returns null for any gap`() {
        // Only one item — can't move anywhere
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 0, originalIndex = 0, totalItemCount = 1))
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 1, originalIndex = 0, totalItemCount = 1))
    }

    // Verify specific scenarios end-to-end:
    // List [A, B, C, D, E], drag C (index=2)

    @Test
    fun `drag C to before A`() {
        // Gap 0, original 2 → result 0 → [C, A, B, D, E]
        assertEquals(0, convertVisualGapToMoveBlocksIndex(visualGap = 0, originalIndex = 2, totalItemCount = 5))
    }

    @Test
    fun `drag C to between A and B`() {
        // Gap 1, original 2 → result 1 → [A, C, B, D, E]
        assertEquals(1, convertVisualGapToMoveBlocksIndex(visualGap = 1, originalIndex = 2, totalItemCount = 5))
    }

    @Test
    fun `drag C stays at C position`() {
        // Gap 2 or 3, original 2 → null (no movement)
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 2, originalIndex = 2, totalItemCount = 5))
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 3, originalIndex = 2, totalItemCount = 5))
    }

    @Test
    fun `drag C to between D and E`() {
        // Gap 4, original 2 → adjusted 4-1=3 → [A, B, D, C, E]
        assertEquals(3, convertVisualGapToMoveBlocksIndex(visualGap = 4, originalIndex = 2, totalItemCount = 5))
    }

    @Test
    fun `drag C to after E`() {
        // Gap 5, original 2 → adjusted 5-1=4 → [A, B, D, E, C]
        assertEquals(4, convertVisualGapToMoveBlocksIndex(visualGap = 5, originalIndex = 2, totalItemCount = 5))
    }
}
