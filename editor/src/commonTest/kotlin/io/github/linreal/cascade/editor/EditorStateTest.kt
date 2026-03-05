package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.*
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
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
    fun `split block uses provided new block id`() {
        val block = createTestBlock("1", "HelloWorld")
        val state = EditorState.withBlocks(listOf(block))
        val expectedNewId = BlockId("split-target")

        val newState = SplitBlock(
            blockId = BlockId("1"),
            atPosition = 5,
            newBlockId = expectedNewId,
        ).reduce(state)

        assertEquals(expectedNewId, newState.blocks[1].id)
        assertEquals(expectedNewId, newState.focusedBlockId)
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

    // ── Helpers with spans ──────────────────────────────────────────────

    private fun createTestBlockWithSpans(
        id: String,
        text: String,
        spans: List<TextSpan>,
    ): Block = Block(
        id = BlockId(id),
        type = BlockType.Paragraph,
        content = BlockContent.Text(text, spans),
    )

    // ── ApplySpanStyle ──────────────────────────────────────────────────

    @Test
    fun `apply span style adds bold to empty spans`() {
        val block = createTestBlock("1", "Hello World")
        val state = EditorState.withBlocks(listOf(block))

        val newState = ApplySpanStyle(BlockId("1"), 0, 5, SpanStyle.Bold).reduce(state)

        val content = newState.blocks[0].content as BlockContent.Text
        assertEquals(1, content.spans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Bold), content.spans[0])
    }

    @Test
    fun `apply span style merges adjacent same-style spans`() {
        val block = createTestBlockWithSpans(
            "1", "Hello World",
            listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )
        val state = EditorState.withBlocks(listOf(block))

        val newState = ApplySpanStyle(BlockId("1"), 5, 11, SpanStyle.Bold).reduce(state)

        val content = newState.blocks[0].content as BlockContent.Text
        assertEquals(1, content.spans.size)
        assertEquals(TextSpan(0, 11, SpanStyle.Bold), content.spans[0])
    }

    @Test
    fun `apply span style preserves different styles`() {
        val block = createTestBlockWithSpans(
            "1", "Hello World",
            listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )
        val state = EditorState.withBlocks(listOf(block))

        val newState = ApplySpanStyle(BlockId("1"), 6, 11, SpanStyle.Italic).reduce(state)

        val content = newState.blocks[0].content as BlockContent.Text
        assertEquals(2, content.spans.size)
    }

    @Test
    fun `apply span style clamps to text length`() {
        val block = createTestBlock("1", "Hello")
        val state = EditorState.withBlocks(listOf(block))

        val newState = ApplySpanStyle(BlockId("1"), 0, 100, SpanStyle.Bold).reduce(state)

        val content = newState.blocks[0].content as BlockContent.Text
        assertEquals(1, content.spans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Bold), content.spans[0])
    }

    @Test
    fun `apply span style no-op for non-text block`() {
        val block = Block(BlockId("1"), BlockType.Divider, BlockContent.Empty)
        val state = EditorState.withBlocks(listOf(block))

        val newState = ApplySpanStyle(BlockId("1"), 0, 5, SpanStyle.Bold).reduce(state)

        assertEquals(state, newState)
    }

    @Test
    fun `apply span style no-op for unknown block`() {
        val block = createTestBlock("1", "Hello")
        val state = EditorState.withBlocks(listOf(block))

        val newState = ApplySpanStyle(BlockId("unknown"), 0, 5, SpanStyle.Bold).reduce(state)

        assertEquals(state, newState)
    }

    @Test
    fun `apply span style produces canonical snapshot with merged overlaps`() {
        val block = createTestBlockWithSpans(
            "1", "Hello World",
            listOf(
                TextSpan(0, 3, SpanStyle.Bold),
                TextSpan(3, 5, SpanStyle.Bold),
            ),
        )
        val state = EditorState.withBlocks(listOf(block))

        val newState = ApplySpanStyle(BlockId("1"), 5, 8, SpanStyle.Italic).reduce(state)

        val content = newState.blocks[0].content as BlockContent.Text
        val boldSpans = content.spans.filter { it.style == SpanStyle.Bold }
        assertEquals(1, boldSpans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Bold), boldSpans[0])
    }

    // ── RemoveSpanStyle ─────────────────────────────────────────────────

    @Test
    fun `remove span style removes style from range`() {
        val block = createTestBlockWithSpans(
            "1", "Hello World",
            listOf(TextSpan(0, 11, SpanStyle.Bold)),
        )
        val state = EditorState.withBlocks(listOf(block))

        val newState = RemoveSpanStyle(BlockId("1"), 0, 5, SpanStyle.Bold).reduce(state)

        val content = newState.blocks[0].content as BlockContent.Text
        assertEquals(1, content.spans.size)
        assertEquals(TextSpan(5, 11, SpanStyle.Bold), content.spans[0])
    }

    @Test
    fun `remove span style splits spanning range`() {
        val block = createTestBlockWithSpans(
            "1", "Hello World",
            listOf(TextSpan(0, 11, SpanStyle.Bold)),
        )
        val state = EditorState.withBlocks(listOf(block))

        val newState = RemoveSpanStyle(BlockId("1"), 3, 8, SpanStyle.Bold).reduce(state)

        val content = newState.blocks[0].content as BlockContent.Text
        assertEquals(2, content.spans.size)
        assertEquals(TextSpan(0, 3, SpanStyle.Bold), content.spans[0])
        assertEquals(TextSpan(8, 11, SpanStyle.Bold), content.spans[1])
    }

    @Test
    fun `remove span style preserves other styles`() {
        val block = createTestBlockWithSpans(
            "1", "Hello World",
            listOf(
                TextSpan(0, 11, SpanStyle.Bold),
                TextSpan(0, 11, SpanStyle.Italic),
            ),
        )
        val state = EditorState.withBlocks(listOf(block))

        val newState = RemoveSpanStyle(BlockId("1"), 0, 11, SpanStyle.Bold).reduce(state)

        val content = newState.blocks[0].content as BlockContent.Text
        assertEquals(1, content.spans.size)
        assertEquals(TextSpan(0, 11, SpanStyle.Italic), content.spans[0])
    }

    @Test
    fun `remove span style no-op for absent style`() {
        val block = createTestBlockWithSpans(
            "1", "Hello",
            listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )
        val state = EditorState.withBlocks(listOf(block))

        val newState = RemoveSpanStyle(BlockId("1"), 0, 5, SpanStyle.Italic).reduce(state)

        val content = newState.blocks[0].content as BlockContent.Text
        assertEquals(1, content.spans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Bold), content.spans[0])
    }

    @Test
    fun `remove span style produces normalized output`() {
        val block = createTestBlockWithSpans(
            "1", "Hello World",
            listOf(TextSpan(0, 11, SpanStyle.Bold)),
        )
        val state = EditorState.withBlocks(listOf(block))

        // Remove middle — should produce two sorted, non-overlapping spans
        val newState = RemoveSpanStyle(BlockId("1"), 4, 6, SpanStyle.Bold).reduce(state)

        val content = newState.blocks[0].content as BlockContent.Text
        assertEquals(2, content.spans.size)
        assertTrue(content.spans[0].start <= content.spans[1].start)
    }

    // ── SplitBlock with spans ───────────────────────────────────────────

    @Test
    fun `split block splits snapshot spans — fallback path`() {
        val block = createTestBlockWithSpans(
            "1", "HelloWorld",
            listOf(TextSpan(0, 10, SpanStyle.Bold)),
        )
        val state = EditorState.withBlocks(listOf(block))

        val newState = SplitBlock(BlockId("1"), atPosition = 5).reduce(state)

        val firstContent = newState.blocks[0].content as BlockContent.Text
        assertEquals("Hello", firstContent.text)
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), firstContent.spans)

        val secondContent = newState.blocks[1].content as BlockContent.Text
        assertEquals("World", secondContent.text)
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), secondContent.spans)
    }

    @Test
    fun `split block with newBlockText preserves new block spans`() {
        val block = createTestBlockWithSpans(
            "1", "HelloWorld",
            listOf(TextSpan(0, 10, SpanStyle.Bold)),
        )
        val state = EditorState.withBlocks(listOf(block))
        val newBlockId = BlockId("new")

        val newState = SplitBlock(
            blockId = BlockId("1"),
            atPosition = 5,
            newBlockText = "World",
            newBlockId = newBlockId,
        ).reduce(state)

        val newBlockContent = newState.blocks[1].content as BlockContent.Text
        assertEquals("World", newBlockContent.text)
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), newBlockContent.spans)
    }

    @Test
    fun `split block clips crossing spans at boundary`() {
        val block = createTestBlockWithSpans(
            "1", "HelloWorld",
            listOf(TextSpan(2, 8, SpanStyle.Italic)),
        )
        val state = EditorState.withBlocks(listOf(block))

        val newState = SplitBlock(BlockId("1"), atPosition = 5).reduce(state)

        val firstContent = newState.blocks[0].content as BlockContent.Text
        assertEquals(listOf(TextSpan(2, 5, SpanStyle.Italic)), firstContent.spans)

        val secondContent = newState.blocks[1].content as BlockContent.Text
        assertEquals(listOf(TextSpan(0, 3, SpanStyle.Italic)), secondContent.spans)
    }

    @Test
    fun `split block at start gives empty source spans`() {
        val block = createTestBlockWithSpans(
            "1", "Hello",
            listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )
        val state = EditorState.withBlocks(listOf(block))

        val newState = SplitBlock(BlockId("1"), atPosition = 0).reduce(state)

        val firstContent = newState.blocks[0].content as BlockContent.Text
        assertEquals("", firstContent.text)
        assertTrue(firstContent.spans.isEmpty())

        val secondContent = newState.blocks[1].content as BlockContent.Text
        assertEquals("Hello", secondContent.text)
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), secondContent.spans)
    }

    @Test
    fun `split block with newBlockSpans uses provided spans for new block`() {
        val block = createTestBlockWithSpans(
            "1", "HelloWorld",
            listOf(TextSpan(0, 10, SpanStyle.Bold)),
        )
        val state = EditorState.withBlocks(listOf(block))
        val runtimeSpans = listOf(TextSpan(0, 5, SpanStyle.Italic))

        val newState = SplitBlock(
            blockId = BlockId("1"),
            atPosition = 5,
            newBlockText = "World",
            newBlockId = BlockId("new"),
            newBlockSpans = runtimeSpans,
        ).reduce(state)

        // Source block uses snapshot-computed beforeSpans
        val sourceContent = newState.blocks[0].content as BlockContent.Text
        assertEquals("Hello", sourceContent.text)
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), sourceContent.spans)

        // New block uses runtime-provided spans (overrides snapshot fallback)
        val newBlockContent = newState.blocks[1].content as BlockContent.Text
        assertEquals("World", newBlockContent.text)
        assertEquals(runtimeSpans, newBlockContent.spans)
    }

    @Test
    fun `split block uses provided runtime source payload for source snapshot`() {
        val block = createTestBlockWithSpans(
            "1", "HelloWorld",
            listOf(TextSpan(0, 10, SpanStyle.Bold)),
        )
        val state = EditorState.withBlocks(listOf(block))

        val newState = SplitBlock(
            blockId = BlockId("1"),
            atPosition = 5,
            newBlockText = "World",
            newBlockId = BlockId("new"),
            newBlockSpans = listOf(TextSpan(0, 100, SpanStyle.Italic)),
            sourceBlockText = "Hello",
            sourceBlockSpans = listOf(TextSpan(0, 100, SpanStyle.Bold)),
        ).reduce(state)

        val sourceContent = newState.blocks[0].content as BlockContent.Text
        assertEquals("Hello", sourceContent.text)
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Bold)), sourceContent.spans)

        val newBlockContent = newState.blocks[1].content as BlockContent.Text
        assertEquals("World", newBlockContent.text)
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Italic)), newBlockContent.spans)
    }

    // ── MergeBlocks with spans ──────────────────────────────────────────

    @Test
    fun `merge blocks merges snapshot spans`() {
        val block1 = createTestBlockWithSpans(
            "1", "Hello ",
            listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )
        val block2 = createTestBlockWithSpans(
            "2", "World",
            listOf(TextSpan(0, 5, SpanStyle.Italic)),
        )
        val state = EditorState.withBlocks(listOf(block1, block2))

        val newState = MergeBlocks(sourceId = BlockId("2"), targetId = BlockId("1")).reduce(state)

        val content = newState.blocks[0].content as BlockContent.Text
        assertEquals("Hello World", content.text)
        assertEquals(2, content.spans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Bold), content.spans[0])
        assertEquals(TextSpan(6, 11, SpanStyle.Italic), content.spans[1])
    }

    @Test
    fun `merge blocks consolidates adjacent same-style spans`() {
        val block1 = createTestBlockWithSpans(
            "1", "Hello",
            listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )
        val block2 = createTestBlockWithSpans(
            "2", "World",
            listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )
        val state = EditorState.withBlocks(listOf(block1, block2))

        val newState = MergeBlocks(sourceId = BlockId("2"), targetId = BlockId("1")).reduce(state)

        val content = newState.blocks[0].content as BlockContent.Text
        assertEquals("HelloWorld", content.text)
        assertEquals(1, content.spans.size)
        assertEquals(TextSpan(0, 10, SpanStyle.Bold), content.spans[0])
    }

    @Test
    fun `merge blocks with empty spans preserves existing spans`() {
        val block1 = createTestBlockWithSpans(
            "1", "Hello",
            listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )
        val block2 = createTestBlock("2", "World")
        val state = EditorState.withBlocks(listOf(block1, block2))

        val newState = MergeBlocks(sourceId = BlockId("2"), targetId = BlockId("1")).reduce(state)

        val content = newState.blocks[0].content as BlockContent.Text
        assertEquals("HelloWorld", content.text)
        assertEquals(1, content.spans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Bold), content.spans[0])
    }

    // ── UpdateBlockText span policy ─────────────────────────────────────

    @Test
    fun `update block text resets spans`() {
        val block = createTestBlockWithSpans(
            "1", "Hello",
            listOf(TextSpan(0, 5, SpanStyle.Bold)),
        )
        val state = EditorState.withBlocks(listOf(block))

        val newState = UpdateBlockText(BlockId("1"), "New text").reduce(state)

        val content = newState.blocks[0].content as BlockContent.Text
        assertEquals("New text", content.text)
        assertTrue(content.spans.isEmpty())
    }

    // ── Snapshot stability ──────────────────────────────────────────────

    @Test
    fun `apply then remove returns to original spans`() {
        val block = createTestBlock("1", "Hello World")
        val state = EditorState.withBlocks(listOf(block))

        val applied = ApplySpanStyle(BlockId("1"), 0, 5, SpanStyle.Bold).reduce(state)
        val removed = RemoveSpanStyle(BlockId("1"), 0, 5, SpanStyle.Bold).reduce(applied)

        val content = removed.blocks[0].content as BlockContent.Text
        assertTrue(content.spans.isEmpty())
    }

    @Test
    fun `idempotent apply does not duplicate spans`() {
        val block = createTestBlock("1", "Hello World")
        val state = EditorState.withBlocks(listOf(block))

        val first = ApplySpanStyle(BlockId("1"), 0, 5, SpanStyle.Bold).reduce(state)
        val second = ApplySpanStyle(BlockId("1"), 0, 5, SpanStyle.Bold).reduce(first)

        val content = second.blocks[0].content as BlockContent.Text
        assertEquals(1, content.spans.size)
        assertEquals(TextSpan(0, 5, SpanStyle.Bold), content.spans[0])
    }
}
