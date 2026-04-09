package io.github.linreal.cascade.editor

import androidx.compose.foundation.text.input.delete
import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.state.BlockTextStates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockTextStatesTest {

    private val textStates = BlockTextStates()

    // -- replaceVisibleRange --

    @Test
    fun `replaceVisibleRange replaces middle range keeping surrounding text`() {
        val id = BlockId.generate()
        textStates.getOrCreate(id, "Hello World")

        val result = textStates.replaceVisibleRange(id, 5, 11, " Kotlin")

        assertEquals("Hello Kotlin", result)
        assertEquals("Hello Kotlin", textStates.getVisibleText(id))
    }

    @Test
    fun `replaceVisibleRange deletes range when replacement is empty`() {
        val id = BlockId.generate()
        textStates.getOrCreate(id, "ab/query cd")

        val result = textStates.replaceVisibleRange(id, 2, 8, "")

        assertEquals("ab cd", result)
        assertEquals("ab cd", textStates.getVisibleText(id))
    }

    @Test
    fun `replaceVisibleRange at start of text`() {
        val id = BlockId.generate()
        textStates.getOrCreate(id, "/cmd rest")

        val result = textStates.replaceVisibleRange(id, 0, 4, "")

        assertEquals(" rest", result)
    }

    @Test
    fun `replaceVisibleRange at end of text`() {
        val id = BlockId.generate()
        textStates.getOrCreate(id, "text/cmd")

        val result = textStates.replaceVisibleRange(id, 4, 8, "")

        assertEquals("text", result)
    }

    @Test
    fun `replaceVisibleRange with insertion replacing entire text`() {
        val id = BlockId.generate()
        textStates.getOrCreate(id, "/all")

        val result = textStates.replaceVisibleRange(id, 0, 4, "replaced")

        assertEquals("replaced", result)
    }

    @Test
    fun `replaceVisibleRange returns null for missing block`() {
        val result = textStates.replaceVisibleRange(
            BlockId.generate(), 0, 1, "x"
        )
        assertNull(result)
    }

    @Test
    fun `replaceVisibleRange clamps out-of-bounds range`() {
        val id = BlockId.generate()
        textStates.getOrCreate(id, "abc")

        val result = textStates.replaceVisibleRange(id, -5, 100, "X")

        assertEquals("X", result)
    }

    @Test
    fun `replaceVisibleRange registers programmatic commit`() {
        val id = BlockId.generate()
        textStates.getOrCreate(id, "abc")

        textStates.replaceVisibleRange(id, 1, 2, "X")

        val commit = textStates.consumeProgrammaticCommit(id)
        assertNotNull(commit)
        assertEquals("aXc", commit)
    }

    @Test
    fun `replaceVisibleRange respects explicit cursor position`() {
        val id = BlockId.generate()
        textStates.getOrCreate(id, "Hello World")

        textStates.replaceVisibleRange(id, 5, 11, " Kotlin", cursorPositionAfter = 0)

        // Text is correct regardless of cursor
        assertEquals("Hello Kotlin", textStates.getVisibleText(id))
    }

    @Test
    fun `replaceVisibleRange defaults cursor to end of replacement`() {
        val id = BlockId.generate()
        textStates.getOrCreate(id, "ab/cmd ef")

        // Replace "/cmd" (range 2..6) with "" → "ab ef", cursor at 2
        textStates.replaceVisibleRange(id, 2, 6, "")

        assertEquals("ab ef", textStates.getVisibleText(id))
    }

    // -- selection --

    @Test
    fun `setSelection restores collapsed selection in visible coordinates`() {
        val id = BlockId.generate()
        textStates.getOrCreate(id, "Hello")

        textStates.setSelection(id, TextRange(3))

        assertEquals(TextRange(3, 3), textStates.getSelection(id))
    }

    @Test
    fun `setSelection restores ranged selection in visible coordinates`() {
        val id = BlockId.generate()
        textStates.getOrCreate(id, "Hello")

        textStates.setSelection(id, TextRange(1, 4))

        assertEquals(TextRange(1, 4), textStates.getSelection(id))
    }

    @Test
    fun `getSelection remains correct when sentinel is missing`() {
        val id = BlockId.generate()
        val state = textStates.getOrCreate(id, "Hello")

        state.edit {
            delete(0, 1)
            selection = TextRange(2, 4)
        }

        assertEquals("Hello", textStates.getVisibleText(id))
        assertEquals(TextRange(2, 4), textStates.getSelection(id))
    }

    @Test
    fun `setSelection uses raw visible offsets when sentinel is missing`() {
        val id = BlockId.generate()
        val state = textStates.getOrCreate(id, "Hello")

        state.edit {
            delete(0, 1)
            selection = TextRange(0)
        }

        textStates.setSelection(id, TextRange(1, 4))

        assertEquals(TextRange(1, 4), state.selection)
        assertEquals(TextRange(1, 4), textStates.getSelection(id))
    }

    // -- hasPendingProgrammaticCommit --

    @Test
    fun `hasPendingProgrammaticCommit returns true after setText`() {
        val id = BlockId.generate()
        textStates.getOrCreate(id, "abc")

        textStates.setText(id, "xyz")

        assertTrue(textStates.hasPendingProgrammaticCommit(id))
    }

    @Test
    fun `hasPendingProgrammaticCommit returns false when nothing pending`() {
        val id = BlockId.generate()
        textStates.getOrCreate(id, "abc")

        assertFalse(textStates.hasPendingProgrammaticCommit(id))
    }

    @Test
    fun `hasPendingProgrammaticCommit does not consume the entry`() {
        val id = BlockId.generate()
        textStates.getOrCreate(id, "abc")
        textStates.setText(id, "xyz")

        // Peek twice — both return true
        assertTrue(textStates.hasPendingProgrammaticCommit(id))
        assertTrue(textStates.hasPendingProgrammaticCommit(id))

        // Consume removes it
        assertNotNull(textStates.consumeProgrammaticCommit(id))
        assertFalse(textStates.hasPendingProgrammaticCommit(id))
    }

    @Test
    fun `hasPendingProgrammaticCommit returns true after replaceVisibleRange`() {
        val id = BlockId.generate()
        textStates.getOrCreate(id, "abc")

        textStates.replaceVisibleRange(id, 1, 2, "X")

        assertTrue(textStates.hasPendingProgrammaticCommit(id))
    }
}
