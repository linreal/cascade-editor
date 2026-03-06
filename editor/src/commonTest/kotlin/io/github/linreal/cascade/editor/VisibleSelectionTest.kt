package io.github.linreal.cascade.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.ui.visibleSelection
import kotlin.test.Test
import kotlin.test.assertEquals

class VisibleSelectionTest {

    private val zwsp = "\u200B"

    private fun createState(text: String, start: Int, end: Int = start): TextFieldState {
        return TextFieldState(initialText = "$zwsp$text").also { state ->
            state.edit { selection = TextRange(start, end) }
        }
    }

    @Test
    fun `collapsed cursor after sentinel returns visible position 0`() {
        val state = createState("Hello", start = 1)
        val result = state.visibleSelection()
        assertEquals(TextRange(0, 0), result)
    }

    @Test
    fun `collapsed cursor at raw 0 clamps to visible 0`() {
        val state = createState("Hello", start = 0)
        val result = state.visibleSelection()
        assertEquals(TextRange(0, 0), result)
    }

    @Test
    fun `collapsed cursor at middle of text`() {
        val state = createState("Hello", start = 3) // raw 3 → visible 2
        val result = state.visibleSelection()
        assertEquals(TextRange(2, 2), result)
    }

    @Test
    fun `collapsed cursor at end of text`() {
        // "Hello" = 5 chars, sentinel at 0, so raw end is 6
        val state = createState("Hello", start = 6)
        val result = state.visibleSelection()
        assertEquals(TextRange(5, 5), result)
    }

    @Test
    fun `ranged selection fully within visible text`() {
        // Select "ell" in "Hello": visible [1,4), raw [2,5)
        val state = createState("Hello", start = 2, end = 5)
        val result = state.visibleSelection()
        assertEquals(TextRange(1, 4), result)
    }

    @Test
    fun `ranged selection from start of visible text`() {
        // Select "Hel" in "Hello": visible [0,3), raw [1,4)
        val state = createState("Hello", start = 1, end = 4)
        val result = state.visibleSelection()
        assertEquals(TextRange(0, 3), result)
    }

    @Test
    fun `ranged selection covering entire visible text`() {
        // Select all "Hello": visible [0,5), raw [1,6)
        val state = createState("Hello", start = 1, end = 6)
        val result = state.visibleSelection()
        assertEquals(TextRange(0, 5), result)
    }

    @Test
    fun `reversed selection is preserved`() {
        // Reversed: end before start in raw coords. raw start=5, end=2 → visible 4,1
        val state = createState("Hello", start = 5, end = 2)
        val result = state.visibleSelection()
        assertEquals(TextRange(4, 1), result)
    }

    @Test
    fun `empty text with cursor after sentinel`() {
        val state = createState("", start = 1)
        val result = state.visibleSelection()
        assertEquals(TextRange(0, 0), result)
    }
}
