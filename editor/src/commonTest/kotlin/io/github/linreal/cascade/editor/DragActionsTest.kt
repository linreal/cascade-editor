package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.*
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.state.DragState
import io.github.linreal.cascade.editor.state.EditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DragActionsTest {

    private fun createTestBlock(id: String, text: String = ""): Block {
        return Block(
            id = BlockId(id),
            type = BlockType.Paragraph,
            content = BlockContent.Text(text)
        )
    }

    private fun createTestBlock(
        id: String,
        type: BlockType,
        indentationLevel: Int,
        text: String = id,
    ): Block {
        return Block(
            id = BlockId(id),
            type = type,
            content = BlockContent.Text(text),
            attributes = BlockAttributes(indentationLevel = indentationLevel),
        )
    }

    private fun createFiveBlockState(): EditorState {
        return EditorState.withBlocks(
            listOf(
                createTestBlock("A", "Alpha"),
                createTestBlock("B", "Beta"),
                createTestBlock("C", "Charlie"),
                createTestBlock("D", "Delta"),
                createTestBlock("E", "Echo")
            )
        )
    }

    // =========================================================================
    // StartDrag
    // =========================================================================

    @Test
    fun `StartDrag creates drag state with correct block`() {
        val state = createFiveBlockState()

        val newState = StartDrag(BlockId("C"), touchOffsetY = 15f).reduce(state)

        assertNotNull(newState.dragState)
        assertEquals(setOf(BlockId("C")), newState.dragState!!.draggingBlockIds)
    }

    @Test
    fun `StartDrag records original index`() {
        val state = createFiveBlockState()

        val newState = StartDrag(BlockId("C"), touchOffsetY = 15f).reduce(state)

        assertEquals(2, newState.dragState!!.primaryBlockOriginalIndex)
    }

    @Test
    fun `StartDrag stores touch offset`() {
        val state = createFiveBlockState()

        val newState = StartDrag(BlockId("B"), touchOffsetY = 42.5f).reduce(state)

        assertEquals(42.5f, newState.dragState!!.initialTouchOffsetY)
    }

    @Test
    fun `StartDrag sets null target index`() {
        val state = createFiveBlockState()

        val newState = StartDrag(BlockId("A"), touchOffsetY = 0f).reduce(state)

        assertNull(newState.dragState!!.targetIndex)
    }

    @Test
    fun `StartDrag with first block records index 0`() {
        val state = createFiveBlockState()

        val newState = StartDrag(BlockId("A"), touchOffsetY = 0f).reduce(state)

        assertEquals(0, newState.dragState!!.primaryBlockOriginalIndex)
    }

    @Test
    fun `StartDrag with last block records last index`() {
        val state = createFiveBlockState()

        val newState = StartDrag(BlockId("E"), touchOffsetY = 0f).reduce(state)

        assertEquals(4, newState.dragState!!.primaryBlockOriginalIndex)
    }

    @Test
    fun `StartDrag with unknown block ID is no-op`() {
        val state = createFiveBlockState()

        val newState = StartDrag(BlockId("unknown"), touchOffsetY = 0f).reduce(state)

        assertNull(newState.dragState)
    }

    @Test
    fun `StartDrag does not modify blocks`() {
        val state = createFiveBlockState()

        val newState = StartDrag(BlockId("C"), touchOffsetY = 0f).reduce(state)

        assertEquals(state.blocks, newState.blocks)
    }

    @Test
    fun `StartDrag on parent includes full subtree payload in document order`() {
        val state = EditorState.withBlocks(
            listOf(
                createTestBlock("A", BlockType.Paragraph, indentationLevel = 0),
                createTestBlock("B", BlockType.Paragraph, indentationLevel = 0),
                createTestBlock("C", BlockType.Paragraph, indentationLevel = 1),
                createTestBlock("D", BlockType.Paragraph, indentationLevel = 2),
                createTestBlock("E", BlockType.Paragraph, indentationLevel = 0),
            )
        )

        val newState = StartDrag(BlockId("B"), touchOffsetY = 0f).reduce(state)

        val dragState = assertNotNull(newState.dragState)
        assertEquals(listOf(BlockId("B")), dragState.dragRootIds)
        assertEquals(listOf(BlockId("B"), BlockId("C"), BlockId("D")), dragState.payloadBlockIds)
        assertEquals(setOf(BlockId("B"), BlockId("C"), BlockId("D")), dragState.payloadBlockIdSet)
        assertEquals(listOf(1, 2, 3), dragState.payloadBlockIndices)
        assertEquals(setOf(1, 2, 3), dragState.payloadBlockIndexSet)
        assertEquals(listOf(1..3), dragState.payloadIndexRanges)
        assertEquals(setOf(BlockId("B"), BlockId("C"), BlockId("D")), dragState.draggingBlockIds)
        assertEquals(mapOf(BlockId("B") to 0), dragState.originalRootIndentationLevels)
        assertEquals(
            mapOf(
                BlockId("B") to 0,
                BlockId("C") to 1,
                BlockId("D") to 2,
            ),
            dragState.payloadRelativeDepthOffsets,
        )
    }

    @Test
    fun `StartDrag on selected parent and child includes child only once`() {
        val state = EditorState.withBlocks(
            listOf(
                createTestBlock("A", BlockType.Paragraph, indentationLevel = 0),
                createTestBlock("B", BlockType.Paragraph, indentationLevel = 0),
                createTestBlock("C", BlockType.Paragraph, indentationLevel = 1),
                createTestBlock("D", BlockType.Paragraph, indentationLevel = 2),
                createTestBlock("E", BlockType.Paragraph, indentationLevel = 0),
            )
        ).copy(
            selectedBlockIds = setOf(BlockId("B"), BlockId("C")),
        )

        val newState = StartDrag(BlockId("B"), touchOffsetY = 0f).reduce(state)

        val dragState = assertNotNull(newState.dragState)
        assertEquals(listOf(BlockId("B")), dragState.dragRootIds)
        assertEquals(listOf(BlockId("B"), BlockId("C"), BlockId("D")), dragState.payloadBlockIds)
    }

    @Test
    fun `StartDrag with two selected roots preserves root and payload document order`() {
        val state = EditorState.withBlocks(
            listOf(
                createTestBlock("A", BlockType.Paragraph, indentationLevel = 0),
                createTestBlock("B", BlockType.Paragraph, indentationLevel = 0),
                createTestBlock("C", BlockType.Paragraph, indentationLevel = 1),
                createTestBlock("D", BlockType.Paragraph, indentationLevel = 0),
                createTestBlock("E", BlockType.Paragraph, indentationLevel = 1),
                createTestBlock("F", BlockType.Paragraph, indentationLevel = 0),
            )
        ).copy(
            selectedBlockIds = setOf(BlockId("D"), BlockId("B")),
        )

        val newState = StartDrag(BlockId("D"), touchOffsetY = 0f).reduce(state)

        val dragState = assertNotNull(newState.dragState)
        assertEquals(listOf(BlockId("B"), BlockId("D")), dragState.dragRootIds)
        assertEquals(listOf(BlockId("B"), BlockId("C"), BlockId("D"), BlockId("E")), dragState.payloadBlockIds)
        assertEquals(BlockId("D"), dragState.primaryRootId)
    }

    // =========================================================================
    // UpdateDragTarget
    // =========================================================================

    @Test
    fun `UpdateDragTarget sets target index`() {
        val state = createFiveBlockState()
        val dragging = StartDrag(BlockId("B"), touchOffsetY = 0f).reduce(state)

        val newState = UpdateDragTarget(3).reduce(dragging)

        assertEquals(3, newState.dragState!!.targetIndex)
    }

    @Test
    fun `UpdateDragTarget updates existing target`() {
        val state = createFiveBlockState()
        val dragging = StartDrag(BlockId("B"), touchOffsetY = 0f).reduce(state)
        val withTarget = UpdateDragTarget(1).reduce(dragging)

        val newState = UpdateDragTarget(4).reduce(withTarget)

        assertEquals(4, newState.dragState!!.targetIndex)
    }

    @Test
    fun `UpdateDragTarget to null clears target`() {
        val state = createFiveBlockState()
        val dragging = StartDrag(BlockId("B"), touchOffsetY = 0f).reduce(state)
        val withTarget = UpdateDragTarget(3).reduce(dragging)

        val newState = UpdateDragTarget(null).reduce(withTarget)

        assertNull(newState.dragState!!.targetIndex)
    }

    @Test
    fun `UpdateDragTarget without active drag is no-op`() {
        val state = createFiveBlockState()

        val newState = UpdateDragTarget(3).reduce(state)

        assertNull(newState.dragState)
    }

    @Test
    fun `UpdateDragTarget preserves other drag state fields`() {
        val state = createFiveBlockState()
        val dragging = StartDrag(BlockId("C"), touchOffsetY = 25f).reduce(state)

        val newState = UpdateDragTarget(4).reduce(dragging)

        assertEquals(setOf(BlockId("C")), newState.dragState!!.draggingBlockIds)
        assertEquals(25f, newState.dragState!!.initialTouchOffsetY)
        assertEquals(2, newState.dragState!!.primaryBlockOriginalIndex)
    }

    @Test
    fun `UpdateDragTarget updates future root indentation level`() {
        val state = createFiveBlockState()
        val dragging = StartDrag(BlockId("C"), touchOffsetY = 25f).reduce(state)

        val newState = UpdateDragTarget(targetIndex = 4, futureRootIndentationLevel = 1).reduce(dragging)

        assertEquals(4, newState.dragState!!.targetIndex)
        assertEquals(1, newState.dragState!!.futureRootIndentationLevel)
    }

    // =========================================================================
    // CompleteDrag
    // =========================================================================

    @Test
    fun `CompleteDrag moves block forward`() {
        // [A, B, C, D, E] — drag B (index 1) to gap 4 (between D and E)
        // Visual gap 4, original 1 → moveIndex = 3
        // After remove B: [A, C, D, E] → insert at 3 → [A, C, D, B, E]
        val state = createFiveBlockState()
        val dragging = StartDrag(BlockId("B"), touchOffsetY = 0f).reduce(state)
        val targeted = UpdateDragTarget(4).reduce(dragging)

        val newState = CompleteDrag.reduce(targeted)

        assertNull(newState.dragState)
        assertEquals(
            listOf("A", "C", "D", "B", "E"),
            newState.blocks.map { it.id.value }
        )
    }

    @Test
    fun `CompleteDrag moves block backward`() {
        // [A, B, C, D, E] — drag D (index 3) to gap 1 (between A and B)
        // Visual gap 1, original 3 → moveIndex = 1
        // After remove D: [A, B, C, E] → insert at 1 → [A, D, B, C, E]
        val state = createFiveBlockState()
        val dragging = StartDrag(BlockId("D"), touchOffsetY = 0f).reduce(state)
        val targeted = UpdateDragTarget(1).reduce(dragging)

        val newState = CompleteDrag.reduce(targeted)

        assertNull(newState.dragState)
        assertEquals(
            listOf("A", "D", "B", "C", "E"),
            newState.blocks.map { it.id.value }
        )
    }

    @Test
    fun `CompleteDrag moves block to start`() {
        // [A, B, C, D, E] — drag C (index 2) to gap 0
        val state = createFiveBlockState()
        val dragging = StartDrag(BlockId("C"), touchOffsetY = 0f).reduce(state)
        val targeted = UpdateDragTarget(0).reduce(dragging)

        val newState = CompleteDrag.reduce(targeted)

        assertEquals(
            listOf("C", "A", "B", "D", "E"),
            newState.blocks.map { it.id.value }
        )
    }

    @Test
    fun `CompleteDrag moves block to end`() {
        // [A, B, C, D, E] — drag B (index 1) to gap 5 (after everything)
        // Visual gap 5, original 1 → moveIndex = 4
        val state = createFiveBlockState()
        val dragging = StartDrag(BlockId("B"), touchOffsetY = 0f).reduce(state)
        val targeted = UpdateDragTarget(5).reduce(dragging)

        val newState = CompleteDrag.reduce(targeted)

        assertEquals(
            listOf("A", "C", "D", "E", "B"),
            newState.blocks.map { it.id.value }
        )
    }

    @Test
    fun `CompleteDrag at original position does not reorder`() {
        // [A, B, C, D, E] — drag C (index 2) to gap 2 (same position)
        val state = createFiveBlockState()
        val dragging = StartDrag(BlockId("C"), touchOffsetY = 0f).reduce(state)
        val targeted = UpdateDragTarget(2).reduce(dragging)

        val newState = CompleteDrag.reduce(targeted)

        assertNull(newState.dragState)
        assertEquals(
            listOf("A", "B", "C", "D", "E"),
            newState.blocks.map { it.id.value }
        )
    }

    @Test
    fun `CompleteDrag at original position with future depth changes indentation`() {
        val state = createFiveBlockState()
        val dragging = StartDrag(BlockId("C"), touchOffsetY = 0f).reduce(state)
        val targeted = UpdateDragTarget(
            targetIndex = 2,
            futureRootIndentationLevel = 1,
        ).reduce(dragging)

        val newState = CompleteDrag.reduce(targeted)

        assertNull(newState.dragState)
        assertEquals(
            listOf("A", "B", "C", "D", "E"),
            newState.blocks.map { it.id.value }
        )
        assertEquals(listOf(0, 0, 1, 0, 0), newState.blocks.map { it.attributes.indentationLevel })
    }

    @Test
    fun `CompleteDrag at gap after original does not reorder`() {
        // Gap 3 for original index 2 = immediately after → no movement
        val state = createFiveBlockState()
        val dragging = StartDrag(BlockId("C"), touchOffsetY = 0f).reduce(state)
        val targeted = UpdateDragTarget(3).reduce(dragging)

        val newState = CompleteDrag.reduce(targeted)

        assertNull(newState.dragState)
        assertEquals(
            listOf("A", "B", "C", "D", "E"),
            newState.blocks.map { it.id.value }
        )
    }

    @Test
    fun `CompleteDrag without target index clears drag state`() {
        val state = createFiveBlockState()
        val dragging = StartDrag(BlockId("B"), touchOffsetY = 0f).reduce(state)
        // No UpdateDragTarget — targetIndex is null

        val newState = CompleteDrag.reduce(dragging)

        assertNull(newState.dragState)
        assertEquals(state.blocks, newState.blocks) // blocks unchanged
    }

    @Test
    fun `CompleteDrag after invalid hover target does not mutate document`() {
        val state = EditorState.withBlocks(
            listOf(
                createTestBlock("A", BlockType.Paragraph, indentationLevel = 0),
                createTestBlock("B", BlockType.Paragraph, indentationLevel = 0),
            )
        )
        val dragging = StartDrag(BlockId("B"), touchOffsetY = 0f).reduce(state)
        val invalidTarget = UpdateDragTarget(
            targetIndex = null,
            futureRootIndentationLevel = 1,
        ).reduce(dragging)

        val newState = CompleteDrag.reduce(invalidTarget)

        assertNull(newState.dragState)
        assertEquals(listOf("A", "B"), newState.blocks.map { it.id.value })
        assertEquals(listOf(0, 0), newState.blocks.map { it.attributes.indentationLevel })
    }

    @Test
    fun `CompleteDrag without active drag is no-op`() {
        val state = createFiveBlockState()

        val newState = CompleteDrag.reduce(state)

        assertEquals(state, newState)
    }

    @Test
    fun `CompleteDrag preserves block content`() {
        val state = createFiveBlockState()
        val dragging = StartDrag(BlockId("B"), touchOffsetY = 0f).reduce(state)
        val targeted = UpdateDragTarget(4).reduce(dragging)

        val newState = CompleteDrag.reduce(targeted)

        val movedBlock = newState.blocks.find { it.id == BlockId("B") }
        assertNotNull(movedBlock)
        assertEquals("Beta", (movedBlock.content as BlockContent.Text).text)
    }

    @Test
    fun `CompleteDrag rejects drop into payload gap`() {
        val state = EditorState.withBlocks(
            listOf(
                createTestBlock("A", BlockType.Paragraph, indentationLevel = 0),
                createTestBlock("B", BlockType.Paragraph, indentationLevel = 0),
                createTestBlock("C", BlockType.Paragraph, indentationLevel = 1),
                createTestBlock("D", BlockType.Paragraph, indentationLevel = 1),
                createTestBlock("E", BlockType.Paragraph, indentationLevel = 0),
            )
        )
        val dragging = StartDrag(BlockId("B"), touchOffsetY = 0f).reduce(state)
        val targeted = UpdateDragTarget(targetIndex = 2).reduce(dragging)

        val newState = CompleteDrag.reduce(targeted)

        assertNull(newState.dragState)
        assertEquals(listOf("A", "B", "C", "D", "E"), newState.blocks.map { it.id.value })
        assertEquals(listOf(0, 0, 1, 1, 0), newState.blocks.map { it.attributes.indentationLevel })
    }

    @Test
    fun `CompleteDrag moves subtree with future depth and renumbers lists`() {
        val state = EditorState.withBlocks(
            listOf(
                createTestBlock("A", BlockType.NumberedList(9), indentationLevel = 0),
                createTestBlock("B", BlockType.NumberedList(9), indentationLevel = 0),
                createTestBlock("C", BlockType.Paragraph, indentationLevel = 0),
                createTestBlock("D", BlockType.NumberedList(9), indentationLevel = 0),
                createTestBlock("E", BlockType.NumberedList(9), indentationLevel = 1),
            )
        )
        val dragging = StartDrag(BlockId("D"), touchOffsetY = 0f).reduce(state)
        val targeted = UpdateDragTarget(targetIndex = 2, futureRootIndentationLevel = 1).reduce(dragging)

        val newState = CompleteDrag.reduce(targeted)

        assertEquals(listOf("A", "B", "D", "E", "C"), newState.blocks.map { it.id.value })
        assertEquals(listOf(0, 0, 1, 2, 0), newState.blocks.map { it.attributes.indentationLevel })
        assertEquals(
            listOf(
                BlockType.NumberedList(1),
                BlockType.NumberedList(2),
                BlockType.NumberedList(1),
                BlockType.NumberedList(1),
                BlockType.Paragraph,
            ),
            newState.blocks.map { it.type },
        )
    }

    // =========================================================================
    // CancelDrag
    // =========================================================================

    @Test
    fun `CancelDrag clears drag state`() {
        val state = createFiveBlockState()
        val dragging = StartDrag(BlockId("C"), touchOffsetY = 0f).reduce(state)
        val targeted = UpdateDragTarget(0).reduce(dragging)

        val newState = CancelDrag.reduce(targeted)

        assertNull(newState.dragState)
    }

    @Test
    fun `CancelDrag preserves blocks in original order`() {
        val state = createFiveBlockState()
        val dragging = StartDrag(BlockId("C"), touchOffsetY = 0f).reduce(state)
        val targeted = UpdateDragTarget(0).reduce(dragging)

        val newState = CancelDrag.reduce(targeted)

        assertEquals(
            listOf("A", "B", "C", "D", "E"),
            newState.blocks.map { it.id.value }
        )
    }

    @Test
    fun `CancelDrag without active drag is no-op`() {
        val state = createFiveBlockState()

        val newState = CancelDrag.reduce(state)

        assertEquals(state, newState)
    }

    // =========================================================================
    // Full drag lifecycle
    // =========================================================================

    @Test
    fun `full drag lifecycle - start target complete`() {
        val state = createFiveBlockState()

        // Start drag on C
        val s1 = StartDrag(BlockId("C"), touchOffsetY = 10f).reduce(state)
        assertNotNull(s1.dragState)
        assertEquals(5, s1.blocks.size)

        // Update target to gap 0 (before A)
        val s2 = UpdateDragTarget(0).reduce(s1)
        assertEquals(0, s2.dragState!!.targetIndex)

        // Change mind — target to gap 4 (between D and E)
        val s3 = UpdateDragTarget(4).reduce(s2)
        assertEquals(4, s3.dragState!!.targetIndex)

        // Complete
        val s4 = CompleteDrag.reduce(s3)
        assertNull(s4.dragState)
        assertEquals(
            listOf("A", "B", "D", "C", "E"),
            s4.blocks.map { it.id.value }
        )
    }

    @Test
    fun `full drag lifecycle - start target cancel`() {
        val state = createFiveBlockState()

        val s1 = StartDrag(BlockId("C"), touchOffsetY = 10f).reduce(state)
        val s2 = UpdateDragTarget(0).reduce(s1)
        val s3 = CancelDrag.reduce(s2)

        assertNull(s3.dragState)
        assertEquals(state.blocks.map { it.id }, s3.blocks.map { it.id })
    }

    // =========================================================================
    // Edge cases with two-item lists
    // =========================================================================

    @Test
    fun `two items - drag first to after second`() {
        val state = EditorState.withBlocks(
            listOf(
                createTestBlock("A"),
                createTestBlock("B")
            )
        )
        val s1 = StartDrag(BlockId("A"), touchOffsetY = 0f).reduce(state)
        val s2 = UpdateDragTarget(2).reduce(s1) // gap after B
        val s3 = CompleteDrag.reduce(s2)

        assertEquals(listOf("B", "A"), s3.blocks.map { it.id.value })
    }

    @Test
    fun `two items - drag second to before first`() {
        val state = EditorState.withBlocks(
            listOf(
                createTestBlock("A"),
                createTestBlock("B")
            )
        )
        val s1 = StartDrag(BlockId("B"), touchOffsetY = 0f).reduce(state)
        val s2 = UpdateDragTarget(0).reduce(s1) // gap before A
        val s3 = CompleteDrag.reduce(s2)

        assertEquals(listOf("B", "A"), s3.blocks.map { it.id.value })
    }

    // =========================================================================
    // Drag state does not leak into other state fields
    // =========================================================================

    @Test
    fun `drag operations preserve focus`() {
        val state = createFiveBlockState().copy(focusedBlockId = BlockId("A"))
        val s1 = StartDrag(BlockId("C"), touchOffsetY = 0f).reduce(state)
        val s2 = UpdateDragTarget(0).reduce(s1)
        val s3 = CompleteDrag.reduce(s2)

        assertEquals(BlockId("A"), s3.focusedBlockId)
    }

    @Test
    fun `drag operations preserve selection`() {
        val state = createFiveBlockState().copy(selectedBlockIds = setOf(BlockId("A"), BlockId("B")))
        val s1 = StartDrag(BlockId("D"), touchOffsetY = 0f).reduce(state)
        val s2 = UpdateDragTarget(0).reduce(s1)
        val s3 = CompleteDrag.reduce(s2)

        assertEquals(setOf(BlockId("A"), BlockId("B")), s3.selectedBlockIds)
    }
}
