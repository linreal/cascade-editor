package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.ui.isDropAtOriginalPosition
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [isDropAtOriginalPosition] — pure function that decides whether a
 * drag gesture ended at the block's original position (selection) or at a
 * different position (actual move).
 */
class DragSelectionTest {

    @Test
    fun `null target index means no movement - select`() {
        assertTrue(isDropAtOriginalPosition(targetIndex = null, originalIndex = 3))
    }

    @Test
    fun `target equals original index - select`() {
        assertTrue(isDropAtOriginalPosition(targetIndex = 2, originalIndex = 2))
    }

    @Test
    fun `target equals original index plus one - select`() {
        assertTrue(isDropAtOriginalPosition(targetIndex = 3, originalIndex = 2))
    }

    @Test
    fun `target before original - not select`() {
        assertFalse(isDropAtOriginalPosition(targetIndex = 0, originalIndex = 2))
    }

    @Test
    fun `target after original plus one - not select`() {
        assertFalse(isDropAtOriginalPosition(targetIndex = 4, originalIndex = 2))
    }

    @Test
    fun `first block dropped at gap zero - select`() {
        assertTrue(isDropAtOriginalPosition(targetIndex = 0, originalIndex = 0))
    }

    @Test
    fun `first block dropped at gap one - select`() {
        assertTrue(isDropAtOriginalPosition(targetIndex = 1, originalIndex = 0))
    }

    @Test
    fun `first block dropped at gap two - not select`() {
        assertFalse(isDropAtOriginalPosition(targetIndex = 2, originalIndex = 0))
    }
}
