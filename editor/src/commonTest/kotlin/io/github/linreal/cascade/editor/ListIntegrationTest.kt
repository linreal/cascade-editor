package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.ConvertBlockType
import io.github.linreal.cascade.editor.action.DeleteBlock
import io.github.linreal.cascade.editor.action.MoveBlocks
import io.github.linreal.cascade.editor.action.SplitBlock
import io.github.linreal.cascade.editor.action.UpdateBlockContent
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.state.EditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Integration tests that verify multi-step list feature scenarios spanning
 * multiple action reducers working together (auto-detect, split, delete,
 * convert, move, renumbering).
 */
class ListIntegrationTest {

    private fun numberedBlock(id: String, text: String, number: Int = 1): Block = Block(
        id = BlockId(id),
        type = BlockType.NumberedList(number),
        content = BlockContent.Text(text),
    )

    private fun bulletBlock(id: String, text: String): Block = Block(
        id = BlockId(id),
        type = BlockType.BulletList,
        content = BlockContent.Text(text),
    )

    private fun paragraphBlock(id: String, text: String): Block = Block(
        id = BlockId(id),
        type = BlockType.Paragraph,
        content = BlockContent.Text(text),
    )

    private fun Block.atDepth(level: Int): Block = copy(
        attributes = BlockAttributes(indentationLevel = level),
    )

    private fun assertNumberedAt(state: EditorState, index: Int, expectedNumber: Int) {
        val type = state.blocks[index].type
        assertIs<BlockType.NumberedList>(type, "Block at index $index should be NumberedList")
        assertEquals(expectedNumber, type.number, "Block at index $index number mismatch")
    }

    // --- Integration: auto-detect → enter continuation → sequential numbers ---

    @Test
    fun `auto-detect then enter continuation produces sequential numbered list`() {
        // Step 1: Simulate auto-detect — user typed "1. " on a paragraph.
        // Observer removes prefix and converts. We start after prefix removal.
        val block = paragraphBlock("a", "First item")
        var state = EditorState.withBlocks(listOf(block))

        // ConvertBlockType to NumberedList(1)
        state = ConvertBlockType(BlockId("a"), BlockType.NumberedList(1)).reduce(state)
        assertNumberedAt(state, 0, 1)
        assertEquals("First item", (state.blocks[0].content as BlockContent.Text).text)

        // Step 2: Enter at end of "First item" → splits, new block gets NumberedList(2)
        state = SplitBlock(
            blockId = BlockId("a"),
            atPosition = 10,
            newBlockText = "Second item",
            newBlockId = BlockId("b"),
        ).reduce(state)
        assertEquals(2, state.blocks.size)
        assertNumberedAt(state, 0, 1)
        assertNumberedAt(state, 1, 2)

        // Step 3: Enter again on block "b"
        state = SplitBlock(
            blockId = BlockId("b"),
            atPosition = 11,
            newBlockText = "Third item",
            newBlockId = BlockId("c"),
        ).reduce(state)
        assertEquals(3, state.blocks.size)
        assertNumberedAt(state, 0, 1)
        assertNumberedAt(state, 1, 2)
        assertNumberedAt(state, 2, 3)
    }

    // --- Integration: delete middle item → renumbering ---

    @Test
    fun `delete middle numbered item renumbers remaining`() {
        val blocks = listOf(
            numberedBlock("a", "First", 1),
            numberedBlock("b", "Second", 2),
            numberedBlock("c", "Third", 3),
            numberedBlock("d", "Fourth", 4),
        )
        var state = EditorState.withBlocks(blocks)

        // Delete block "b" (item 2)
        state = DeleteBlock(BlockId("b")).reduce(state)

        assertEquals(3, state.blocks.size)
        assertNumberedAt(state, 0, 1)
        // "c" was 3, now renumbered to 2
        assertNumberedAt(state, 1, 2)
        // "d" was 4, now renumbered to 3
        assertNumberedAt(state, 2, 3)
    }

    // --- Integration: empty-enter exit → paragraph + renumbering ---

    @Test
    fun `empty-enter exit in middle converts to paragraph and renumbers remaining`() {
        val blocks = listOf(
            numberedBlock("a", "First", 1),
            numberedBlock("b", "Second", 2),
            numberedBlock("c", "", 3),  // empty list item
            numberedBlock("d", "Fourth", 4),
            numberedBlock("e", "Fifth", 5),
        )
        var state = EditorState.withBlocks(blocks)

        // User presses Enter on empty block "c" → onEnter dispatches ConvertBlockType
        state = ConvertBlockType(BlockId("c"), BlockType.Paragraph).reduce(state)

        assertEquals(5, state.blocks.size)
        // First run: a(1), b(2)
        assertNumberedAt(state, 0, 1)
        assertNumberedAt(state, 1, 2)
        // Block c is now Paragraph
        assertEquals(BlockType.Paragraph, state.blocks[2].type)
        // Second run: always starts from 1
        assertNumberedAt(state, 3, 1)
        assertNumberedAt(state, 4, 2)
    }

    // --- Integration: backspace un-list in middle → run splits, sub-runs renumber ---

