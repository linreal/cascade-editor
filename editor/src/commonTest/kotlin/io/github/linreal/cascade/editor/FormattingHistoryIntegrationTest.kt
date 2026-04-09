package io.github.linreal.cascade.editor

import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.DefaultFormattingActions
import io.github.linreal.cascade.editor.richtext.SpanActionDispatcher
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.PendingTextHistoryPush
import io.github.linreal.cascade.editor.state.TextEditHistoryTracker
import io.github.linreal.cascade.editor.state.captureCheckpoint
import io.github.linreal.cascade.editor.state.captureFocusedEditingUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormattingHistoryIntegrationTest {

    private class Harness(
        blockId: BlockId,
        text: String,
        spans: List<TextSpan> = emptyList(),
        selection: TextRange,
    ) {
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        val stateHolder = EditorStateHolder(
            EditorState.withBlocks(
                listOf(
                    Block(
                        id = blockId,
                        type = BlockType.Paragraph,
                        content = BlockContent.Text(text, spans),
                    )
                )
            ).copy(focusedBlockId = blockId)
        )

        val spanActionDispatcher = SpanActionDispatcher(
            dispatchFn = { action -> stateHolder.dispatch(action) },
            textStates = textStates,
            spanStates = spanStates,
            stateHolder = stateHolder,
        )

        val formattingActions = DefaultFormattingActions(
            stateHolder = stateHolder,
            textStates = textStates,
            spanActionDispatcher = spanActionDispatcher,
        )

        init {
            stateHolder.bindHistoryRuntime(textStates, spanStates)
            textStates.getOrCreate(blockId, text)
            textStates.setSelection(blockId, selection)
            spanStates.getOrCreate(blockId, spans, text.length)
        }
    }

    @Test
    fun `selected range formatting is undoable and redoes spans correctly`() {
        val blockId = BlockId("b1")
        val harness = Harness(
            blockId = blockId,
            text = "hello",
            selection = TextRange(0, 5),
        )

        harness.formattingActions.toggleStyle(SpanStyle.Bold)

        assertTrue(harness.stateHolder.canUndo)
        assertEquals(
            listOf(TextSpan(0, 5, SpanStyle.Bold)),
            harness.spanStates.getSpans(blockId),
        )
        assertEquals(
            listOf(TextSpan(0, 5, SpanStyle.Bold)),
            (harness.stateHolder.state.getBlock(blockId)?.content as BlockContent.Text).spans,
        )

        harness.stateHolder.undo()

        assertTrue(harness.stateHolder.canRedo)
        assertTrue(harness.spanStates.getSpans(blockId).isEmpty())
        assertEquals(
            emptyList(),
            (harness.stateHolder.state.getBlock(blockId)?.content as BlockContent.Text).spans,
        )
        assertEquals(TextRange(0, 5), harness.textStates.getSelection(blockId))

        harness.stateHolder.redo()

        assertEquals(
            listOf(TextSpan(0, 5, SpanStyle.Bold)),
            harness.spanStates.getSpans(blockId),
        )
        assertEquals(TextRange(0, 5), harness.textStates.getSelection(blockId))
    }

    @Test
    fun `collapsed cursor toggle does not create standalone history`() {
        val blockId = BlockId("b1")
        val harness = Harness(
            blockId = blockId,
            text = "hello",
            selection = TextRange(3),
        )

        harness.formattingActions.toggleStyle(SpanStyle.Bold)

        assertFalse(harness.stateHolder.canUndo)
        assertEquals(setOf(SpanStyle.Bold), harness.spanStates.getPendingStyles(blockId))
        assertTrue(harness.spanStates.getSpans(blockId).isEmpty())
    }

    @Test
    fun `formatting replay restores focused pending styles carried by the entry`() {
        val blockId = BlockId("b1")
        val harness = Harness(
            blockId = blockId,
            text = "hello",
            selection = TextRange(0, 5),
        )
        harness.spanStates.setPendingStyles(blockId, setOf(SpanStyle.Underline))

        harness.formattingActions.applyStyle(SpanStyle.Italic)

        assertTrue(harness.stateHolder.canUndo)
        assertEquals(setOf(SpanStyle.Underline), harness.spanStates.getPendingStyles(blockId))

        harness.spanStates.setPendingStyles(blockId, setOf(SpanStyle.StrikeThrough))
        harness.stateHolder.undo()

        assertEquals(setOf(SpanStyle.Underline), harness.spanStates.getPendingStyles(blockId))
        assertEquals(TextRange(0, 5), harness.textStates.getSelection(blockId))
        assertTrue(harness.spanStates.getSpans(blockId).isEmpty())

        harness.spanStates.clearPendingStyles(blockId)
        assertNull(harness.spanStates.getPendingStyles(blockId))

        harness.stateHolder.redo()

        assertEquals(setOf(SpanStyle.Underline), harness.spanStates.getPendingStyles(blockId))
        assertEquals(
            listOf(TextSpan(0, 5, SpanStyle.Italic)),
            harness.spanStates.getSpans(blockId),
        )
        assertEquals(TextRange(0, 5), harness.textStates.getSelection(blockId))
    }

    @Test
    fun `formatting breaks an active typing batch so typing before and after undo separately`() {
        val blockId = BlockId("b1")
        val harness = Harness(
            blockId = blockId,
            text = "abc",
            selection = TextRange(3),
        )
        val clock = FakeClock()
        val tracker = TextEditHistoryTracker(
            initialCheckpoint = harness.stateHolder.captureCheckpoint(harness.textStates, harness.spanStates),
            nowMs = clock::now,
        )
        harness.stateHolder.registerTextHistoryTracker(blockId, tracker)

        try {
            clock.nowMs = 0L
            harness.setRuntimeText(blockId, "abcd", TextRange(4))
            harness.stateHolder.pushFrom(
                tracker.onUserTextCommit(
                    harness.stateHolder.captureCheckpoint(harness.textStates, harness.spanStates)
                )
            )

            harness.moveSelection(blockId, TextRange(0, 1), tracker)
            harness.formattingActions.toggleStyle(SpanStyle.Bold)

            harness.moveSelection(blockId, TextRange(4), tracker)
            clock.nowMs = 100L
            harness.setRuntimeText(blockId, "abcde", TextRange(5))
            harness.stateHolder.pushFrom(
                tracker.onUserTextCommit(
                    harness.stateHolder.captureCheckpoint(harness.textStates, harness.spanStates)
                )
            )

            harness.stateHolder.undo()
            assertEquals("abcd", harness.textStates.getVisibleText(blockId))
            assertEquals(listOf(TextSpan(0, 1, SpanStyle.Bold)), harness.spanStates.getSpans(blockId))

            harness.stateHolder.undo()
            assertEquals("abcd", harness.textStates.getVisibleText(blockId))
            assertTrue(harness.spanStates.getSpans(blockId).isEmpty())

            harness.stateHolder.undo()
            assertEquals("abc", harness.textStates.getVisibleText(blockId))
            assertTrue(harness.spanStates.getSpans(blockId).isEmpty())
        } finally {
            harness.stateHolder.unregisterTextHistoryTracker(blockId, tracker)
        }
    }

    private fun Harness.setRuntimeText(
        blockId: BlockId,
        text: String,
        selection: TextRange,
    ) {
        textStates.setText(blockId, text, cursorPosition = selection.end)
        textStates.setSelection(blockId, selection)
        textStates.consumeProgrammaticCommit(blockId)
    }

    private fun Harness.moveSelection(
        blockId: BlockId,
        selection: TextRange,
        tracker: TextEditHistoryTracker,
    ) {
        textStates.setSelection(blockId, selection)
        tracker.noteSelectionChanged(
            selection = selection,
            ui = captureFocusedEditingUiState(stateHolder.state, textStates, spanStates),
        )
    }

    private fun EditorStateHolder.pushFrom(pending: PendingTextHistoryPush?) {
        if (pending == null) return
        pushHistoryEntry(pending.entry, pending.policy)
    }

    private class FakeClock {
        var nowMs: Long = 0L

        fun now(): Long = nowMs
    }
}
