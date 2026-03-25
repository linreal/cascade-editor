package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.DeleteBlock
import io.github.linreal.cascade.editor.action.InsertBlock
import io.github.linreal.cascade.editor.action.MoveBlocks
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrailingTextBlockTest {

    private fun textBlock(id: String, text: String = ""): Block = Block(
        id = BlockId(id),
        type = BlockType.Paragraph,
        content = BlockContent.Text(text)
    )

    private fun dividerBlock(id: String): Block = Block(
        id = BlockId(id),
        type = BlockType.Divider,
        content = BlockContent.Empty
    )

    // --- ensureTrailingTextBlock (pure function) ---

    @Test
    fun `empty blocks get a trailing paragraph`() {
        val state = EditorState.Empty
        val result = state.ensureTrailingTextBlock()

        assertEquals(1, result.blocks.size)
        assertTrue(result.blocks.last().type.supportsText)
        assertEquals(BlockType.Paragraph, result.blocks.last().type)
    }

    @Test
    fun `blocks ending with divider get a trailing paragraph`() {
        val state = EditorState.withBlocks(listOf(textBlock("1", "Hello"), dividerBlock("2")))
        val result = state.ensureTrailingTextBlock()

        assertEquals(3, result.blocks.size)
        assertEquals(BlockType.Paragraph, result.blocks.last().type)
    }

    @Test
    fun `blocks ending with text block are unchanged`() {
        val state = EditorState.withBlocks(listOf(textBlock("1", "Hello")))
        val result = state.ensureTrailingTextBlock()

        assertEquals(1, result.blocks.size)
        assertEquals(state, result)
    }

    @Test
    fun `blocks ending with heading are unchanged`() {
        val block = Block(
            id = BlockId("1"),
            type = BlockType.Heading(1),
            content = BlockContent.Text("Title")
        )
        val state = EditorState.withBlocks(listOf(block))
        val result = state.ensureTrailingTextBlock()

        assertEquals(1, result.blocks.size)
        assertEquals(state, result)
    }

    @Test
    fun `blocks ending with todo are unchanged`() {
        val block = Block(
            id = BlockId("1"),
            type = BlockType.Todo(false),
            content = BlockContent.Text("Task")
        )
        val state = EditorState.withBlocks(listOf(block))
        val result = state.ensureTrailingTextBlock()

        assertEquals(1, result.blocks.size)
        assertEquals(state, result)
    }

    @Test
    fun `only divider gets a trailing paragraph`() {
        val state = EditorState.withBlocks(listOf(dividerBlock("1")))
        val result = state.ensureTrailingTextBlock()

        assertEquals(2, result.blocks.size)
        assertEquals(BlockType.Divider, result.blocks[0].type)
        assertEquals(BlockType.Paragraph, result.blocks[1].type)
    }

    // --- EditorStateHolder enforcement ---

    @Test
    fun `holder with empty initial state has trailing paragraph`() {
        val holder = EditorStateHolder()
        assertEquals(1, holder.state.blocks.size)
        assertEquals(BlockType.Paragraph, holder.state.blocks.last().type)
    }

    @Test
    fun `holder with divider-only initial state has trailing paragraph`() {
        val holder = EditorStateHolder(EditorState.withBlocks(listOf(dividerBlock("1"))))
        assertEquals(2, holder.state.blocks.size)
        assertEquals(BlockType.Paragraph, holder.state.blocks.last().type)
    }

    @Test
    fun `dispatch that leaves divider last adds trailing paragraph`() {
        val holder = EditorStateHolder(
            EditorState.withBlocks(listOf(dividerBlock("1"), textBlock("2", "text")))
        )
        holder.dispatch(DeleteBlock(BlockId("2")))

        assertTrue(holder.state.blocks.size >= 2)
        assertEquals(BlockType.Paragraph, holder.state.blocks.last().type)
    }

    @Test
    fun `dispatch that leaves text last does not add extra paragraph`() {
        val holder = EditorStateHolder(
            EditorState.withBlocks(listOf(textBlock("1", "Hello"), dividerBlock("2")))
        )
        // After init, trailing paragraph was already added (3 blocks).
        // Delete the divider → text block "1" is last, but trailing paragraph follows it.
        holder.dispatch(DeleteBlock(BlockId("2")))

        // Last block should be a text block — no extra paragraphs piling up
        assertTrue(holder.state.blocks.last().type.supportsText)
    }

    @Test
    fun `setState enforces trailing paragraph`() {
        val holder = EditorStateHolder()
        holder.setState(EditorState.withBlocks(listOf(dividerBlock("1"))))

        assertEquals(2, holder.state.blocks.size)
        assertEquals(BlockType.Paragraph, holder.state.blocks.last().type)
    }

    @Test
    fun `idempotent - already valid state not modified`() {
        val blocks = listOf(textBlock("1", "Hello"))
        val holder = EditorStateHolder(EditorState.withBlocks(blocks))

        assertEquals(1, holder.state.blocks.size)
        assertEquals(blocks, holder.state.blocks)
    }
}
