package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.ClearSelection
import io.github.linreal.cascade.editor.action.DeleteBlock
import io.github.linreal.cascade.editor.action.DeleteSelectedOrFocused
import io.github.linreal.cascade.editor.action.FocusBlock
import io.github.linreal.cascade.editor.action.InsertBlockAfter
import io.github.linreal.cascade.editor.action.ToggleBlockSelection
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.SlashCommandState
import io.github.linreal.cascade.editor.state.SlashQueryRange
import io.github.linreal.cascade.editor.ui.shouldInvalidateSlashSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests that verify the block selection feature as a cohesive workflow,
 * spanning multiple action reducers working together (toggle, focus, delete, insert,
 * slash session invalidation).
 */
class BlockSelectionIntegrationTest {

    private val blockA = block("A")
    private val blockB = block("B")
    private val blockC = block("C")
    private val blockD = block("D")

    private fun block(id: String, text: String = ""): Block = Block(
        id = BlockId(id),
        type = BlockType.Paragraph,
        content = BlockContent.Text(text),
    )

    @Test
    fun `enter selection via toggle clears focus`() {
        val state = EditorState.withBlocks(listOf(blockA, blockB, blockC)).copy(
            focusedBlockId = blockA.id,
        )

        val result = ToggleBlockSelection(blockB.id).reduce(state)

        assertEquals(setOf(blockB.id), result.selectedBlockIds)
        assertNull(result.focusedBlockId)
    }

    @Test
    fun `multi-select via sequential toggles`() {
        val state = EditorState.withBlocks(listOf(blockA, blockB, blockC)).copy(
            selectedBlockIds = setOf(blockB.id),
        )

        val result = ToggleBlockSelection(blockA.id).reduce(state)

        assertEquals(setOf(blockB.id, blockA.id), result.selectedBlockIds)
        assertNull(result.focusedBlockId)
    }

    @Test
    fun `deselect last block exits selection mode`() {
        val state = EditorState.withBlocks(listOf(blockA, blockB, blockC)).copy(
            selectedBlockIds = setOf(blockB.id),
        )

        val result = ToggleBlockSelection(blockB.id).reduce(state)

        assertTrue(result.selectedBlockIds.isEmpty())
        assertNull(result.focusedBlockId)
    }

    @Test
    fun `focus after selection exit`() {
        val state = EditorState.withBlocks(listOf(blockA, blockB, blockC)).copy(
            selectedBlockIds = setOf(blockB.id),
        )

        var result = ToggleBlockSelection(blockB.id).reduce(state)
        result = FocusBlock(blockA.id).reduce(result)

        assertEquals(blockA.id, result.focusedBlockId)
        assertTrue(result.selectedBlockIds.isEmpty())
    }

    @Test
    fun `delete selected blocks`() {
        val state = EditorState.withBlocks(listOf(blockA, blockB, blockC)).copy(
            selectedBlockIds = setOf(blockA.id, blockC.id),
        )

        val result = DeleteSelectedOrFocused.reduce(state)

        assertEquals(listOf(blockB), result.blocks)
        assertTrue(result.selectedBlockIds.isEmpty())
        assertNull(result.focusedBlockId)
    }

    @Test
    fun `ClearSelection external action`() {
        val state = EditorState.withBlocks(listOf(blockA, blockB, blockC)).copy(
            selectedBlockIds = setOf(blockA.id, blockB.id),
        )

        val result = ClearSelection.reduce(state)

        assertTrue(result.selectedBlockIds.isEmpty())
        assertNull(result.focusedBlockId)
    }

    @Test
    fun `stale ID ignored`() {
        val state = EditorState.withBlocks(listOf(blockA, blockB))

        val result = ToggleBlockSelection(BlockId("nonexistent")).reduce(state)

        assertEquals(state, result)
    }

    @Test
    fun `selection preserved across block insertion`() {
        val state = EditorState.withBlocks(listOf(blockA, blockB)).copy(
            selectedBlockIds = setOf(blockA.id),
        )
        val newBlock = block("new")

        val result = InsertBlockAfter(newBlock, afterBlockId = blockA.id).reduce(state)

        assertTrue(result.selectedBlockIds.contains(blockA.id))
        assertEquals(3, result.blocks.size)
    }

    @Test
    fun `selection updated when selected block is deleted externally`() {
        val state = EditorState.withBlocks(listOf(blockA, blockB)).copy(
            selectedBlockIds = setOf(blockA.id, blockB.id),
        )

        val result = DeleteBlock(blockA.id).reduce(state)

        assertEquals(setOf(blockB.id), result.selectedBlockIds)
        assertNull(result.focusedBlockId)
    }

    @Test
    fun `slash session invalidated by selection`() {
        val state = EditorState.withBlocks(listOf(blockA, blockB)).copy(
            slashCommandState = SlashCommandState(
                anchorBlockId = blockA.id,
                query = "",
                queryRange = SlashQueryRange(start = 0, endExclusive = 1),
            ),
        )

        val withSelection = ToggleBlockSelection(blockB.id).reduce(state)

        assertTrue(shouldInvalidateSlashSession(withSelection))
    }

    @Test
    fun `full lifecycle - select, multi-select, delete, back to normal`() {
        var state = EditorState.withBlocks(listOf(blockA, blockB, blockC, blockD)).copy(
            focusedBlockId = blockA.id,
        )

        // Enter selection mode
        state = ToggleBlockSelection(blockB.id).reduce(state)
        assertEquals(setOf(blockB.id), state.selectedBlockIds)
        assertNull(state.focusedBlockId)
        assertTrue(state.hasSelection)

        // Add another to selection
        state = ToggleBlockSelection(blockD.id).reduce(state)
        assertEquals(setOf(blockB.id, blockD.id), state.selectedBlockIds)
        assertNull(state.focusedBlockId)

        // Delete selected
        state = DeleteSelectedOrFocused.reduce(state)
        assertEquals(listOf(blockA, blockC), state.blocks)
        assertTrue(state.selectedBlockIds.isEmpty())
        assertNull(state.focusedBlockId)
        assertFalse(state.hasSelection)

        // Restore normal focus
        state = FocusBlock(blockA.id).reduce(state)
        assertEquals(blockA.id, state.focusedBlockId)
        assertTrue(state.selectedBlockIds.isEmpty())
    }
}
