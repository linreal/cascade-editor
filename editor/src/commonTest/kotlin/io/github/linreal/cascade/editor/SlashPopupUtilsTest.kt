package io.github.linreal.cascade.editor

import androidx.compose.ui.geometry.Rect
import io.github.linreal.cascade.editor.slash.SlashCommandAction
import io.github.linreal.cascade.editor.slash.SlashCommandId
import io.github.linreal.cascade.editor.slash.SlashCommandItem
import io.github.linreal.cascade.editor.slash.SlashCommandResult
import io.github.linreal.cascade.editor.ui.SlashPopupDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlashPopupUtilsTest {

    // =========================================================================
    // estimatePopupHeightDp
    // =========================================================================

    @Test
    fun `estimatePopupHeightDp - small list uses compact height`() {
        val estimated = SlashPopupDefaults.estimatePopupHeightDp(
            itemCount = 2,
            hasBackHeader = false,
        )

        assertTrue(estimated < SlashPopupDefaults.MAX_HEIGHT_DP)
        assertTrue(estimated > SlashPopupDefaults.CONTENT_PADDING_DP * 2)
    }

    @Test
    fun `estimatePopupHeightDp - clamps large lists to max height`() {
        val estimated = SlashPopupDefaults.estimatePopupHeightDp(
            itemCount = 50,
            hasBackHeader = true,
        )

        assertEquals(SlashPopupDefaults.MAX_HEIGHT_DP, estimated)
    }

    // =========================================================================
    // calculatePopupOffset
    // =========================================================================

    @Test
    fun `calculatePopupOffset - below caret when space available`() {
        val caret = Rect(left = 50f, top = 100f, right = 52f, bottom = 120f)
        val offset = SlashPopupDefaults.calculatePopupOffset(
            caretRect = caret,
            popupHeight = 200f,
            popupWidth = 280f,
            viewportHeight = 600f,
            viewportWidth = 1080f,
            gap = 4f,
        )
        assertEquals(50f, offset.x)
        assertEquals(124f, offset.y) // bottom(120) + gap(4)
    }

    @Test
    fun `calculatePopupOffset - flips above when insufficient space below`() {
        val caret = Rect(left = 10f, top = 450f, right = 12f, bottom = 470f)
        val offset = SlashPopupDefaults.calculatePopupOffset(
            caretRect = caret,
            popupHeight = 200f,
            popupWidth = 280f,
            viewportHeight = 600f,
            viewportWidth = 1080f,
            gap = 4f,
        )
        assertEquals(10f, offset.x)
        assertEquals(246f, offset.y) // top(450) - gap(4) - height(200) = 246
    }

    @Test
    fun `calculatePopupOffset - clamps to 0 when neither direction fits`() {
        // Caret at top, popup taller than viewport
        val caret = Rect(left = 0f, top = 10f, right = 2f, bottom = 30f)
        val offset = SlashPopupDefaults.calculatePopupOffset(
            caretRect = caret,
            popupHeight = 800f,
            popupWidth = 280f,
            viewportHeight = 100f,
            viewportWidth = 1080f,
            gap = 4f,
        )
        assertEquals(0f, offset.x)
        assertEquals(34f, offset.y) // max(belowY, 0) = max(34, 0) = 34
    }

    @Test
    fun `calculatePopupOffset - zero gap`() {
        val caret = Rect(left = 0f, top = 0f, right = 2f, bottom = 20f)
        val offset = SlashPopupDefaults.calculatePopupOffset(
            caretRect = caret,
            popupHeight = 100f,
            popupWidth = 280f,
            viewportHeight = 200f,
            viewportWidth = 1080f,
            gap = 0f,
        )
        assertEquals(0f, offset.x)
        assertEquals(20f, offset.y) // bottom(20) + gap(0)
    }

    @Test
    fun `calculatePopupOffset - clamps X when caret near right edge`() {
        val caret = Rect(left = 900f, top = 100f, right = 902f, bottom = 120f)
        val offset = SlashPopupDefaults.calculatePopupOffset(
            caretRect = caret,
            popupHeight = 200f,
            popupWidth = 280f,
            viewportHeight = 600f,
            viewportWidth = 1080f,
            gap = 4f,
        )
        assertEquals(800f, offset.x) // 1080 - 280 = 800
        assertEquals(124f, offset.y)
    }

    @Test
    fun `calculatePopupOffset - X stays at caret when popup fits`() {
        val caret = Rect(left = 100f, top = 100f, right = 102f, bottom = 120f)
        val offset = SlashPopupDefaults.calculatePopupOffset(
            caretRect = caret,
            popupHeight = 200f,
            popupWidth = 280f,
            viewportHeight = 600f,
            viewportWidth = 1080f,
            gap = 4f,
        )
        assertEquals(100f, offset.x)
        assertEquals(124f, offset.y)
    }

    // =========================================================================
    // resolveNextHighlight
    // =========================================================================

    @Test
    fun `resolveNextHighlight - empty list returns null`() {
        assertNull(
            SlashPopupDefaults.resolveNextHighlight(
                currentId = null,
                flatItems = emptyList(),
                direction = 1,
            )
        )
    }

    @Test
    fun `resolveNextHighlight - from null direction down selects first`() {
        val items = testItems("a", "b", "c")
        assertEquals(
            SlashCommandId("a"),
            SlashPopupDefaults.resolveNextHighlight(null, items, direction = 1),
        )
    }

    @Test
    fun `resolveNextHighlight - from null direction up selects last`() {
        val items = testItems("a", "b", "c")
        assertEquals(
            SlashCommandId("c"),
            SlashPopupDefaults.resolveNextHighlight(null, items, direction = -1),
        )
    }

    @Test
    fun `resolveNextHighlight - normal navigation down`() {
        val items = testItems("a", "b", "c")
        assertEquals(
            SlashCommandId("b"),
            SlashPopupDefaults.resolveNextHighlight(SlashCommandId("a"), items, direction = 1),
        )
    }

    @Test
    fun `resolveNextHighlight - normal navigation up`() {
        val items = testItems("a", "b", "c")
        assertEquals(
            SlashCommandId("a"),
            SlashPopupDefaults.resolveNextHighlight(SlashCommandId("b"), items, direction = -1),
        )
    }

    @Test
    fun `resolveNextHighlight - down from last stays at last`() {
        val items = testItems("a", "b", "c")
        assertEquals(
            SlashCommandId("c"),
            SlashPopupDefaults.resolveNextHighlight(SlashCommandId("c"), items, direction = 1),
        )
    }

    @Test
    fun `resolveNextHighlight - up from first stays at first`() {
        val items = testItems("a", "b", "c")
        assertEquals(
            SlashCommandId("a"),
            SlashPopupDefaults.resolveNextHighlight(SlashCommandId("a"), items, direction = -1),
        )
    }

    @Test
    fun `resolveNextHighlight - unknown current id selects first`() {
        val items = testItems("a", "b")
        assertEquals(
            SlashCommandId("a"),
            SlashPopupDefaults.resolveNextHighlight(SlashCommandId("unknown"), items, direction = 1),
        )
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun testAction(id: String): SlashCommandItem = SlashCommandAction(
        id = SlashCommandId(id),
        title = id,
        description = "",
        onExecute = { SlashCommandResult.Done },
    )

    private fun testItems(vararg ids: String): List<SlashCommandItem> =
        ids.map { testAction(it) }
}
