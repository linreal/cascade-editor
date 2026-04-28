package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.StartDrag
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.ui.calculateDropIndicatorGeometry
import io.github.linreal.cascade.editor.ui.dragPreviewPayloadBadgeText
import io.github.linreal.cascade.editor.ui.utils.DragHoverOutlineIndex
import io.github.linreal.cascade.editor.ui.utils.convertVisualGapToMoveBlocksIndex
import io.github.linreal.cascade.editor.ui.utils.resolveDepthAwareDragHoverTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for pure drag utility functions that don't depend on Compose UI types.
 *
 * Note: [calculateDropTargetIndex] and [calculateDropIndicatorY] depend on
 * [LazyListLayoutInfo] and require compose-foundation in test dependencies
 * to create fakes. They should be tested via UI/integration tests or after
 * adding the compose-foundation test dependency.
 */
@Suppress("DEPRECATION")
class DragUtilsTest {

    private fun block(
        id: String,
        depth: Int,
        type: BlockType = BlockType.Paragraph,
    ): Block {
        return Block(
            id = BlockId(id),
            type = type,
            content = BlockContent.Text(id),
            attributes = BlockAttributes(indentationLevel = depth),
        )
    }

    private fun draggingState(
        blocks: List<Block>,
        draggedBlockId: String,
    ): EditorState {
        return StartDrag(BlockId(draggedBlockId), touchOffsetY = 0f)
            .reduce(EditorState.withBlocks(blocks))
    }

    // =========================================================================
    // resolveDepthAwareDragHoverTarget
    // =========================================================================

    @Test
    fun `hover target allows free depth beyond previous block plus one`() {
        val state = draggingState(
            blocks = listOf(
                block("A", depth = 0),
                block("B", depth = 1),
                block("C", depth = 0),
            ),
            draggedBlockId = "C",
        )

        val target = resolveDepthAwareDragHoverTarget(
            blocks = state.blocks,
            dragState = state.dragState,
            visualGap = 2,
            horizontalDragDeltaPx = 72f,
            indentUnitPx = 24f,
        )

        assertEquals(2, target?.visualGap)
        assertEquals(3, target?.futureRootIndentationLevel)
    }

    @Test
    fun `hover target before first block allows free indentation`() {
        val state = draggingState(
            blocks = listOf(
                block("A", depth = 0),
                block("B", depth = 0),
            ),
            draggedBlockId = "B",
        )

        val target = resolveDepthAwareDragHoverTarget(
            blocks = state.blocks,
            dragState = state.dragState,
            visualGap = 0,
            horizontalDragDeltaPx = 48f,
            indentUnitPx = 24f,
        )

        assertEquals(0, target?.visualGap)
        assertEquals(2, target?.futureRootIndentationLevel)
    }

    @Test
    fun `hover target raises future depth to avoid adopting following non-payload block`() {
        val state = draggingState(
            blocks = listOf(
                block("A", depth = 0),
                block("B", depth = 1),
                block("C", depth = 2),
                block("D", depth = 0),
                block("E", depth = 1),
                block("F", depth = 2),
            ),
            draggedBlockId = "D",
        )

        val target = resolveDepthAwareDragHoverTarget(
            blocks = state.blocks,
            dragState = state.dragState,
            visualGap = 2,
            horizontalDragDeltaPx = 0f,
            indentUnitPx = 24f,
        )

        assertEquals(2, target?.visualGap)
        assertEquals(2, target?.futureRootIndentationLevel)
    }

    @Test
    fun `hover target allows subtree to move until deepest dragged descendant reaches max depth`() {
        val state = draggingState(
            blocks = listOf(
                block("A", depth = 0),
                block("B", depth = 0),
                block("C", depth = 1),
                block("D", depth = 2),
            ),
            draggedBlockId = "B",
        )

        val target = resolveDepthAwareDragHoverTarget(
            blocks = state.blocks,
            dragState = state.dragState,
            visualGap = 1,
            horizontalDragDeltaPx = 72f,
            indentUnitPx = 24f,
        )

        assertEquals(1, target?.visualGap)
        assertEquals(3, target?.futureRootIndentationLevel)
    }

    @Test
    fun `hover target is invalid for gaps inside drag payload`() {
        val state = draggingState(
            blocks = listOf(
                block("A", depth = 0),
                block("B", depth = 0),
                block("C", depth = 1),
                block("D", depth = 0),
            ),
            draggedBlockId = "B",
        )

        val target = resolveDepthAwareDragHoverTarget(
            blocks = state.blocks,
            dragState = state.dragState,
            visualGap = 2,
            horizontalDragDeltaPx = 0f,
            indentUnitPx = 24f,
        )

        assertNull(target)
    }

