package io.github.linreal.cascade.editor

import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditingUiState
import io.github.linreal.cascade.editor.state.captureFocusedEditingUiState
import io.github.linreal.cascade.editor.state.restoreFocusedEditingUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditorHistoryUiStateTest {

    @Test
    fun `captureFocusedEditingUiState captures collapsed selection in visible coordinates`() {
        val blockId = BlockId("b1")
        val state = EditorState.withBlocks(listOf(textBlock(blockId, "Hello")))
            .copy(focusedBlockId = blockId)
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        textStates.getOrCreate(blockId, "Hello")
        textStates.setSelection(blockId, TextRange(3))

        val uiState = captureFocusedEditingUiState(state, textStates, spanStates)

        assertEquals(blockId, uiState.focusedBlockId)
        assertEquals(TextRange(3, 3), uiState.focusedTextSelection)
        assertEquals(emptySet(), uiState.focusedPendingStyles)
    }

    @Test
    fun `captureFocusedEditingUiState captures ranged selection and pending styles`() {
        val blockId = BlockId("b1")
        val state = EditorState.withBlocks(listOf(textBlock(blockId, "Hello")))
            .copy(focusedBlockId = blockId)
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        textStates.getOrCreate(blockId, "Hello")
        textStates.setSelection(blockId, TextRange(1, 4))
        spanStates.setPendingStyles(blockId, setOf(SpanStyle.Bold, SpanStyle.Italic))

        val uiState = captureFocusedEditingUiState(state, textStates, spanStates)

        assertEquals(TextRange(1, 4), uiState.focusedTextSelection)
        assertEquals(setOf(SpanStyle.Bold, SpanStyle.Italic), uiState.focusedPendingStyles)
    }

    @Test
    fun `restoreFocusedEditingUiState restores selection and pending styles`() {
        val blockId = BlockId("b1")
        val state = EditorState.withBlocks(listOf(textBlock(blockId, "Hello")))
            .copy(focusedBlockId = blockId)
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        textStates.getOrCreate(blockId, "Hello")

        restoreFocusedEditingUiState(
            uiState = EditingUiState(
                focusedBlockId = blockId,
                focusedTextSelection = TextRange(2, 5),
                focusedPendingStyles = setOf(SpanStyle.Underline),
            ),
            state = state,
            textStates = textStates,
            spanStates = spanStates,
        )

        assertEquals(TextRange(2, 5), textStates.getSelection(blockId))
        assertEquals(setOf(SpanStyle.Underline), spanStates.getPendingStyles(blockId))
    }

    @Test
    fun `restoreFocusedEditingUiState clears focused pending styles when snapshot is empty`() {
        val blockId = BlockId("b1")
        val state = EditorState.withBlocks(listOf(textBlock(blockId, "Hello")))
            .copy(focusedBlockId = blockId)
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        textStates.getOrCreate(blockId, "Hello")
        spanStates.setPendingStyles(blockId, setOf(SpanStyle.Bold))

        restoreFocusedEditingUiState(
            uiState = EditingUiState(
                focusedBlockId = blockId,
                focusedTextSelection = TextRange(0),
                focusedPendingStyles = emptySet(),
            ),
            state = state,
            textStates = textStates,
            spanStates = spanStates,
        )

        assertEquals(TextRange(0, 0), textStates.getSelection(blockId))
        assertNull(spanStates.getPendingStyles(blockId))
    }

    @Test
    fun `captureFocusedEditingUiState with no focus returns empty ui state`() {
        val blockId = BlockId("b1")
        val state = EditorState.withBlocks(listOf(textBlock(blockId, "Hello")))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        textStates.getOrCreate(blockId, "Hello")
        textStates.setSelection(blockId, TextRange(2, 4))
        spanStates.setPendingStyles(blockId, setOf(SpanStyle.Bold))

        val uiState = captureFocusedEditingUiState(state, textStates, spanStates)

        assertNull(uiState.focusedBlockId)
        assertNull(uiState.focusedTextSelection)
        assertEquals(emptySet(), uiState.focusedPendingStyles)
    }

    @Test
    fun `restoreFocusedEditingUiState is safe no-op when focus is absent`() {
        val blockId = BlockId("b1")
        val state = EditorState.withBlocks(listOf(textBlock(blockId, "Hello")))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        textStates.getOrCreate(blockId, "Hello")
        textStates.setSelection(blockId, TextRange(1, 3))
        spanStates.setPendingStyles(blockId, setOf(SpanStyle.Italic))

        restoreFocusedEditingUiState(
            uiState = EditingUiState(
                focusedBlockId = null,
                focusedTextSelection = TextRange(0, 5),
                focusedPendingStyles = setOf(SpanStyle.Bold),
            ),
            state = state,
            textStates = textStates,
            spanStates = spanStates,
        )

        assertEquals(TextRange(1, 3), textStates.getSelection(blockId))
        assertEquals(setOf(SpanStyle.Italic), spanStates.getPendingStyles(blockId))
    }

    @Test
    fun `restoreFocusedEditingUiState is safe no-op when focused block is missing`() {
        val existingBlockId = BlockId("existing")
        val missingBlockId = BlockId("missing")
        val state = EditorState.withBlocks(listOf(textBlock(existingBlockId, "Hello")))
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        textStates.getOrCreate(existingBlockId, "Hello")
        textStates.setSelection(existingBlockId, TextRange(1, 3))
        spanStates.setPendingStyles(existingBlockId, setOf(SpanStyle.Italic))

        restoreFocusedEditingUiState(
            uiState = EditingUiState(
                focusedBlockId = missingBlockId,
                focusedTextSelection = TextRange(0, 5),
                focusedPendingStyles = setOf(SpanStyle.Bold),
            ),
            state = state,
            textStates = textStates,
            spanStates = spanStates,
        )

        assertEquals(TextRange(1, 3), textStates.getSelection(existingBlockId))
        assertEquals(setOf(SpanStyle.Italic), spanStates.getPendingStyles(existingBlockId))
    }

    private fun textBlock(
        id: BlockId,
        text: String,
    ): Block {
        return Block(
            id = id,
            type = BlockType.Paragraph,
            content = BlockContent.Text(text),
        )
    }
}
