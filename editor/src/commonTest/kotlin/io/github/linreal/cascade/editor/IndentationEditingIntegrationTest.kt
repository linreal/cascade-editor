package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IndentationEditingIntegrationTest {

    private class Harness(
        blocks: List<Block>,
        focusedBlockId: BlockId?,
        selectedBlockIds: Set<BlockId> = emptySet(),
    ) {
        var state: EditorState = EditorState.withBlocks(blocks).copy(
            focusedBlockId = focusedBlockId,
            selectedBlockIds = selectedBlockIds,
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        init {
            for (block in blocks) {
                val content = block.content as? BlockContent.Text
                val text = content?.text.orEmpty()
                textStates.getOrCreate(block.id, text)
                spanStates.getOrCreate(block.id, content?.spans.orEmpty(), text.length)
            }
        }

        val callbacks = DefaultBlockCallbacks(
            dispatchFn = { action: EditorAction -> state = action.reduce(state) },
            stateProvider = { state },
            textStates = textStates,
            spanStates = spanStates,
        )
    }

    @Test
    fun `enter on depth 2 numbered list with text creates same-depth numbered item and renumbers`() {
        val focusedId = BlockId("leaf")
        val harness = Harness(
            blocks = listOf(
                numberedBlock("root", "Root", number = 1, depth = 0),
                numberedBlock("child", "Child", number = 1, depth = 1),
                numberedBlock("leaf", "Leaf", number = 1, depth = 2),
                numberedBlock("sibling", "Sibling", number = 2, depth = 2),
            ),
            focusedBlockId = focusedId,
        )

        harness.callbacks.onEnter(focusedId, cursorPosition = 4)

        assertEquals(5, harness.state.blocks.size)
        assertNumberedAt(harness.state, index = 2, expectedNumber = 1)
        assertNumberedAt(harness.state, index = 3, expectedNumber = 2)
        assertNumberedAt(harness.state, index = 4, expectedNumber = 3)
        assertEquals(2, harness.state.blocks[3].attributes.indentationLevel)
        assertEquals("", (harness.state.blocks[3].content as BlockContent.Text).text)
    }

    @Test
    fun `enter on empty nested bullet list outdents and keeps list type`() {
        val focusedId = BlockId("leaf")
        val harness = Harness(
            blocks = listOf(
                paragraphBlock("root", "Root", depth = 0),
                bulletBlock("child", "Child", depth = 1),
                bulletBlock("leaf", "", depth = 2),
            ),
            focusedBlockId = focusedId,
        )

        harness.callbacks.onEnter(focusedId, cursorPosition = 0)

        assertEquals(3, harness.state.blocks.size)
        assertEquals(BlockType.BulletList, harness.state.blocks[2].type)
        assertEquals(1, harness.state.blocks[2].attributes.indentationLevel)
    }

    @Test
    fun `enter on empty root bullet list exits to paragraph at depth 0`() {
        val focusedId = BlockId("item")
        val harness = Harness(
            blocks = listOf(bulletBlock("item", "", depth = 0)),
            focusedBlockId = focusedId,
        )

        harness.callbacks.onEnter(focusedId, cursorPosition = 0)

        assertEquals(1, harness.state.blocks.size)
        assertEquals(BlockType.Paragraph, harness.state.blocks[0].type)
        assertEquals(0, harness.state.blocks[0].attributes.indentationLevel)
    }

    @Test
    fun `enter on empty root todo keeps split continuation behavior`() {
        val focusedId = BlockId("todo")
        val harness = Harness(
            blocks = listOf(todoBlock("todo", "", checked = true, depth = 0)),
            focusedBlockId = focusedId,
        )

        harness.callbacks.onEnter(focusedId, cursorPosition = 0)

        assertEquals(2, harness.state.blocks.size)
        assertEquals(BlockType.Todo(checked = true), harness.state.blocks[0].type)
        assertEquals(BlockType.Todo(checked = false), harness.state.blocks[1].type)
        assertEquals(0, harness.state.blocks[0].attributes.indentationLevel)
        assertEquals(0, harness.state.blocks[1].attributes.indentationLevel)
    }

    @Test
    fun `enter on empty nested todo outdents and keeps checked state`() {
        val focusedId = BlockId("todo")
        val harness = Harness(
            blocks = listOf(
                paragraphBlock("root", "Root", depth = 0),
                todoBlock("todo", "", checked = true, depth = 1),
            ),
            focusedBlockId = focusedId,
        )

        harness.callbacks.onEnter(focusedId, cursorPosition = 0)

        assertEquals(2, harness.state.blocks.size)
        assertEquals(BlockType.Todo(checked = true), harness.state.blocks[1].type)
        assertEquals(0, harness.state.blocks[1].attributes.indentationLevel)
    }

    @Test
    fun `enter on empty indented paragraph splits to same-depth paragraph`() {
        val focusedId = BlockId("child")
        val harness = Harness(
            blocks = listOf(
                paragraphBlock("root", "Root", depth = 0),
                paragraphBlock("child", "", depth = 1),
            ),
            focusedBlockId = focusedId,
        )

        harness.callbacks.onEnter(focusedId, cursorPosition = 0)

        assertEquals(3, harness.state.blocks.size)
        assertEquals(BlockType.Paragraph, harness.state.blocks[1].type)
        assertEquals(BlockType.Paragraph, harness.state.blocks[2].type)
        assertEquals(1, harness.state.blocks[1].attributes.indentationLevel)
        assertEquals(1, harness.state.blocks[2].attributes.indentationLevel)
    }

    @Test
    fun `enter is ignored while block selection is active`() {
        val selectedId = BlockId("selected")
        val fieldBlockId = BlockId("field")
        val harness = Harness(
            blocks = listOf(
                paragraphBlock("selected", "Selected", depth = 0),
                paragraphBlock("field", "Field", depth = 0),
            ),
            focusedBlockId = null,
            selectedBlockIds = setOf(selectedId),
        )
        val originalBlocks = harness.state.blocks

        harness.callbacks.onEnter(fieldBlockId, cursorPosition = 2)

        assertEquals(originalBlocks, harness.state.blocks)
        assertEquals(setOf(selectedId), harness.state.selectedBlockIds)
    }

    @Test
    fun `backspace at start is ignored while block selection is active`() {
        val selectedId = BlockId("child")
        val harness = Harness(
            blocks = listOf(
                paragraphBlock("root", "Root", depth = 0),
                paragraphBlock("child", "Child", depth = 1),
            ),
            focusedBlockId = null,
            selectedBlockIds = setOf(selectedId),
        )
        val originalBlocks = harness.state.blocks

        harness.callbacks.onBackspaceAtStart(selectedId)

        assertEquals(originalBlocks, harness.state.blocks)
        assertEquals(setOf(selectedId), harness.state.selectedBlockIds)
    }

    @Test
    fun `backspace at start of nested paragraph outdents and does not merge`() {
        val focusedId = BlockId("child")
        val harness = Harness(
            blocks = listOf(
                paragraphBlock("root", "Root", depth = 0),
                paragraphBlock("child", "Child", depth = 1),
            ),
            focusedBlockId = focusedId,
        )

        harness.callbacks.onBackspaceAtStart(focusedId)

        assertEquals(2, harness.state.blocks.size)
        assertEquals("Root", (harness.state.blocks[0].content as BlockContent.Text).text)
        assertEquals("Child", (harness.state.blocks[1].content as BlockContent.Text).text)
        assertEquals(0, harness.state.blocks[1].attributes.indentationLevel)
    }

    @Test
    fun `backspace at start of root todo still converts to paragraph`() {
        val focusedId = BlockId("todo")
        val harness = Harness(
            blocks = listOf(
                paragraphBlock("root", "Root", depth = 0),
                todoBlock("todo", "Task", checked = true, depth = 0),
            ),
            focusedBlockId = focusedId,
        )

        harness.callbacks.onBackspaceAtStart(focusedId)

        assertEquals(2, harness.state.blocks.size)
        assertEquals(BlockType.Paragraph, harness.state.blocks[1].type)
        assertEquals("Task", (harness.state.blocks[1].content as BlockContent.Text).text)
        assertEquals(0, harness.state.blocks[1].attributes.indentationLevel)
    }

    @Test
    fun `backspace outdent of nested numbered list renumbers parent sequence`() {
        val focusedId = BlockId("child")
        val harness = Harness(
            blocks = listOf(
                numberedBlock("first", "First", number = 1, depth = 0),
                numberedBlock("child", "Child", number = 1, depth = 1),
                numberedBlock("second", "Second", number = 2, depth = 0),
            ),
            focusedBlockId = focusedId,
        )

        harness.callbacks.onBackspaceAtStart(focusedId)

        assertEquals(3, harness.state.blocks.size)
        assertNumberedAt(harness.state, index = 0, expectedNumber = 1)
        assertNumberedAt(harness.state, index = 1, expectedNumber = 2)
        assertNumberedAt(harness.state, index = 2, expectedNumber = 3)
        assertEquals(0, harness.state.blocks[1].attributes.indentationLevel)
    }

    private fun paragraphBlock(
        id: String,
        text: String,
        depth: Int,
    ): Block = textBlock(
        id = id,
        type = BlockType.Paragraph,
        text = text,
        depth = depth,
    )

    private fun bulletBlock(
        id: String,
        text: String,
        depth: Int,
    ): Block = textBlock(
        id = id,
        type = BlockType.BulletList,
        text = text,
        depth = depth,
    )

    private fun numberedBlock(
        id: String,
        text: String,
        number: Int,
        depth: Int,
    ): Block = textBlock(
        id = id,
        type = BlockType.NumberedList(number),
        text = text,
        depth = depth,
    )

    private fun todoBlock(
        id: String,
        text: String,
        checked: Boolean,
        depth: Int,
    ): Block = textBlock(
        id = id,
        type = BlockType.Todo(checked),
        text = text,
        depth = depth,
    )

    private fun textBlock(
        id: String,
        type: BlockType,
        text: String,
        depth: Int,
    ): Block = Block(
        id = BlockId(id),
        type = type,
        content = BlockContent.Text(text),
        attributes = BlockAttributes(indentationLevel = depth),
    )

    private fun assertNumberedAt(
        state: EditorState,
        index: Int,
        expectedNumber: Int,
    ) {
        val type = assertIs<BlockType.NumberedList>(state.blocks[index].type)
        assertEquals(expectedNumber, type.number)
    }
}
