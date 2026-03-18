package io.github.linreal.cascade.editor

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.theme.CascadeEditorTypography
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CascadeEditorTypographyTest {

    private val typography = CascadeEditorTypography.default()

    // -- All slots have valid font sizes --

    @Test
    fun `all slots have positive font sizes`() {
        allFontSizes().forEach { (name, size) ->
            assertTrue(size > 0f, "$name should have a positive font size, was $size")
        }
    }

    // -- Heading sizes are monotonically decreasing --

    @Test
    fun `heading sizes are monotonically decreasing from h1 to h6`() {
        val sizes = listOf(
            typography.heading1.fontSize.value,
            typography.heading2.fontSize.value,
            typography.heading3.fontSize.value,
            typography.heading4.fontSize.value,
            typography.heading5.fontSize.value,
            typography.heading6.fontSize.value,
        )
        for (i in 0 until sizes.size - 1) {
            assertTrue(
                sizes[i] > sizes[i + 1],
                "heading${i + 1} (${sizes[i]}) should be larger than heading${i + 2} (${sizes[i + 1]})"
            )
        }
    }

    @Test
    fun `h1 is the largest heading`() {
        assertTrue(typography.heading1.fontSize > typography.heading2.fontSize)
    }

    @Test
    fun `h6 is the smallest heading`() {
        assertTrue(typography.heading5.fontSize > typography.heading6.fontSize)
    }

    // -- Code font uses monospace --

    @Test
    fun `code font uses monospace family`() {
        assertEquals(FontFamily.Monospace, typography.code.fontFamily)
    }

    // -- Toolbar button uses Medium weight --

    @Test
    fun `toolbar button uses medium font weight`() {
        assertEquals(FontWeight.Medium, typography.toolbarButton.fontWeight)
    }

    // -- Default stability --

    @Test
    fun `default called twice produces equal instances`() {
        assertEquals(CascadeEditorTypography.default(), CascadeEditorTypography.default())
    }

    @Test
    fun `copy produces equal instance when no changes`() {
        assertEquals(typography, typography.copy())
    }

    @Test
    fun `copy with changed slot differs`() {
        val modified = typography.copy(body = TextStyle(fontSize = 20.sp))
        assertNotEquals(typography, modified)
        assertEquals(20.sp, modified.body.fontSize)
    }

    // -- Helpers --

    private fun allFontSizes(): List<Pair<String, Float>> = listOf(
        "body" to typography.body.fontSize.value,
        "heading1" to typography.heading1.fontSize.value,
        "heading2" to typography.heading2.fontSize.value,
        "heading3" to typography.heading3.fontSize.value,
        "heading4" to typography.heading4.fontSize.value,
        "heading5" to typography.heading5.fontSize.value,
        "heading6" to typography.heading6.fontSize.value,
        "code" to typography.code.fontSize.value,
        "slashItemTitle" to typography.slashItemTitle.fontSize.value,
        "slashBackButton" to typography.slashBackButton.fontSize.value,
        "toolbarButton" to typography.toolbarButton.fontSize.value,
    )
}
