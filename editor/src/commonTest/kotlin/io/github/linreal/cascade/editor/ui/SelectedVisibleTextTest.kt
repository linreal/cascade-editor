package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SelectedVisibleTextTest {

    private val zwsp = "\u200B"

    @Test
    fun `selection including raw zero excludes sentinel`() {
        val state = createState("Hello", start = 0, end = 3)

        assertEquals("He", state.selectedVisibleText())
    }

    @Test
    fun `selection covering full visible text matches visible text`() {
        val state = createState("Hello", start = 1, end = 6)

        assertEquals(state.visibleText(), state.selectedVisibleText())
    }

    @Test
    fun `reversed visible selection returns selected text in document order`() {
        val state = createState("Hello", start = 5, end = 2)

        assertEquals("ell", state.selectedVisibleText())
    }

    @Test
    fun `collapsed selection returns empty text`() {
        val state = createState("Hello", start = 3)

        assertEquals("", state.selectedVisibleText())
    }

    @Test
    fun `raw selection at exactly zero excludes sentinel character`() {
        // Raw [0, 2) spans sentinel + 'H'. visibleSelection clamps the start to
        // 0, so the returned text is "H" — crucially, the sentinel char is not
        // part of the returned string and never reaches the platform clipboard.
        val state = createState("Hello", start = 0, end = 2)

        val selected = state.selectedVisibleText()
        assertEquals("H", selected)
        assertFalse(selected.contains(zwsp.first()))
    }

    @Test
    fun `raw selection covering entire buffer including sentinel returns visible text only`() {
        val state = createState("Hi", start = 0, end = 3)

        val selected = state.selectedVisibleText()
        assertEquals("Hi", selected)
        assertFalse(selected.contains(zwsp.first()))
    }

    @Test
    fun `sentinel-only buffer with selection across sentinel returns empty string`() {
        // Edge case: a field with no visible text (sentinel-only). Any selection
        // collapses to visible space, which has length 0.
        val state = TextFieldState(initialText = zwsp).also { state ->
            state.edit { selection = TextRange(0, 1) }
        }

        val selected = state.selectedVisibleText()
        assertEquals("", selected)
        assertFalse(selected.contains(zwsp.first()))
    }

    private fun createState(text: String, start: Int, end: Int = start): TextFieldState {
        return TextFieldState(initialText = "$zwsp$text").also { state ->
            state.edit { selection = TextRange(start, end) }
        }
    }
}
