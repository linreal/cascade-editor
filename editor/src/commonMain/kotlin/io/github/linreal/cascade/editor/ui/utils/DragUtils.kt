package io.github.linreal.cascade.editor.ui.utils

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.isInsidePayloadRanges
import io.github.linreal.cascade.editor.core.toContiguousRanges
import io.github.linreal.cascade.editor.state.DragState
import kotlin.math.max
import kotlin.math.min

/**
 * Stable semantic drag-hover target derived from pointer position.
 *
 * Pointer X/Y values are intentionally excluded: callers dispatch only this resolved
 * structure into editor state so frame-rate pointer movement does not recompose the
 * document tree unless the visual gap or future indentation lane actually changes.
 */
internal data class DragHoverTarget(
    val visualGap: Int,
    val futureRootIndentationLevel: Int,
)

private data class PayloadDepthOffsetRange(
    val shallowestOffset: Int,
    val deepestOffset: Int,
)

/**
 * Cached block lookup data for drag-hover resolution.
 *
 * Build one instance per [blocks] list and reuse it while pointer movement updates the
 * hover target. This keeps the 60fps hover path from rebuilding full-document maps.
 */
internal class DragHoverOutlineIndex(
    internal val blocks: List<Block>,
) {
    private val indexByBlockId: Map<BlockId, Int> = blocks
        .mapIndexed { index, block -> block.id to index }
        .toMap()

    fun blockAt(index: Int): Block? = blocks.getOrNull(index)

    fun blockById(id: BlockId): Block? {
        return indexByBlockId[id]?.let { index -> blocks[index] }
    }

    fun payloadIndicesFor(payloadIds: Set<BlockId>): List<Int>? {
        if (payloadIds.isEmpty()) return null

        val indices = ArrayList<Int>(payloadIds.size)
        for (id in payloadIds) {
            indices += indexByBlockId[id] ?: return null
        }
        return indices.sorted()
    }

    fun matchesPayloadIndices(
        payloadIndices: List<Int>,
        payloadIds: Set<BlockId>,
    ): Boolean {
        if (payloadIndices.size != payloadIds.size || payloadIndices.isEmpty()) return false
        return payloadIndices.all { index ->
            val block = blockAt(index) ?: return@all false
            block.id in payloadIds
        }
    }

    fun previousNonPayloadBlock(
        visualGap: Int,
        payloadIndexSet: Set<Int>,
    ): Block? {
        var index = visualGap - 1
        while (index >= 0 && index in payloadIndexSet) {
            index--
        }
        return blockAt(index)
    }

    fun nextNonPayloadBlock(
        visualGap: Int,
        payloadIndexSet: Set<Int>,
    ): Block? {
        var index = visualGap
        while (index < blocks.size && index in payloadIndexSet) {
            index++
        }
        return blockAt(index)
    }
}

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
 * Drag completion now consumes this visual gap directly through the subtree-aware
 * reducer. [convertVisualGapToMoveBlocksIndex] remains for legacy flat-reorder
 * callers and focused utility tests.
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
 * Resolves a drag hover into a visual gap and a legal future root depth.
 *
 * Y selects [visualGap]. Horizontal movement selects a raw depth in whole indent-unit
 * steps from the primary root's original depth. Surrounding non-payload blocks and
 * payload depth range then clamp that raw value so the eventual move cannot exceed
 * the persisted depth range or make following non-dragged blocks become accidental
 * descendants of the moved payload.
 *
 * When [allowIndentationChange] is `false`, horizontal movement is ignored and the
 * primary root must keep its original depth. A gap that cannot accept the payload at
 * that depth is rejected instead of silently rewriting indentation.
 *
 * Returns `null` when the visual gap is inside the dragged payload or when no depth can
 * satisfy the surrounding outline constraints.
 */
internal fun resolveDepthAwareDragHoverTarget(
    blocks: List<Block>,
    dragState: DragState?,
    visualGap: Int?,
    horizontalDragDeltaPx: Float,
    indentUnitPx: Float,
    allowIndentationChange: Boolean = true,
): DragHoverTarget? {
    return resolveDepthAwareDragHoverTarget(
        outlineIndex = DragHoverOutlineIndex(blocks),
        dragState = dragState,
        visualGap = visualGap,
        horizontalDragDeltaPx = horizontalDragDeltaPx,
        indentUnitPx = indentUnitPx,
        allowIndentationChange = allowIndentationChange,
    )
}

