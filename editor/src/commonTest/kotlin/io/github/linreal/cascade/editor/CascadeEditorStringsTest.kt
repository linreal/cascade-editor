package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.theme.CascadeEditorStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CascadeEditorStringsTest {

    private val strings = CascadeEditorStrings.default()

    // -- default() returns non-empty strings --

    @Test
    fun `back is non-empty`() {
        assertTrue(strings.back.isNotEmpty())
    }

    @Test
    fun `bold is non-empty`() {
        assertTrue(strings.bold.isNotEmpty())
    }

    @Test
    fun `italic is non-empty`() {
        assertTrue(strings.italic.isNotEmpty())
    }

    @Test
    fun `underline is non-empty`() {
        assertTrue(strings.underline.isNotEmpty())
    }

    @Test
    fun `strikethrough is non-empty`() {
        assertTrue(strings.strikethrough.isNotEmpty())
    }

    @Test
    fun `inlineCode is non-empty`() {
        assertTrue(strings.inlineCode.isNotEmpty())
    }

    @Test
    fun `highlight is non-empty`() {
        assertTrue(strings.highlight.isNotEmpty())
    }

    @Test
    fun `slashCommand is non-empty`() {
        assertTrue(strings.slashCommand.isNotEmpty())
    }

    @Test
    fun `hideKeyboard is non-empty`() {
        assertTrue(strings.hideKeyboard.isNotEmpty())
    }

    // -- unsupportedBlock lambda --

    @Test
    fun `unsupportedBlock interpolates typeId`() {
        val result = strings.unsupportedBlock("custom:widget")
        assertTrue(result.contains("custom:widget"), "Result should contain typeId: $result")
    }

    @Test
    fun `unsupportedBlock default format matches original hardcoded pattern`() {
        assertEquals("Unsupported block type: foo", strings.unsupportedBlock("foo"))
    }

    // -- Copy with custom values --

    @Test
    fun `copy with custom back string works`() {
        val custom = strings.copy(back = "Retour")
        assertEquals("Retour", custom.back)
        // Other fields unchanged
        assertEquals(strings.bold, custom.bold)
    }

    @Test
    fun `copy with custom unsupportedBlock lambda works`() {
        val custom = strings.copy(unsupportedBlock = { "Unknown: $it" })
        assertEquals("Unknown: test_block", custom.unsupportedBlock("test_block"))
    }

    @Test
    fun `copy produces equal instance when no changes`() {
        assertEquals(strings, strings.copy())
    }

    // -- Default stability --

    @Test
    fun `default called twice produces equal instances`() {
        assertEquals(CascadeEditorStrings.default(), CascadeEditorStrings.default())
    }

    // -- Known default values --

    @Test
    fun `default back label contains Back`() {
        assertTrue(strings.back.contains("Back"))
    }

    @Test
    fun `default toolbar labels match English`() {
        assertEquals("Bold", strings.bold)
        assertEquals("Italic", strings.italic)
        assertEquals("Underline", strings.underline)
        assertEquals("Strikethrough", strings.strikethrough)
        assertEquals("Inline Code", strings.inlineCode)
        assertEquals("Highlight", strings.highlight)
        assertEquals("Slash Command", strings.slashCommand)
        assertEquals("Hide Keyboard", strings.hideKeyboard)
    }
}
