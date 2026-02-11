package io.github.linreal.cascade.editor.ui.utils

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo

/**
 * Finds the LazyList item at the given Y position within the viewport.
 *
 * @param layoutInfo The layout info from LazyListState
 * @param y Y position relative to the list viewport
 * @return The item at that position, or null if no item is found
 */
internal fun findItemAtPosition(
    layoutInfo: LazyListLayoutInfo,
    y: Float
): LazyListItemInfo? {
    return layoutInfo.visibleItemsInfo.find { info ->
        y >= info.offset && y < info.offset + info.size
    }
}

/**
 * Calculates the visual drop target index based on the current drag Y position.
 *
 * **Important:** During drag, the dragged item remains in its original position
 * (just semi-transparent). This function returns the **visual gap position** in the
 * full list, NOT the MoveBlocks-compatible index.
 *
 * ## Gap Positions
 * For a list [A, B, C, D, E]:
 * - Gap 0: Before A
 * - Gap 1: Between A and B
 * - Gap 2: Between B and C
 * - Gap 3: Between C and D
 * - Gap 4: Between D and E
 * - Gap 5: After E
 *
 * ## Algorithm
 * 1. Scan visible items to find which gap the dragY is closest to
 * 2. For each item, check if dragY is above its midpoint
 * 3. Return the visual gap position
 *
 * Use [convertVisualGapToMoveBlocksIndex] in CompleteDrag to get the actual
 * index for the MoveBlocks action.
 *
 * @param layoutInfo The layout info from LazyListState
 * @param dragY Current Y position of the drag gesture (relative to the list)
 * @param totalItemCount Total number of items in the list
 * @return The visual gap position (0 to totalItemCount), or null if cannot determine
 */
public fun calculateDropTargetIndex(
    layoutInfo: LazyListLayoutInfo,
    dragY: Float,
    totalItemCount: Int
): Int? {
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return null

    return findVisualGapPosition(visibleItems, dragY, totalItemCount)
}

/**
 * Finds which visual gap (between items) the drag position is closest to.
 *
 * Gap positions:
 * - Gap 0: Before first item
 * - Gap 1: After first item (between item 0 and 1)
 * - Gap N: After item N-1 (between item N-1 and N)
 * - Gap totalCount: After last item
 *
 * @return The visual gap index (0 to totalItemCount)
 */
private fun findVisualGapPosition(
    visibleItems: List<androidx.compose.foundation.lazy.LazyListItemInfo>,
    dragY: Float,
    totalItemCount: Int
): Int {
    val firstVisible = visibleItems.first()
    val lastVisible = visibleItems.last()

    // If drag is above all visible items
    if (dragY < firstVisible.offset) {
        // Could be dropping at position 0 or before the first visible item
        return firstVisible.index
    }

    // If drag is below all visible items
    if (dragY > lastVisible.offset + lastVisible.size) {
        // Could be dropping at the end or after the last visible item
        return (lastVisible.index + 1).coerceAtMost(totalItemCount)
    }

    // Find which item boundary we're closest to
    for (item in visibleItems) {
        val itemMidpoint = item.offset + item.size / 2

        if (dragY < itemMidpoint) {
            // Drag is in the upper half of this item - drop before it
            return item.index
        }
    }

    // Drag is below the midpoint of the last visible item - drop after it
    return (lastVisible.index + 1).coerceAtMost(totalItemCount)
}

/**
 * Converts a visual gap position to the index expected by MoveBlocks action.
 *
 * MoveBlocks operates on the list AFTER removing the dragged item,
 * so we need to adjust the index accordingly.
 *
 * Call this in CompleteDrag when actually performing the move operation.
 *
 * @param visualGap The visual gap position (0 to totalItemCount) from [calculateDropTargetIndex]
 * @param originalIndex The original index of the dragged item
 * @param totalItemCount Total number of items
 * @return The adjusted index for MoveBlocks, or null if the drop would result in no movement
 */
public fun convertVisualGapToMoveBlocksIndex(
    visualGap: Int,
    originalIndex: Int,
    totalItemCount: Int
): Int? {
    // If dropping at the original position or immediately after, no movement needed
    if (visualGap == originalIndex || visualGap == originalIndex + 1) {
        return null
    }

    // Adjust for the removed item
    return if (visualGap > originalIndex) {
        visualGap - 1
    } else {
        visualGap
    }.coerceIn(0, totalItemCount - 1)
}

/**
 * Calculates the Y position for the drop indicator based on the visual gap position.
 *
 * Since [calculateDropTargetIndex] now returns the visual gap directly,
 * this function simply looks up the Y position for that gap.
 *
 * @param layoutInfo The layout info from LazyListState
 * @param visualGap The visual gap position (0 to totalItemCount)
 * @return The Y position for the drop indicator, or null if not determinable
 */
public fun calculateDropIndicatorY(
    layoutInfo: LazyListLayoutInfo,
    visualGap: Int?
): Float? {
    if (visualGap == null) return null

    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return null

    // Find the Y position for this gap
    val itemAtGap = visibleItems.find { it.index == visualGap }
    if (itemAtGap != null) {
        // Drop indicator goes at the top of this item
        return itemAtGap.offset.toFloat()
    }

    // Gap is before the first visible item
    val firstVisible = visibleItems.first()
    if (visualGap <= firstVisible.index) {
        return firstVisible.offset.toFloat()
    }

    // Gap is after the last visible item
    val lastVisible = visibleItems.last()
    if (visualGap > lastVisible.index) {
        return (lastVisible.offset + lastVisible.size).toFloat()
    }

    return null
}
