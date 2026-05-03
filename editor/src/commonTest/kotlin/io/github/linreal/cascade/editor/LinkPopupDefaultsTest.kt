package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.ui.LinkPopupDefaults
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkPopupDefaultsTest {

    @Test
    fun `centers popup horizontally and vertically in viewport`() {
        val offset = LinkPopupDefaults.calculatePopupOffset(
            popupHeight = 220f,
            popupWidth = 320f,
            viewportHeight = 800f,
            viewportWidth = 1000f,
        )
        assertEquals(340f, offset.x)
        assertEquals(290f, offset.y)
    }

    @Test
    fun `clamps to zero when popup is wider than viewport`() {
        val offset = LinkPopupDefaults.calculatePopupOffset(
            popupHeight = 220f,
            popupWidth = 1200f,
            viewportHeight = 800f,
            viewportWidth = 1000f,
        )
        assertEquals(0f, offset.x)
        assertEquals(290f, offset.y)
    }

    @Test
    fun `clamps to zero when popup is taller than viewport`() {
        val offset = LinkPopupDefaults.calculatePopupOffset(
            popupHeight = 900f,
            popupWidth = 320f,
            viewportHeight = 600f,
            viewportWidth = 1000f,
        )
        assertEquals(340f, offset.x)
        assertEquals(0f, offset.y)
    }

    @Test
    fun `popup ignores caret position - same offset regardless of viewport caret context`() {
        val small = LinkPopupDefaults.calculatePopupOffset(
            popupHeight = 220f,
            popupWidth = 320f,
            viewportHeight = 400f,
            viewportWidth = 400f,
        )
        assertEquals(40f, small.x)
        assertEquals(90f, small.y)
    }
}
