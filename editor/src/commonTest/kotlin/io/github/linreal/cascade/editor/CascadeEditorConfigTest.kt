package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.ui.CascadeEditorConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CascadeEditorConfigTest {

    @Test
    fun `default config is editable`() {
        assertFalse(CascadeEditorConfig.Default.readOnly)
        assertTrue(CascadeEditorConfig.Default.blockSelectionEnabled)
        assertTrue(CascadeEditorConfig.Default.blockDraggingEnabled)
        assertEquals(
            CascadeEditorConfig(
                readOnly = false,
                blockSelectionEnabled = true,
                blockDraggingEnabled = true,
            ),
            CascadeEditorConfig.Default,
        )
    }
}
