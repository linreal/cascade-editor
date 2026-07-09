package io.github.linreal.cascade.ios.toolbar

import io.github.linreal.cascade.editor.richtext.StyleStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CascadeToolbarStateTest {
    @Test
    fun mapsStyleStatusToTriState() {
        assertEquals(CascadeStyleState.active, StyleStatus.FullyActive.toCascadeStyleState())
        assertEquals(CascadeStyleState.mixed, StyleStatus.Partial.toCascadeStyleState())
        assertEquals(CascadeStyleState.inactive, StyleStatus.Absent.toCascadeStyleState())
    }

    @Test
    fun emptyToolbarStateIsUnavailable() {
        val empty = CascadeToolbarState.Empty

        assertFalse(empty.focused)
        assertFalse(empty.canFormat)
        assertEquals(CascadeStyleState.inactive, empty.bold)
        assertEquals(CascadeStyleState.inactive, empty.highlight)
        assertFalse(empty.canIndentForward)
        assertFalse(empty.canIndentBackward)
        assertFalse(empty.canLink)
        assertNull(empty.existingUrl)
    }
}
