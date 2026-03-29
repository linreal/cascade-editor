package io.github.linreal.cascade.editor

import androidx.compose.ui.graphics.Color
import io.github.linreal.cascade.editor.theme.CascadeEditorColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CascadeEditorColorsTest {

    // -- light() preset --

    @Test
    fun `light returns non-transparent values for all slots`() {
        val colors = CascadeEditorColors.light()
        allSlots(colors).forEach { (name, color) ->
            assertTrue(color != Color.Unspecified, "$name should not be Unspecified")
        }
    }

    @Test
    fun `light primary matches original hardcoded Google Blue`() {
        val colors = CascadeEditorColors.light()
        assertEquals(Color(0xFF1A73E8), colors.primary)
    }

    @Test
    fun `light onPrimary is white`() {
        assertEquals(Color.White, CascadeEditorColors.light().onPrimary)
    }

    // -- dark() preset --

    @Test
    fun `dark returns non-transparent values for all slots`() {
        val colors = CascadeEditorColors.dark()
        allSlots(colors).forEach { (name, color) ->
            assertTrue(color != Color.Unspecified, "$name should not be Unspecified")
        }
    }

    // -- light vs dark differ --

    @Test
    fun `light and dark differ on text color`() {
        assertNotEquals(CascadeEditorColors.light().text, CascadeEditorColors.dark().text)
    }

    @Test
    fun `light and dark differ on popupBackground`() {
        assertNotEquals(
            CascadeEditorColors.light().popupBackground,
            CascadeEditorColors.dark().popupBackground,
        )
    }

    @Test
    fun `light and dark differ on primary`() {
        assertNotEquals(CascadeEditorColors.light().primary, CascadeEditorColors.dark().primary)
    }

    @Test
    fun `light and dark differ on quoteBorder`() {
        assertNotEquals(CascadeEditorColors.light().quoteBorder, CascadeEditorColors.dark().quoteBorder)
    }

    @Test
    fun `light and dark differ on selectionOverlay`() {
        assertNotEquals(
            CascadeEditorColors.light().selectionOverlay,
            CascadeEditorColors.dark().selectionOverlay,
        )
    }

    @Test
    fun `light and dark differ on onPrimary`() {
        assertNotEquals(CascadeEditorColors.light().onPrimary, CascadeEditorColors.dark().onPrimary)
    }

    // -- Copy / equality --

    @Test
    fun `copy produces equal instance when no changes`() {
        val original = CascadeEditorColors.light()
        assertEquals(original, original.copy())
    }

    @Test
    fun `copy with changed slot differs`() {
        val original = CascadeEditorColors.light()
        val modified = original.copy(primary = Color.Red)
        assertNotEquals(original, modified)
        assertEquals(Color.Red, modified.primary)
    }

    @Test
    fun `light called twice produces equal instances`() {
        assertEquals(CascadeEditorColors.light(), CascadeEditorColors.light())
    }

    @Test
    fun `dark called twice produces equal instances`() {
        assertEquals(CascadeEditorColors.dark(), CascadeEditorColors.dark())
    }

    // -- Helpers --

    private fun allSlots(c: CascadeEditorColors): List<Pair<String, Color>> = listOf(
        "primary" to c.primary,
        "onPrimary" to c.onPrimary,
        "text" to c.text,
        "popupBackground" to c.popupBackground,
        "unknownBlockBackground" to c.unknownBlockBackground,
        "toolbarIcon" to c.toolbarIcon,
        "toolbarIconDisabled" to c.toolbarIconDisabled,
        "slashItemTitle" to c.slashItemTitle,
        "slashChevron" to c.slashChevron,
        "unknownBlockText" to c.unknownBlockText,
        "uiDivider" to c.uiDivider,
        "contentDivider" to c.contentDivider,
        "slashSelectedItem" to c.slashSelectedItem,
        "inlineCodeBackground" to c.inlineCodeBackground,
        "highlight" to c.highlight,
        "cursor" to c.cursor,
        "textSelectionBackground" to c.textSelectionBackground,
        "quoteBorder" to c.quoteBorder,
        "quoteBackground" to c.quoteBackground,
        "selectionOverlay" to c.selectionOverlay,
    )
}