internal fun resolveDepthAwareDragHoverTarget(
    outlineIndex: DragHoverOutlineIndex,
    dragState: DragState?,
    visualGap: Int?,
    horizontalDragDeltaPx: Float,
    indentUnitPx: Float,
    allowIndentationChange: Boolean = true,
): DragHoverTarget? {
    val currentDragState = dragState ?: return null
    val blocks = outlineIndex.blocks
    val gap = visualGap?.takeIf { it in 0..blocks.size } ?: return null
    val payloadIds = currentDragState.payloadBlockIds
    if (blocks.isEmpty() || payloadIds.isEmpty()) return null

    val payloadIdSet = currentDragState.payloadBlockIdSet
        .takeIf { it.size == payloadIds.size && it.containsAll(payloadIds) }
        ?: payloadIds.toSet()
    val cachedPayloadIndices = currentDragState.payloadBlockIndices
        .takeIf { outlineIndex.matchesPayloadIndices(it, payloadIdSet) }
    val payloadIndices = cachedPayloadIndices
        ?: outlineIndex.payloadIndicesFor(payloadIdSet)
        ?: return null
    val payloadIndexSet = currentDragState.payloadBlockIndexSet
        .takeIf { it.size == payloadIndices.size && it.containsAll(payloadIndices) }
        ?: payloadIndices.toSet()
    val payloadIndexRanges = if (cachedPayloadIndices != null) {
        currentDragState.payloadIndexRanges
            .takeIf { it.matchesPayloadIndices(payloadIndices) }
    } else {
        null
    } ?: payloadIndices.toContiguousRanges()
    if (gap.isInsidePayloadRanges(payloadIndexRanges)) return null

    val nextBlock = outlineIndex.nextNonPayloadBlock(gap, payloadIndexSet)

    val primaryRootId = currentDragState.primaryRootId ?: return null
    val primaryRoot = outlineIndex.blockById(primaryRootId) ?: return null
    val originalPrimaryDepth = currentDragState.originalRootIndentationLevels[primaryRootId]
        ?: primaryRoot.attributes.indentationLevel
    val primarySupportsIndentation =
        currentDragState.primaryRootSupportsIndentation ?: primaryRoot.type.supportsIndentation
    val requestedDepth = if (primarySupportsIndentation && allowIndentationChange) {
        originalPrimaryDepth + horizontalIndentSteps(horizontalDragDeltaPx, indentUnitPx)
    } else {
        originalPrimaryDepth
    }

    val payloadDepthOffsetRange =
        currentDragState.payloadDepthOffsetRangeFromPrimary(originalPrimaryDepth)
            ?: blocks.payloadDepthOffsetRangeFromPrimary(
                payloadIds = payloadIdSet,
                originalPrimaryDepth = originalPrimaryDepth,
            )
            ?: return null
    val shallowestPayloadOffset = payloadDepthOffsetRange.shallowestOffset
    val deepestPayloadOffset = payloadDepthOffsetRange.deepestOffset

    var minDepth = max(
        BlockAttributes.MIN_INDENTATION_LEVEL,
        BlockAttributes.MIN_INDENTATION_LEVEL - shallowestPayloadOffset,
    )
    var maxDepth = min(
        BlockAttributes.MAX_INDENTATION_LEVEL,
        BlockAttributes.MAX_INDENTATION_LEVEL - deepestPayloadOffset,
    )

    if (!primarySupportsIndentation) {
        minDepth = max(minDepth, BlockAttributes.MIN_INDENTATION_LEVEL)
        maxDepth = min(maxDepth, BlockAttributes.MIN_INDENTATION_LEVEL)
    } else if (nextBlock != null) {
        // A following block deeper than the moved root would become part of the moved
        // subtree. Clamp against the shallowest moved block, not just the primary root,
        // so multi-root drags cannot make a shallower secondary root adopt the next block.
        minDepth = max(minDepth, nextBlock.attributes.indentationLevel - shallowestPayloadOffset)
    }

    if (minDepth > maxDepth) return null
    if (!allowIndentationChange && requestedDepth !in minDepth..maxDepth) return null

    return DragHoverTarget(
        visualGap = gap,
        futureRootIndentationLevel = requestedDepth.coerceIn(minDepth, maxDepth),
    )
}

/**
 * Recomputes the depth-aware hover target from current list layout.
 *
 * Both direct pointer drag and auto-scroll use this helper so visual-gap and future-depth
 * semantics do not diverge as the list scrolls under an active drag.
 */
internal fun recomputeDepthAwareDragHoverTarget(
    layoutInfo: LazyListLayoutInfo,
    dragY: Float,
    blocks: List<Block>,
    dragState: DragState?,
    horizontalDragDeltaPx: Float,
    indentUnitPx: Float,
    allowIndentationChange: Boolean = true,
): DragHoverTarget? {
    return recomputeDepthAwareDragHoverTarget(
        layoutInfo = layoutInfo,
        dragY = dragY,
        outlineIndex = DragHoverOutlineIndex(blocks),
        dragState = dragState,
        horizontalDragDeltaPx = horizontalDragDeltaPx,
        indentUnitPx = indentUnitPx,
        allowIndentationChange = allowIndentationChange,
    )
}

