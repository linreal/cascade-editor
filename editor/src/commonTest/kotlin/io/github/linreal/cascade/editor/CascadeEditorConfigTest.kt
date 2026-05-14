package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.ui.CascadeEditorConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CascadeEditorConfigTest {

    @Test
    fun `default config is editable`() {
        assertFalse(CascadeEditorConfig.Default.readOnly)
        assertEquals(CascadeEditorConfig(readOnly = false), CascadeEditorConfig.Default)
    }
}
