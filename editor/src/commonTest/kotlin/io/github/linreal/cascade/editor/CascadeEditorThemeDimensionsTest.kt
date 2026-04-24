package io.github.linreal.cascade.editor

import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.theme.CascadeEditorDimensions
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CascadeEditorThemeDimensionsTest {

    @Test
    fun `default dimensions expose positive spacing tokens`() {
        val dimensions = CascadeEditorDimensions.default()

        assertTrue(dimensions.indentUnit.value > 0f)
        assertTrue(dimensions.blockHorizontalPadding.value > 0f)
    }

    @Test
    fun `default dimensions preserve existing horizontal block padding`() {
        assertEquals(16.dp, CascadeEditorDimensions.default().blockHorizontalPadding)
    }

    @Test
    fun `light and dark themes use default dimensions`() {
        val expected = CascadeEditorDimensions.default()

        assertEquals(expected, CascadeEditorTheme.light().dimensions)
        assertEquals(expected, CascadeEditorTheme.dark().dimensions)
    }

    @Test
    fun `theme copy can override dimensions`() {
        val original = CascadeEditorTheme.light()
        val modified = original.copy(
            dimensions = CascadeEditorDimensions.default().copy(indentUnit = 32.dp),
        )

        assertNotEquals(original, modified)
        assertEquals(32.dp, modified.dimensions.indentUnit)
    }
}
