package io.github.linreal.cascade.editor.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Gap kept below the focused text block when editor-owned focus scrolling moves
 * a block into view. This prevents a newly split block from ending up flush
 * against the IME-adjusted viewport bottom on Android.
 */
internal val FocusedBlockBottomMargin: Dp = 12.dp

/**
 * Minimal layout data needed by [focusedBlockScrollTarget].
 *
 * Keeping this shape independent of Compose's LazyList item classes lets the
 * focus-scroll policy stay pure and covered by common tests.
 */
internal data class VisibleLazyListItemBounds(
    val index: Int,
    val offset: Int,
    val size: Int,
)

/**
 * Scroll operation needed to reveal a focused block without unnecessarily
 * moving it to the top of the editor viewport.
 */
internal sealed interface FocusedBlockScrollTarget {
    /**
     * Scroll by a small pixel delta when the focused item is already composed
     * but clipped by the current viewport.
     */
    data class By(val deltaPx: Int) : FocusedBlockScrollTarget

    /**
     * Scroll directly to an item when the focused item is not currently
     * composed. [scrollOffset] uses LazyListState's item-offset convention.
     */
    data class ToItem(val index: Int, val scrollOffset: Int) : FocusedBlockScrollTarget
}

/**
 * Calculates the least disruptive scroll needed to reveal [focusedIndex].
 *
 * The important Android case is pressing Enter when the current block sits just
 * above the software keyboard. The split creates a new focused block just below
 * the IME-adjusted viewport; a bare `animateScrollToItem(index)` would align the
 * new block to the top and make the document jump. This policy instead:
 *
 * - returns no target when the focused item is fully visible;
 * - scrolls only by the clipped amount when the focused item is partially visible;
 * - bottom-aligns a not-yet-composed item when it sits after the current viewport;
 * - keeps top alignment for items before the current viewport.
 */
internal fun focusedBlockScrollTarget(
    focusedIndex: Int,
    visibleItems: List<VisibleLazyListItemBounds>,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
    bottomMarginPx: Int,
): FocusedBlockScrollTarget? {
    if (focusedIndex < 0 || viewportEndOffset <= viewportStartOffset) return null

    val effectiveViewportEnd = (viewportEndOffset - bottomMarginPx.coerceAtLeast(0))
        .coerceAtLeast(viewportStartOffset)
    val focusedItem = visibleItems.firstOrNull { it.index == focusedIndex }
    if (focusedItem != null) {
        val itemStart = focusedItem.offset
        val itemEnd = focusedItem.offset + focusedItem.size
        return when {
            itemStart < viewportStartOffset ->
                FocusedBlockScrollTarget.By(deltaPx = itemStart - viewportStartOffset)
            itemEnd > effectiveViewportEnd ->
                FocusedBlockScrollTarget.By(deltaPx = itemEnd - effectiveViewportEnd)
            else -> null
        }
    }

    val firstVisibleIndex = visibleItems.minOfOrNull { it.index }
        ?: return FocusedBlockScrollTarget.ToItem(index = focusedIndex, scrollOffset = 0)
    val lastVisibleIndex = visibleItems.maxOf { it.index }
    if (focusedIndex < firstVisibleIndex) {
        return FocusedBlockScrollTarget.ToItem(index = focusedIndex, scrollOffset = 0)
    }

    if (focusedIndex > lastVisibleIndex) {
        val estimatedItemSize = visibleItems
            .filter { it.index < focusedIndex }
            .maxByOrNull { it.index }
            ?.size
            ?: visibleItems.maxByOrNull { it.index }?.size
            ?: 0
        val desiredTop = effectiveViewportEnd - estimatedItemSize.coerceAtLeast(0)
        val scrollOffset = if (desiredTop > viewportStartOffset) -desiredTop else 0
        return FocusedBlockScrollTarget.ToItem(index = focusedIndex, scrollOffset = scrollOffset)
    }

    return FocusedBlockScrollTarget.ToItem(index = focusedIndex, scrollOffset = 0)
}