    @Test
    fun `backspace un-list in middle splits run into two independent sub-runs`() {
        val blocks = listOf(
            numberedBlock("a", "First", 1),
            numberedBlock("b", "Second", 2),
            numberedBlock("c", "Third", 3),
            numberedBlock("d", "Fourth", 4),
            numberedBlock("e", "Fifth", 5),
        )
        var state = EditorState.withBlocks(blocks)

        // Backspace at start of block "c" → ConvertBlockType to Paragraph
        state = ConvertBlockType(BlockId("c"), BlockType.Paragraph).reduce(state)

        assertEquals(5, state.blocks.size)
        // First sub-run: a(1), b(2)
        assertNumberedAt(state, 0, 1)
        assertNumberedAt(state, 1, 2)
        // Block c is Paragraph, text preserved
        assertEquals(BlockType.Paragraph, state.blocks[2].type)
        assertEquals("Third", (state.blocks[2].content as BlockContent.Text).text)
        // Second sub-run: always starts from 1
        assertNumberedAt(state, 3, 1)
        assertNumberedAt(state, 4, 2)
    }

    // --- Integration: swap blocks within a run ---

    @Test
    fun `swap two numbered blocks renumbers correctly`() {
        // Two-item list: 1, 2
        val blocks = listOf(
            numberedBlock("a", "First", 1),
            numberedBlock("b", "Second", 2),
        )
        var state = EditorState.withBlocks(blocks)

        // Move block "a" to after block "b" (swap)
        state = MoveBlocks(setOf(BlockId("a")), toIndex = 2).reduce(state)

        // After swap: b, a — should be renumbered 1, 2
        assertEquals(2, state.blocks.size)
        assertEquals(BlockId("b"), state.blocks[0].id)
        assertEquals(BlockId("a"), state.blocks[1].id)
        assertNumberedAt(state, 0, 1)
        assertNumberedAt(state, 1, 2)
    }

    @Test
    fun `swap first and last in three-item list renumbers correctly`() {
        val blocks = listOf(
            numberedBlock("a", "First", 1),
            numberedBlock("b", "Second", 2),
            numberedBlock("c", "Third", 3),
        )
        var state = EditorState.withBlocks(blocks)

        // Move block "a" to after block "c"
        state = MoveBlocks(setOf(BlockId("a")), toIndex = 3).reduce(state)

        // After move: b, c, a — should be renumbered 1, 2, 3
        assertEquals(3, state.blocks.size)
        assertEquals(BlockId("b"), state.blocks[0].id)
        assertEquals(BlockId("c"), state.blocks[1].id)
        assertEquals(BlockId("a"), state.blocks[2].id)
        assertNumberedAt(state, 0, 1)
        assertNumberedAt(state, 1, 2)
        assertNumberedAt(state, 2, 3)
    }

    @Test
    fun `move last block to first position renumbers correctly`() {
        val blocks = listOf(
            numberedBlock("a", "First", 1),
            numberedBlock("b", "Second", 2),
            numberedBlock("c", "Third", 3),
        )
        var state = EditorState.withBlocks(blocks)

        // Move block "c" to position 0 (before all)
        state = MoveBlocks(setOf(BlockId("c")), toIndex = 0).reduce(state)

        // After move: c, a, b — should be renumbered 1, 2, 3
        assertEquals(3, state.blocks.size)
        assertEquals(BlockId("c"), state.blocks[0].id)
        assertEquals(BlockId("a"), state.blocks[1].id)
        assertEquals(BlockId("b"), state.blocks[2].id)
        assertNumberedAt(state, 0, 1)
        assertNumberedAt(state, 1, 2)
        assertNumberedAt(state, 2, 3)
    }

    // --- Integration: move blocks → both runs renumber ---

    @Test
    fun `move numbered item from one run to another renumbers both`() {
        // Two separate runs separated by a paragraph
        val blocks = listOf(
            numberedBlock("a", "A1", 1),
            numberedBlock("b", "A2", 2),
            numberedBlock("c", "A3", 3),
            paragraphBlock("sep", "---"),
            numberedBlock("d", "B1", 1),
            numberedBlock("e", "B2", 2),
        )
        var state = EditorState.withBlocks(blocks)

        // Move block "b" (A2) to after block "e" (index 6 in remaining list after removal)
        state = MoveBlocks(setOf(BlockId("b")), toIndex = 6).reduce(state)

        // After move: a, c, sep, d, e, b
        assertEquals(6, state.blocks.size)

        // Source run (a, c): a keeps base 1, c renumbered to 2
        assertNumberedAt(state, 0, 1)
        assertNumberedAt(state, 1, 2)

        // Separator
        assertEquals(BlockType.Paragraph, state.blocks[2].type)

        // Destination run (d, e, b): d keeps base 1, e=2, b=3
        assertNumberedAt(state, 3, 1)
        assertNumberedAt(state, 4, 2)
        assertNumberedAt(state, 5, 3)
    }

    // --- Integration: mid-text split → both blocks are list items with correct numbers ---