    @Test
    fun `hover target keeps starting depth when horizontal movement is less than one indent unit`() {
        val state = draggingState(
            blocks = listOf(
                block("A", depth = 0),
                block("B", depth = 1),
                block("C", depth = 0),
            ),
            draggedBlockId = "B",
        )

        val target = resolveDepthAwareDragHoverTarget(
            blocks = state.blocks,
            dragState = state.dragState,
            visualGap = 1,
            horizontalDragDeltaPx = 23f,
            indentUnitPx = 24f,
        )

        assertEquals(1, target?.visualGap)
        assertEquals(1, target?.futureRootIndentationLevel)
    }

    @Test
    fun `hover target keeps unsupported primary root at depth zero during horizontal drag`() {
        val state = draggingState(
            blocks = listOf(
                block("A", depth = 0),
                block("B", depth = 1),
                block("C", depth = 1),
                block("H", depth = 0, type = BlockType.Heading(1)),
            ),
            draggedBlockId = "H",
        )

        val target = resolveDepthAwareDragHoverTarget(
            blocks = state.blocks,
            dragState = state.dragState,
            visualGap = 2,
            horizontalDragDeltaPx = 72f,
            indentUnitPx = 24f,
        )

        assertEquals(2, target?.visualGap)
        assertEquals(0, target?.futureRootIndentationLevel)
    }

    @Test
    fun `hover target allows free indentation after previous unsupported block`() {
        val state = draggingState(
            blocks = listOf(
                block("A", depth = 0),
                block("H", depth = 0, type = BlockType.Heading(1)),
                block("B", depth = 0),
            ),
            draggedBlockId = "B",
        )

        val target = resolveDepthAwareDragHoverTarget(
            outlineIndex = DragHoverOutlineIndex(state.blocks),
            dragState = state.dragState,
            visualGap = 2,
            horizontalDragDeltaPx = 24f,
            indentUnitPx = 24f,
        )

        assertEquals(2, target?.visualGap)
        assertEquals(1, target?.futureRootIndentationLevel)
    }

    @Test
    fun `hover target clamps next depth against shallowest moved root in multi-root drag`() {
        val state = EditorState.withBlocks(
            listOf(
                block("R", depth = 0),
                block("P", depth = 1),
                block("C", depth = 1),
                block("A", depth = 0),
                block("X", depth = 0),
                block("B", depth = 1),
            )
        ).copy(
            selectedBlockIds = setOf(BlockId("A"), BlockId("B")),
        ).let { selectedState ->
            StartDrag(BlockId("B"), touchOffsetY = 0f).reduce(selectedState)
        }

        val target = resolveDepthAwareDragHoverTarget(
            blocks = state.blocks,
            dragState = state.dragState,
            visualGap = 2,
            horizontalDragDeltaPx = 0f,
            indentUnitPx = 24f,
        )

        assertEquals(2, target?.visualGap)
        assertEquals(2, target?.futureRootIndentationLevel)
    }

    // =========================================================================
    // Drag indicator and preview pure geometry
    // =========================================================================

    @Test
    fun `indicator geometry at depth zero starts at base padding`() {
        val geometry = calculateDropIndicatorGeometry(
            viewportWidthPx = 320f,
            futureRootIndentationLevel = 0f,
            blockHorizontalPaddingPx = 16f,
            indentUnitPx = 24f,
        )

        assertEquals(16f, geometry?.startX)
        assertEquals(304f, geometry?.endX)
    }

    @Test
    fun `indicator geometry at depth two includes two indent units`() {
        val geometry = calculateDropIndicatorGeometry(
            viewportWidthPx = 320f,
            futureRootIndentationLevel = 2f,
            blockHorizontalPaddingPx = 16f,
            indentUnitPx = 24f,
        )

        assertEquals(64f, geometry?.startX)
        assertEquals(304f, geometry?.endX)
    }

    @Test
    fun `indicator geometry returns null when line would be inverted`() {
        val geometry = calculateDropIndicatorGeometry(
            viewportWidthPx = 80f,
            futureRootIndentationLevel = 3f,
            blockHorizontalPaddingPx = 16f,
            indentUnitPx = 24f,
        )

        assertNull(geometry)
    }

    @Test
    fun `preview badge text reports additional payload blocks`() {
        assertNull(dragPreviewPayloadBadgeText(payloadBlockCount = 1))
        assertEquals("+2", dragPreviewPayloadBadgeText(payloadBlockCount = 3))
    }

    // =========================================================================
    // convertVisualGapToMoveBlocksIndex
    // =========================================================================

    // List: [A(0), B(1), C(2), D(3), E(4)] — 5 items

