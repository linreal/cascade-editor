package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.*
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.state.EditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorStateTest {

    private fun createTestBlock(id: String, text: String = ""): Block {
        return Block(
            id = BlockId(id),
            type = BlockType.Paragraph,
            content = BlockContent.Text(text)
        )
    }

    @Test
    fun `insert block at end`() {
        val state = EditorState.Empty
        val block = createTestBlock("1", "Hello")

        val newState = InsertBlock(block).reduce(state)

        assertEquals(1, newState.blocks.size)
        assertEquals(block, newState.blocks[0])
    }

    @Test
    fun `insert block at specific index`() {
        val block1 = createTestBlock("1", "First")
        val block2 = createTestBlock("2", "Second")
        val block3 = createTestBlock("3", "Third")
        val state = EditorState.withBlocks(listOf(block1, block3))

        val newState = InsertBlock(block2, atIndex = 1).reduce(state)

        assertEquals(3, newState.blocks.size)
        assertEquals(block1, newState.blocks[0])
        assertEquals(block2, newState.blocks[1])
        assertEquals(block3, newState.blocks[2])
    }

    @Test
    fun `delete blocks removes blocks and clears selection`() {
        val block1 = createTestBlock("1")
        val block2 = createTestBlock("2")
        val block3 = createTestBlock("3")
        val state = EditorState(
            blocks = listOf(block1, block2, block3),
            focusedBlockId = BlockId("2"),
            selectedBlockIds = setOf(BlockId("1"), BlockId("2")),
            dragState = null,
            slashCommandState = null
        )

        val newState = DeleteBlocks(setOf(BlockId("1"), BlockId("2"))).reduce(state)

        assertEquals(1, newState.blocks.size)
        assertEquals(block3, newState.blocks[0])
        assertNull(newState.focusedBlockId)
        assertTrue(newState.selectedBlockIds.isEmpty())
    }

    @Test
    fun `update block content`() {
        val block = createTestBlock("1", "Old text")
        val state = EditorState.withBlocks(listOf(block))

        val newState = UpdateBlockText(BlockId("1"), "New text").reduce(state)

        assertEquals("New text", (newState.blocks[0].content as BlockContent.Text).text)
    }

    @Test
    fun `convert block type`() {
        val block = createTestBlock("1", "Hello")
        val state = EditorState.withBlocks(listOf(block))

        val newState = ConvertBlockType(BlockId("1"), BlockType.Heading(1)).reduce(state)

        assertEquals(BlockType.Heading(1), newState.blocks[0].type)
        assertEquals("Hello", (newState.blocks[0].content as BlockContent.Text).text)
    }

    @Test
    fun `select block clears previous selection`() {
        val block1 = createTestBlock("1")
        val block2 = createTestBlock("2")
        val state = EditorState(
            blocks = listOf(block1, block2),
            focusedBlockId = null,
            selectedBlockIds = setOf(BlockId("1")),
            dragState = null,
            slashCommandState = null
        )

        val newState = SelectBlock(BlockId("2")).reduce(state)

        assertEquals(setOf(BlockId("2")), newState.selectedBlockIds)
    }

    @Test
    fun `toggle selection adds and removes`() {
        val block1 = createTestBlock("1")
        val block2 = createTestBlock("2")
        val state = EditorState(
            blocks = listOf(block1, block2),
            focusedBlockId = null,
            selectedBlockIds = setOf(BlockId("1")),
            dragState = null,
            slashCommandState = null
        )

        // Add block2 to selection
        val state2 = ToggleBlockSelection(BlockId("2")).reduce(state)
        assertEquals(setOf(BlockId("1"), BlockId("2")), state2.selectedBlockIds)

        // Remove block1 from selection
        val state3 = ToggleBlockSelection(BlockId("1")).reduce(state2)
        assertEquals(setOf(BlockId("2")), state3.selectedBlockIds)
    }

    @Test
    fun `select range selects contiguous blocks`() {
        val block1 = createTestBlock("1")
        val block2 = createTestBlock("2")
        val block3 = createTestBlock("3")
        val block4 = createTestBlock("4")
        val state = EditorState.withBlocks(listOf(block1, block2, block3, block4))

        val newState = SelectBlockRange(BlockId("2"), BlockId("4")).reduce(state)

        assertEquals(setOf(BlockId("2"), BlockId("3"), BlockId("4")), newState.selectedBlockIds)
    }

    @Test
    fun `merge blocks combines text and removes source`() {
        val block1 = createTestBlock("1", "Hello ")
        val block2 = createTestBlock("2", "World")
        val state = EditorState.withBlocks(listOf(block1, block2))

        val newState = MergeBlocks(sourceId = BlockId("2"), targetId = BlockId("1")).reduce(state)

        assertEquals(1, newState.blocks.size)
        assertEquals("Hello World", (newState.blocks[0].content as BlockContent.Text).text)
        assertEquals(BlockId("1"), newState.focusedBlockId)
    }

    @Test
    fun `split block creates new block`() {
        val block = createTestBlock("1", "HelloWorld")
        val state = EditorState.withBlocks(listOf(block))

        val newState = SplitBlock(BlockId("1"), atPosition = 5).reduce(state)

        assertEquals(2, newState.blocks.size)
        assertEquals("Hello", (newState.blocks[0].content as BlockContent.Text).text)
        assertEquals("World", (newState.blocks[1].content as BlockContent.Text).text)
        assertEquals(newState.blocks[1].id, newState.focusedBlockId)
    }

    @Test
    fun `move blocks to new position`() {
        val block1 = createTestBlock("1")
        val block2 = createTestBlock("2")
        val block3 = createTestBlock("3")
        val block4 = createTestBlock("4")
        val state = EditorState.withBlocks(listOf(block1, block2, block3, block4))

        val newState = MoveBlocks(setOf(BlockId("1"), BlockId("2")), toIndex = 2).reduce(state)

        assertEquals(listOf(block3, block4, block1, block2).map { it.id }, newState.blocks.map { it.id })
    }

    @Test
    fun `select all selects all blocks`() {
        val block1 = createTestBlock("1")
        val block2 = createTestBlock("2")
        val block3 = createTestBlock("3")
        val state = EditorState.withBlocks(listOf(block1, block2, block3))

        val newState = SelectAll.reduce(state)

        assertEquals(setOf(BlockId("1"), BlockId("2"), BlockId("3")), newState.selectedBlockIds)
    }

    @Test
    fun `clear selection removes all selections`() {
        val block1 = createTestBlock("1")
        val block2 = createTestBlock("2")
        val state = EditorState(
            blocks = listOf(block1, block2),
            focusedBlockId = null,
            selectedBlockIds = setOf(BlockId("1"), BlockId("2")),
            dragState = null,
            slashCommandState = null
        )

        val newState = ClearSelection.reduce(state)

        assertTrue(newState.selectedBlockIds.isEmpty())
    }
}