    @Test
    fun `mid-text split on numbered list preserves type and renumbers`() {
        val spans = listOf(
            TextSpan(0, 5, SpanStyle.Bold),  // "Hello" is bold
            TextSpan(5, 10, SpanStyle.Italic), // "World" is italic
        )
        val blocks = listOf(
            numberedBlock("a", "First", 1),
            Block(
                id = BlockId("b"),
                type = BlockType.NumberedList(2),
                content = BlockContent.Text("HelloWorld", spans),
            ),
            numberedBlock("c", "Third", 3),
        )
        var state = EditorState.withBlocks(blocks)

        // Split block "b" at position 5 (between "Hello" and "World")
        state = SplitBlock(
            blockId = BlockId("b"),
            atPosition = 5,
            newBlockText = "World",
            newBlockId = BlockId("new"),
        ).reduce(state)

        assertEquals(4, state.blocks.size)

        // All four blocks are NumberedList, sequentially numbered
        assertNumberedAt(state, 0, 1)  // "First"
        assertNumberedAt(state, 1, 2)  // "Hello"
        assertNumberedAt(state, 2, 3)  // "World" (new block)
        assertNumberedAt(state, 3, 4)  // "Third"

        // Verify text split
        assertEquals("Hello", (state.blocks[1].content as BlockContent.Text).text)
        assertEquals("World", (state.blocks[2].content as BlockContent.Text).text)

        // Verify spans split: source keeps [0,5) Bold, new block gets [0,5) Italic
        val sourceSpans = (state.blocks[1].content as BlockContent.Text).spans
        assertEquals(1, sourceSpans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Bold), sourceSpans[0])

        val newSpans = (state.blocks[2].content as BlockContent.Text).spans
        assertEquals(1, newSpans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Italic), newSpans[0])
    }

    @Test
    fun `mid-text split on bullet list preserves BulletList type on both blocks`() {
        val blocks = listOf(
            bulletBlock("a", "First"),
            bulletBlock("b", "HelloWorld"),
            bulletBlock("c", "Third"),
        )
        var state = EditorState.withBlocks(blocks)

        state = SplitBlock(
            blockId = BlockId("b"),
            atPosition = 5,
            newBlockText = "World",
            newBlockId = BlockId("new"),
        ).reduce(state)

        assertEquals(4, state.blocks.size)
        assertEquals(BlockType.BulletList, state.blocks[0].type)
        assertEquals(BlockType.BulletList, state.blocks[1].type)
        assertEquals(BlockType.BulletList, state.blocks[2].type)
        assertEquals(BlockType.BulletList, state.blocks[3].type)
    }

    @Test
    fun `auto-detect conversion preserves source paragraph indentation`() {
        val blocks = listOf(
            paragraphBlock("parent", "Parent"),
            paragraphBlock("child", "- Item").atDepth(1),
        )
        var state = EditorState.withBlocks(blocks)

        // Simulate the observer pipeline after detecting "- ": remove the trigger prefix,
        // then convert the same block to a list type.
        state = UpdateBlockContent(
            blockId = BlockId("child"),
            content = BlockContent.Text("Item"),
        ).reduce(state)
        state = ConvertBlockType(BlockId("child"), BlockType.BulletList).reduce(state)

        assertEquals(BlockType.BulletList, state.blocks[1].type)
        assertEquals(1, state.blocks[1].attributes.indentationLevel)
        assertEquals("Item", (state.blocks[1].content as BlockContent.Text).text)
    }

    // --- Integration: combined multi-step scenario ---

    @Test
    fun `full lifecycle - create list then add items then delete middle then exit at end`() {
        // Step 1: Start with a paragraph, simulate auto-detect to NumberedList(1)
        var state = EditorState.withBlocks(listOf(paragraphBlock("a", "Item one")))
        state = ConvertBlockType(BlockId("a"), BlockType.NumberedList(1)).reduce(state)

        // Step 2: Enter to create items 2, 3, 4
        state = SplitBlock(BlockId("a"), 8, "Item two", BlockId("b")).reduce(state)
        state = SplitBlock(BlockId("b"), 8, "Item three", BlockId("c")).reduce(state)
        state = SplitBlock(BlockId("c"), 10, "", BlockId("d")).reduce(state)

        assertEquals(4, state.blocks.size)
        assertNumberedAt(state, 0, 1)
        assertNumberedAt(state, 1, 2)
        assertNumberedAt(state, 2, 3)
        assertNumberedAt(state, 3, 4)

        // Step 3: Delete item 2 (block "b")
        state = DeleteBlock(BlockId("b")).reduce(state)
        assertEquals(3, state.blocks.size)
        assertNumberedAt(state, 0, 1)
        assertNumberedAt(state, 1, 2)  // was 3, renumbered
        assertNumberedAt(state, 2, 3)  // was 4, renumbered

        // Step 4: Empty-enter exit on last item (block "d", now at index 2, empty text)
        state = ConvertBlockType(BlockId("d"), BlockType.Paragraph).reduce(state)
        assertEquals(3, state.blocks.size)
        assertNumberedAt(state, 0, 1)
        assertNumberedAt(state, 1, 2)
        assertEquals(BlockType.Paragraph, state.blocks[2].type)
    }
}
