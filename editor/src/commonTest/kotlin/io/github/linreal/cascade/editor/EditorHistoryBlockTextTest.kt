package io.github.linreal.cascade.editor

import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextEntry
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorCheckpoint
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.EditingUiState
import io.github.linreal.cascade.editor.state.StructuralEntry
import io.github.linreal.cascade.editor.state.buildHistoryEntryFromCheckpoints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class EditorHistoryBlockTextTest {

    @Test
    fun `predicate accepts typing edit in one block`() {
        val blockId = BlockId("b1")

        val entry = buildHistoryEntryFromCheckpoints(
            before = checkpoint(
                blocks = listOf(textBlock(blockId, "a")),
                focusedBlockId = blockId,
            ),
            after = checkpoint(
                blocks = listOf(textBlock(blockId, "ab")),
                focusedBlockId = blockId,
            ),
        )

        assertIs<BlockTextEntry>(entry)
        assertEquals(blockId, entry.blockId)
        assertEquals(BlockContent.Text("a"), entry.before)
        assertEquals(BlockContent.Text("ab"), entry.after)
    }

    @Test
    fun `predicate accepts delete edit in one block`() {
        val blockId = BlockId("b1")

        val entry = buildHistoryEntryFromCheckpoints(
            before = checkpoint(blocks = listOf(textBlock(blockId, "abc"))),
            after = checkpoint(blocks = listOf(textBlock(blockId, "ac"))),
        )

        assertIs<BlockTextEntry>(entry)
        assertEquals(BlockContent.Text("abc"), entry.before)
        assertEquals(BlockContent.Text("ac"), entry.after)
    }

    @Test
    fun `predicate accepts paste into one block`() {
        val blockId = BlockId("b1")

        val entry = buildHistoryEntryFromCheckpoints(
            before = checkpoint(blocks = listOf(textBlock(blockId, "a"))),
            after = checkpoint(blocks = listOf(textBlock(blockId, "alpha beta"))),
        )

        assertIs<BlockTextEntry>(entry)
        assertEquals(BlockContent.Text("alpha beta"), entry.after)
    }

    @Test
    fun `predicate accepts selected text formatting in one block`() {
        val blockId = BlockId("b1")

        val entry = buildHistoryEntryFromCheckpoints(
            before = checkpoint(
                blocks = listOf(textBlock(blockId, "hello")),
                focusedBlockId = blockId,
                focusedSelection = TextRange(0, 5),
            ),
            after = checkpoint(
                blocks = listOf(
                    textBlock(
                        id = blockId,
                        text = "hello",
                        spans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
                    )
                ),
                focusedBlockId = blockId,
                focusedSelection = TextRange(0, 5),
            ),
        )

        assertIs<BlockTextEntry>(entry)
        assertEquals(
            BlockContent.Text("hello", listOf(TextSpan(0, 5, SpanStyle.Bold))),
            entry.after,
        )
    }

    @Test
    fun `predicate rejects split into two blocks`() {
        val originalId = BlockId("b1")
        val newId = BlockId("b2")

        val entry = buildHistoryEntryFromCheckpoints(
            before = checkpoint(blocks = listOf(textBlock(originalId, "hello"))),
            after = checkpoint(
                blocks = listOf(
                    textBlock(originalId, "he"),
                    textBlock(newId, "llo"),
                )
            ),
        )

        assertIs<StructuralEntry>(entry)
    }

    @Test
    fun `predicate rejects merge into one block`() {
        val firstId = BlockId("b1")
        val secondId = BlockId("b2")

        val entry = buildHistoryEntryFromCheckpoints(
            before = checkpoint(
                blocks = listOf(
                    textBlock(firstId, "he"),
                    textBlock(secondId, "llo"),
                )
            ),
            after = checkpoint(blocks = listOf(textBlock(firstId, "hello"))),
        )

        assertIs<StructuralEntry>(entry)
    }

    @Test
    fun `predicate rejects reorder`() {
        val firstId = BlockId("b1")
        val secondId = BlockId("b2")

        val entry = buildHistoryEntryFromCheckpoints(
            before = checkpoint(
                blocks = listOf(
                    textBlock(firstId, "one"),
                    textBlock(secondId, "two"),
                )
            ),
            after = checkpoint(
                blocks = listOf(
                    textBlock(secondId, "two"),
                    textBlock(firstId, "one"),
                )
            ),
        )

        assertIs<StructuralEntry>(entry)
    }

    @Test
    fun `predicate rejects block type change`() {
        val blockId = BlockId("b1")

        val entry = buildHistoryEntryFromCheckpoints(
            before = checkpoint(blocks = listOf(textBlock(blockId, "hello"))),
            after = checkpoint(blocks = listOf(headingBlock(blockId, "hello"))),
        )

        assertIs<StructuralEntry>(entry)
    }

    @Test
    fun `predicate rejects block insert and delete`() {
        val blockId = BlockId("b1")
        val insertedId = BlockId("b2")

        val insertEntry = buildHistoryEntryFromCheckpoints(
            before = checkpoint(blocks = listOf(textBlock(blockId, "hello"))),
            after = checkpoint(
                blocks = listOf(
                    textBlock(blockId, "hello"),
                    textBlock(insertedId, "new"),
                )
            ),
        )
        val deleteEntry = buildHistoryEntryFromCheckpoints(
            before = checkpoint(
                blocks = listOf(
                    textBlock(blockId, "hello"),
                    textBlock(insertedId, "new"),
                )
            ),
            after = checkpoint(blocks = listOf(textBlock(blockId, "hello"))),
        )

        assertIs<StructuralEntry>(insertEntry)
        assertIs<StructuralEntry>(deleteEntry)
    }

    @Test
    fun `predicate rejects multi block content change`() {
        val firstId = BlockId("b1")
        val secondId = BlockId("b2")

        val entry = buildHistoryEntryFromCheckpoints(
            before = checkpoint(
                blocks = listOf(
                    textBlock(firstId, "one"),
                    textBlock(secondId, "two"),
                )
            ),
            after = checkpoint(
                blocks = listOf(
                    textBlock(firstId, "ONE"),
                    textBlock(secondId, "TWO"),
                )
            ),
        )

        assertIs<StructuralEntry>(entry)
    }

    @Test
    fun `local replay patches only target block and restores focused ui state`() {
        val targetId = BlockId("target")
        val untouchedId = BlockId("untouched")
        val targetBeforeContent = BlockContent.Text(
            text = "before",
            spans = listOf(TextSpan(0, 6, SpanStyle.Bold)),
        )
        val targetAfterContent = BlockContent.Text(
            text = "after",
            spans = listOf(TextSpan(0, 5, SpanStyle.Italic)),
        )
        val untouchedBlock = textBlock(
            id = untouchedId,
            text = "same",
            spans = listOf(TextSpan(0, 4, SpanStyle.Underline)),
        )
        val holder = EditorStateHolder(
            EditorState.withBlocks(
                listOf(
                    Block(
                        id = targetId,
                        type = BlockType.Paragraph,
                        content = targetAfterContent,
                    ),
                    untouchedBlock,
                )
            ).copy(focusedBlockId = targetId)
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        holder.bindHistoryRuntime(textStates, spanStates)

        textStates.getOrCreate(targetId, targetAfterContent.text)
        textStates.setSelection(targetId, TextRange(5))
        spanStates.getOrCreate(
            blockId = targetId,
            initialSpans = targetAfterContent.spans,
            textLength = targetAfterContent.text.length,
        )

        val untouchedTextState = textStates.getOrCreate(untouchedId, "same")
        textStates.setSelection(untouchedId, TextRange(1, 3))
        val untouchedSpanState = spanStates.getOrCreate(
            blockId = untouchedId,
            initialSpans = (untouchedBlock.content as BlockContent.Text).spans,
            textLength = 4,
        )
        spanStates.setPendingStyles(untouchedId, setOf(SpanStyle.Underline))

        holder.pushHistoryEntry(
            BlockTextEntry(
                blockId = targetId,
                before = targetBeforeContent,
                after = targetAfterContent,
                uiBefore = EditingUiState(
                    focusedBlockId = targetId,
                    focusedTextSelection = TextRange(1, 4),
                    focusedPendingStyles = setOf(SpanStyle.StrikeThrough),
                ),
                uiAfter = EditingUiState(
                    focusedBlockId = targetId,
                    focusedTextSelection = TextRange(2, 5),
                    focusedPendingStyles = emptySet(),
                ),
            )
        )

        holder.undo()

        assertEquals(targetBeforeContent, holder.state.getBlock(targetId)?.content)
        assertSame(untouchedBlock, holder.state.blocks[1])
        assertEquals("before", textStates.getVisibleText(targetId))
        assertEquals(TextRange(1, 4), textStates.getSelection(targetId))
        assertEquals(targetBeforeContent.spans, spanStates.getSpans(targetId))
        assertEquals(
            setOf(SpanStyle.StrikeThrough),
            spanStates.getPendingStyles(targetId),
        )

        assertSame(untouchedTextState, textStates.get(untouchedId))
        assertSame(untouchedSpanState, spanStates.get(untouchedId))
        assertEquals("same", textStates.getVisibleText(untouchedId))
        assertEquals(TextRange(1, 3), textStates.getSelection(untouchedId))
        assertEquals(
            listOf(TextSpan(0, 4, SpanStyle.Underline)),
            spanStates.getSpans(untouchedId),
        )
        assertEquals(
            setOf(SpanStyle.Underline),
            spanStates.getPendingStyles(untouchedId),
        )

        holder.redo()

        assertEquals(targetAfterContent, holder.state.getBlock(targetId)?.content)
        assertSame(untouchedBlock, holder.state.blocks[1])
        assertEquals("after", textStates.getVisibleText(targetId))
        assertEquals(TextRange(2, 5), textStates.getSelection(targetId))
        assertEquals(targetAfterContent.spans, spanStates.getSpans(targetId))
        assertNull(spanStates.getPendingStyles(targetId))

        assertSame(untouchedTextState, textStates.get(untouchedId))
        assertSame(untouchedSpanState, spanStates.get(untouchedId))
        assertEquals("same", textStates.getVisibleText(untouchedId))
        assertEquals(TextRange(1, 3), textStates.getSelection(untouchedId))
    }

    private fun checkpoint(
        blocks: List<Block>,
        focusedBlockId: BlockId? = null,
        focusedSelection: TextRange? = null,
        pendingStyles: Set<SpanStyle> = emptySet(),
    ): EditorCheckpoint {
        return EditorCheckpoint(
            blocks = blocks,
            ui = EditingUiState(
                focusedBlockId = focusedBlockId,
                focusedTextSelection = focusedSelection,
                focusedPendingStyles = pendingStyles,
            ),
        )
    }

    private fun textBlock(
        id: BlockId,
        text: String,
        spans: List<TextSpan> = emptyList(),
    ): Block {
        return Block(
            id = id,
            type = BlockType.Paragraph,
            content = BlockContent.Text(text, spans),
        )
    }

    private fun headingBlock(
        id: BlockId,
        text: String,
    ): Block {
        return Block(
            id = id,
            type = BlockType.Heading(1),
            content = BlockContent.Text(text),
        )
    }
}