internal fun recomputeDepthAwareDragHoverTarget(
    layoutInfo: LazyListLayoutInfo,
    dragY: Float,
    outlineIndex: DragHoverOutlineIndex,
    dragState: DragState?,
    horizontalDragDeltaPx: Float,
    indentUnitPx: Float,
    allowIndentationChange: Boolean = true,
): DragHoverTarget? {
    val visualGap = calculateDropTargetIndex(
        layoutInfo = layoutInfo,
        dragY = dragY,
        totalItemCount = outlineIndex.blocks.size,
    )
    return resolveDepthAwareDragHoverTarget(
        outlineIndex = outlineIndex,
        dragState = dragState,
        visualGap = visualGap,
        horizontalDragDeltaPx = horizontalDragDeltaPx,
        indentUnitPx = indentUnitPx,
        allowIndentationChange = allowIndentationChange,
    )
}

private fun List<IntRange>.matchesPayloadIndices(payloadIndices: List<Int>): Boolean {
    if (isEmpty() || payloadIndices.isEmpty()) return false

    var cursor = 0
    for (range in this) {
        for (index in range) {
            if (cursor >= payloadIndices.size || payloadIndices[cursor] != index) {
                return false
            }
            cursor++
        }
    }
    return cursor == payloadIndices.size
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
 * Converts horizontal drag delta into whole outline steps, preserving the starting
 * depth until the pointer crosses at least one full indent unit.
 */
private fun horizontalIndentSteps(
    horizontalDragDeltaPx: Float,
    indentUnitPx: Float,
): Int {
    if (indentUnitPx <= 0f) return 0
    return (horizontalDragDeltaPx / indentUnitPx).toInt()
}

private fun DragState.payloadDepthOffsetRangeFromPrimary(
    originalPrimaryDepth: Int,
): PayloadDepthOffsetRange? {
    if (payloadBlockIds.isEmpty()) return null

    var shallowestOffset: Int? = null
    var deepestOffset: Int? = null
    for (blockId in payloadBlockIds) {
        val rootId = payloadRootIdsByBlockId[blockId] ?: return null
        val rootDepth = originalRootIndentationLevels[rootId] ?: return null
        val relativeOffset = payloadRelativeDepthOffsets[blockId] ?: return null
        val offsetFromPrimary = rootDepth + relativeOffset - originalPrimaryDepth
        shallowestOffset = min(shallowestOffset ?: offsetFromPrimary, offsetFromPrimary)
        deepestOffset = max(deepestOffset ?: offsetFromPrimary, offsetFromPrimary)
    }

    return PayloadDepthOffsetRange(
        shallowestOffset = shallowestOffset ?: return null,
        deepestOffset = deepestOffset ?: return null,
    )
}

private fun List<Block>.payloadDepthOffsetRangeFromPrimary(
    payloadIds: Set<BlockId>,
    originalPrimaryDepth: Int,
): PayloadDepthOffsetRange? {
    var shallowestOffset: Int? = null
    var deepestOffset: Int? = null
    for (block in this) {
        if (block.id !in payloadIds) continue
        val offsetFromPrimary = block.attributes.indentationLevel - originalPrimaryDepth
        shallowestOffset = min(shallowestOffset ?: offsetFromPrimary, offsetFromPrimary)
        deepestOffset = max(deepestOffset ?: offsetFromPrimary, offsetFromPrimary)
    }

    return PayloadDepthOffsetRange(
        shallowestOffset = shallowestOffset ?: return null,
        deepestOffset = deepestOffset ?: return null,
    )
}

/**
 * Converts a visual gap position to the index expected by the flat `MoveBlocks` action.
 *
 * `MoveBlocks` operates on the list AFTER removing the dragged item, so callers need
 * to adjust the index accordingly. The subtree drag reducer does not use this helper
 * because it has to reject gaps inside a multi-block payload and rewrite indentation
 * atomically.
 *
 * Prefer the subtree-aware drag reducer for editor drag completion. Keep this helper
 * only for flat reorder use cases.
 *
 * @param visualGap The visual gap position (0 to totalItemCount) from [calculateDropTargetIndex]
 * @param originalIndex The original index of the dragged item
 * @param totalItemCount Total number of items
 * @return The adjusted index for MoveBlocks, or null if the drop would result in no movement
 */
@Deprecated(
    message = "Use visual-gap drag completion for CascadeEditor. This helper is kept only for legacy flat MoveBlocks integrations.",
)
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
