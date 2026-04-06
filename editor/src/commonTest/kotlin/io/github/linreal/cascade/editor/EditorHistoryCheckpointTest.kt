package io.github.linreal.cascade.editor

import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.slash.SlashCommandId
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.DragState
import io.github.linreal.cascade.editor.state.EditorCheckpoint
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.EditingUiState
import io.github.linreal.cascade.editor.state.SlashCommandState
import io.github.linreal.cascade.editor.state.SlashQueryRange
import io.github.linreal.cascade.editor.state.StructuralEntry
import io.github.linreal.cascade.editor.state.captureCheckpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class EditorHistoryCheckpointTest {

    @Test
    fun `captureCheckpoint resolves mixed runtime snapshot state and reuses unchanged blocks`() {
        val focusedId = BlockId("focused")
        val runtimeSpanId = BlockId("runtime-span")
        val runtimeSameId = BlockId("runtime-same")
        val untouchedId = BlockId("untouched")

        val focusedBlock = textBlock(focusedId, "snapshot")
        val runtimeSpanBlock = textBlock(runtimeSpanId, "stable")
        val runtimeSameBlock = textBlock(runtimeSameId, "same")
        val untouchedBlock = textBlock(untouchedId, "untouched")

        val holder = EditorStateHolder(
            EditorState.withBlocks(
                listOf(focusedBlock, runtimeSpanBlock, runtimeSameBlock, untouchedBlock)
            ).copy(focusedBlockId = focusedId)
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        textStates.getOrCreate(focusedId, "snapshot")
        textStates.setText(focusedId, "runtime")
        textStates.setSelection(focusedId, TextRange(1, 4))
        spanStates.setPendingStyles(focusedId, setOf(SpanStyle.Bold))

        spanStates.getOrCreate(runtimeSpanId, initialSpans = emptyList(), textLength = 6)
        spanStates.set(
            blockId = runtimeSpanId,
            spans = listOf(TextSpan(0, 6, SpanStyle.Italic)),
            textLength = 6,
        )

        textStates.getOrCreate(runtimeSameId, "same")
        spanStates.getOrCreate(runtimeSameId, initialSpans = emptyList(), textLength = 4)

        val checkpoint = holder.captureCheckpoint(textStates, spanStates)

        assertEquals(
            BlockContent.Text("runtime"),
            checkpoint.blocks[0].content,
        )
        assertEquals(
            BlockContent.Text("stable", listOf(TextSpan(0, 6, SpanStyle.Italic))),
            checkpoint.blocks[1].content,
        )
        assertSame(runtimeSameBlock, checkpoint.blocks[2])
        assertSame(untouchedBlock, checkpoint.blocks[3])
        assertEquals(TextRange(1, 4), checkpoint.ui.focusedTextSelection)
        assertEquals(setOf(SpanStyle.Bold), checkpoint.ui.focusedPendingStyles)
    }

    @Test
    fun `structural undo redo replays checkpoint runtime and clears transient state`() {
        val beforeFocusedId = BlockId("before-focused")
        val beforeDividerId = BlockId("before-divider")
        val beforeTrailingId = BlockId("before-trailing")
        val afterFocusedId = BlockId("after-focused")
        val afterExtraId = BlockId("after-extra")
        val staleId = BlockId("stale")

        val beforeCheckpoint = EditorCheckpoint(
            blocks = listOf(
                textBlock(
                    id = beforeFocusedId,
                    text = "before",
                    spans = listOf(TextSpan(0, 6, SpanStyle.Bold)),
                ),
                dividerBlock(beforeDividerId),
                textBlock(beforeTrailingId, ""),
            ),
            ui = EditingUiState(
                focusedBlockId = beforeFocusedId,
                focusedTextSelection = TextRange(1, 4),
                focusedPendingStyles = setOf(SpanStyle.Underline),
            ),
        )
        val afterCheckpoint = EditorCheckpoint(
            blocks = listOf(
                textBlock(
                    id = afterFocusedId,
                    text = "after",
                    spans = listOf(TextSpan(0, 5, SpanStyle.Italic)),
                ),
                textBlock(afterExtraId, "tail"),
            ),
            ui = EditingUiState(
                focusedBlockId = afterFocusedId,
                focusedTextSelection = TextRange(2, 5),
                focusedPendingStyles = setOf(SpanStyle.StrikeThrough),
            ),
        )
        val holder = EditorStateHolder(
            EditorState(
                blocks = afterCheckpoint.blocks,
                focusedBlockId = afterFocusedId,
                selectedBlockIds = setOf(afterExtraId),
                dragState = DragState(
                    draggingBlockIds = setOf(afterFocusedId),
                    targetIndex = 1,
                    initialTouchOffsetY = 12f,
                    primaryBlockOriginalIndex = 0,
                ),
                slashCommandState = SlashCommandState(
                    anchorBlockId = afterFocusedId,
                    query = "af",
                    queryRange = SlashQueryRange(0, 3),
                    navigationPath = listOf(SlashCommandId("sub")),
                    highlightedCommandId = SlashCommandId("highlight"),
                ),
            )
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        holder.bindHistoryRuntime(textStates, spanStates)
        seedRuntimeState(
            blocks = afterCheckpoint.blocks + textBlock(staleId, "stale"),
            textStates = textStates,
            spanStates = spanStates,
        )

        holder.pushHistoryEntry(
            StructuralEntry(
                before = beforeCheckpoint,
                after = afterCheckpoint,
            )
        )

        holder.undo()

        assertEquals(beforeCheckpoint.blocks, holder.state.blocks)
        assertEquals(beforeFocusedId, holder.state.focusedBlockId)
        assertTrueEmptySelection(holder)
        assertNull(holder.state.dragState)
        assertNull(holder.state.slashCommandState)
        assertEquals("before", textStates.getVisibleText(beforeFocusedId))
        assertEquals(TextRange(1, 4), textStates.getSelection(beforeFocusedId))
        assertEquals(
            listOf(TextSpan(0, 6, SpanStyle.Bold)),
            spanStates.getSpans(beforeFocusedId),
        )
        assertEquals(
            setOf(SpanStyle.Underline),
            spanStates.getPendingStyles(beforeFocusedId),
        )
        assertNull(textStates.get(afterFocusedId))
        assertNull(spanStates.get(afterFocusedId))
        assertNull(textStates.get(afterExtraId))
        assertNull(spanStates.get(afterExtraId))
        assertNull(textStates.get(staleId))
        assertNull(spanStates.get(staleId))

        holder.redo()

        assertEquals(afterCheckpoint.blocks, holder.state.blocks)
        assertEquals(afterFocusedId, holder.state.focusedBlockId)
        assertTrueEmptySelection(holder)
        assertNull(holder.state.dragState)
        assertNull(holder.state.slashCommandState)
        assertEquals("after", textStates.getVisibleText(afterFocusedId))
        assertEquals(TextRange(2, 5), textStates.getSelection(afterFocusedId))
        assertEquals(
            listOf(TextSpan(0, 5, SpanStyle.Italic)),
            spanStates.getSpans(afterFocusedId),
        )
        assertEquals(
            setOf(SpanStyle.StrikeThrough),
            spanStates.getPendingStyles(afterFocusedId),
        )
        assertEquals("tail", textStates.getVisibleText(afterExtraId))
        assertEquals(emptyList(), spanStates.getSpans(afterExtraId))
        assertNull(textStates.get(beforeFocusedId))
        assertNull(spanStates.get(beforeFocusedId))
    }

    @Test
    fun `pushHistoryEntry is ignored while replay guard is active`() {
        val blockId = BlockId("b1")
        val holder = EditorStateHolder(
            EditorState.withBlocks(listOf(textBlock(blockId, "current")))
        )

        holder.withHistoryReplay {
            holder.pushHistoryEntry(
                StructuralEntry(
                    before = checkpoint(blockId, "before"),
                    after = checkpoint(blockId, "after"),
                )
            )
        }

        assertFalse(holder.canUndo)
        assertFalse(holder.canRedo)
    }

    @Test
    fun `structural replay rejects non normalized checkpoint`() {
        val blockId = BlockId("b1")
        val dividerId = BlockId("divider")
        val holder = EditorStateHolder(
            EditorState.withBlocks(listOf(textBlock(blockId, "after")))
        )

        holder.pushHistoryEntry(
            StructuralEntry(
                before = EditorCheckpoint(
                    blocks = listOf(
                        textBlock(blockId, "before"),
                        dividerBlock(dividerId),
                    ),
                    ui = EditingUiState(
                        focusedBlockId = blockId,
                        focusedTextSelection = null,
                        focusedPendingStyles = emptySet(),
                    ),
                ),
                after = checkpoint(blockId, "after"),
            )
        )

        val thrown = assertFailsWith<IllegalArgumentException> {
            holder.undo()
        }

        assertEquals(
            "History checkpoints must be normalized and end with a text-supporting block",
            thrown.message,
        )
    }

    private fun checkpoint(
        blockId: BlockId,
        text: String,
    ): EditorCheckpoint {
        return EditorCheckpoint(
            blocks = listOf(textBlock(blockId, text)),
            ui = EditingUiState(
                focusedBlockId = blockId,
                focusedTextSelection = null,
                focusedPendingStyles = emptySet(),
            ),
        )
    }

    private fun seedRuntimeState(
        blocks: List<Block>,
        textStates: BlockTextStates,
        spanStates: BlockSpanStates,
    ) {
        blocks.forEach { block ->
            val content = block.content as? BlockContent.Text ?: return@forEach
            textStates.getOrCreate(block.id, content.text)
            spanStates.getOrCreate(
                blockId = block.id,
                initialSpans = content.spans,
                textLength = content.text.length,
            )
        }
    }

    private fun assertTrueEmptySelection(holder: EditorStateHolder) {
        assertEquals(emptySet(), holder.state.selectedBlockIds)
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

    private fun dividerBlock(id: BlockId): Block {
        return Block(
            id = id,
            type = BlockType.Divider,
            content = BlockContent.Empty,
        )
    }
}