    @Test
    fun `dropping at original position returns null`() {
        // Dragging item at index 2, dropping at gap 2 (before item 2) = same position
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 2, originalIndex = 2, totalItemCount = 5))
    }

    @Test
    fun `dropping immediately after original position returns null`() {
        // Dragging item at index 2, dropping at gap 3 (after item 2) = same position
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 3, originalIndex = 2, totalItemCount = 5))
    }

    @Test
    fun `dropping before original position returns gap directly`() {
        // Dragging item at index 3, dropping at gap 1 (between A and B)
        // After removing D: [A, B, C, E] → insert at index 1 → [A, D, B, C, E]
        assertEquals(1, convertVisualGapToMoveBlocksIndex(visualGap = 1, originalIndex = 3, totalItemCount = 5))
    }

    @Test
    fun `dropping at start returns 0`() {
        // Dragging item at index 3, dropping at gap 0 (before everything)
        assertEquals(0, convertVisualGapToMoveBlocksIndex(visualGap = 0, originalIndex = 3, totalItemCount = 5))
    }

    @Test
    fun `dropping after original position adjusts for removal`() {
        // Dragging item at index 1, dropping at gap 4 (between D and E)
        // After removing B: [A, C, D, E] → insert at index 3 → [A, C, D, B, E]
        assertEquals(3, convertVisualGapToMoveBlocksIndex(visualGap = 4, originalIndex = 1, totalItemCount = 5))
    }

    @Test
    fun `dropping at end after original position`() {
        // Dragging item at index 1, dropping at gap 5 (after everything)
        // After removing B: [A, C, D, E] → insert at index 4 → [A, C, D, E, B]
        assertEquals(4, convertVisualGapToMoveBlocksIndex(visualGap = 5, originalIndex = 1, totalItemCount = 5))
    }

    @Test
    fun `dropping at end when already last returns null`() {
        // Dragging last item (index 4), dropping at gap 5 (after everything) = same position
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 5, originalIndex = 4, totalItemCount = 5))
    }

    @Test
    fun `dropping at start when already first returns null`() {
        // Dragging first item (index 0), dropping at gap 0 = same position
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 0, originalIndex = 0, totalItemCount = 5))
    }

    @Test
    fun `dropping first item at gap 1 returns null`() {
        // Gap 1 is immediately after item 0 — no movement
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 1, originalIndex = 0, totalItemCount = 5))
    }

    @Test
    fun `dropping last item at gap N-1 returns null`() {
        // Dragging item 4, gap 4 = before item 4 = same position
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 4, originalIndex = 4, totalItemCount = 5))
    }

    @Test
    fun `two item list - swap forward`() {
        // [A(0), B(1)] — drag A to after B
        // Gap 2 (after B), original 0 → adjust: 2-1=1 → [B, A]
        assertEquals(1, convertVisualGapToMoveBlocksIndex(visualGap = 2, originalIndex = 0, totalItemCount = 2))
    }

    @Test
    fun `two item list - swap backward`() {
        // [A(0), B(1)] — drag B to before A
        // Gap 0, original 1 → no adjust: 0 → [B, A]
        assertEquals(0, convertVisualGapToMoveBlocksIndex(visualGap = 0, originalIndex = 1, totalItemCount = 2))
    }

    @Test
    fun `result is clamped to valid range`() {
        // Edge case: gap beyond total count
        val result = convertVisualGapToMoveBlocksIndex(visualGap = 10, originalIndex = 0, totalItemCount = 5)
        // 10 > 0, so adjusted = 9, clamped to 4 (totalItemCount - 1)
        assertEquals(4, result)
    }

    @Test
    fun `single item list returns null for any gap`() {
        // Only one item — can't move anywhere
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 0, originalIndex = 0, totalItemCount = 1))
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 1, originalIndex = 0, totalItemCount = 1))
    }

    // Verify specific scenarios end-to-end:
    // List [A, B, C, D, E], drag C (index=2)

    @Test
    fun `drag C to before A`() {
        // Gap 0, original 2 → result 0 → [C, A, B, D, E]
        assertEquals(0, convertVisualGapToMoveBlocksIndex(visualGap = 0, originalIndex = 2, totalItemCount = 5))
    }

    @Test
    fun `drag C to between A and B`() {
        // Gap 1, original 2 → result 1 → [A, C, B, D, E]
        assertEquals(1, convertVisualGapToMoveBlocksIndex(visualGap = 1, originalIndex = 2, totalItemCount = 5))
    }

    @Test
    fun `drag C stays at C position`() {
        // Gap 2 or 3, original 2 → null (no movement)
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 2, originalIndex = 2, totalItemCount = 5))
        assertNull(convertVisualGapToMoveBlocksIndex(visualGap = 3, originalIndex = 2, totalItemCount = 5))
    }

    @Test
    fun `drag C to between D and E`() {
        // Gap 4, original 2 → adjusted 4-1=3 → [A, B, D, C, E]
        assertEquals(3, convertVisualGapToMoveBlocksIndex(visualGap = 4, originalIndex = 2, totalItemCount = 5))
    }

    @Test
    fun `drag C to after E`() {
        // Gap 5, original 2 → adjusted 5-1=4 → [A, B, D, E, C]
        assertEquals(4, convertVisualGapToMoveBlocksIndex(visualGap = 5, originalIndex = 2, totalItemCount = 5))
    }
}
