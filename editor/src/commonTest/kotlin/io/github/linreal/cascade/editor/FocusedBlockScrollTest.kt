package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.ui.FocusedBlockScrollTarget
import io.github.linreal.cascade.editor.ui.VisibleLazyListItemBounds
import io.github.linreal.cascade.editor.ui.focusedBlockScrollTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FocusedBlockScrollTest {

    @Test
    fun `focused item immediately below viewport aligns near bottom instead of top`() {
        val target = focusedBlockScrollTarget(
            focusedIndex = 4,
            visibleItems = listOf(
                VisibleLazyListItemBounds(index = 1, offset = -20, size = 80),
                VisibleLazyListItemBounds(index = 2, offset = 60, size = 380),
                VisibleLazyListItemBounds(index = 3, offset = 440, size = 40),
            ),
            viewportStartOffset = 0,
            viewportEndOffset = 500,
            bottomMarginPx = 12,
        )

        assertEquals(
            FocusedBlockScrollTarget.ToItem(index = 4, scrollOffset = -448),
            target,
        )
    }

    @Test
    fun `focused item clipped by bottom viewport scrolls only by clipped amount`() {
        val target = focusedBlockScrollTarget(
            focusedIndex = 3,
            visibleItems = listOf(
                VisibleLazyListItemBounds(index = 2, offset = 40, size = 420),
                VisibleLazyListItemBounds(index = 3, offset = 480, size = 40),
            ),
            viewportStartOffset = 0,
            viewportEndOffset = 500,
            bottomMarginPx = 12,
        )

        assertEquals(FocusedBlockScrollTarget.By(deltaPx = 32), target)
    }

    @Test
    fun `fully visible focused item does not request scroll`() {
        val target = focusedBlockScrollTarget(
            focusedIndex = 2,
            visibleItems = listOf(
                VisibleLazyListItemBounds(index = 2, offset = 40, size = 120),
            ),
            viewportStartOffset = 0,
            viewportEndOffset = 500,
            bottomMarginPx = 12,
        )

        assertNull(target)
    }
}
