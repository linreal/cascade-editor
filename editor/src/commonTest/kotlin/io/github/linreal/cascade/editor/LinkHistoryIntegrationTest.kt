package io.github.linreal.cascade.editor

import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.LinkActionDispatcher
import io.github.linreal.cascade.editor.richtext.LinkTarget
import io.github.linreal.cascade.editor.richtext.LinkValidationResult
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.PendingTextHistoryPush
import io.github.linreal.cascade.editor.state.TextEditHistoryTracker
import io.github.linreal.cascade.editor.state.captureCheckpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkHistoryIntegrationTest {

    @Test
    fun `applying link is one undoable and redoable history step`() {
        val blockId = BlockId("b1")
        val harness = Harness(
            blockId = blockId,
            text = "foo",
            spans = emptyList(),
            selection = TextRange(0, 3),
        )

        harness.linkActions.applyLink(
            target = LinkTarget(blockId, 0, 3),
            url = "example.com",
        )

        assertTrue(harness.stateHolder.canUndo)
        assertEquals(
            listOf(TextSpan(0, 3, SpanStyle.Link("https://example.com"))),
            harness.spanStates.getSpans(blockId),
        )

        harness.stateHolder.undo()

        assertFalse(harness.stateHolder.canUndo)
        assertTrue(harness.stateHolder.canRedo)
        assertTrue(harness.spanStates.getSpans(blockId).isEmpty())
        assertEquals("foo", harness.textStates.getVisibleText(blockId))

        harness.stateHolder.redo()

        assertEquals(
            listOf(TextSpan(0, 3, SpanStyle.Link("https://example.com"))),
            harness.spanStates.getSpans(blockId),
        )
    }

    @Test
    fun `editing link title and URL replays as one history step`() {
        val blockId = BlockId("b1")
        val harness = Harness(
            blockId = blockId,
            text = "old",
            spans = listOf(TextSpan(0, 3, SpanStyle.Link("https://old.example"))),
            selection = TextRange(0, 3),
        )

        harness.linkActions.applyLink(
            target = LinkTarget(blockId, 0, 3),
            url = "new.example",
            title = "new",
        )

        assertEquals("new", harness.textStates.getVisibleText(blockId))
        assertEquals(
            listOf(TextSpan(0, 3, SpanStyle.Link("https://new.example"))),
            harness.spanStates.getSpans(blockId),
        )

        harness.stateHolder.undo()

        assertFalse(harness.stateHolder.canUndo)
        assertEquals("old", harness.textStates.getVisibleText(blockId))
        assertEquals(
            listOf(TextSpan(0, 3, SpanStyle.Link("https://old.example"))),
            harness.spanStates.getSpans(blockId),
        )

        harness.stateHolder.redo()

        assertEquals("new", harness.textStates.getVisibleText(blockId))
        assertEquals(
            listOf(TextSpan(0, 3, SpanStyle.Link("https://new.example"))),
            harness.spanStates.getSpans(blockId),
        )
    }

    @Test
    fun `removing link is one undoable and redoable history step`() {
        val blockId = BlockId("b1")
        val bold = TextSpan(0, 3, SpanStyle.Bold)
        val link = TextSpan(0, 3, SpanStyle.Link("https://example.com"))
        val harness = Harness(
            blockId = blockId,
            text = "foo",
            spans = listOf(bold, link),
            selection = TextRange(0, 3),
        )

        harness.linkActions.removeLink(LinkTarget(blockId, 0, 3))

        assertEquals(listOf(bold), harness.spanStates.getSpans(blockId))

        harness.stateHolder.undo()

        assertFalse(harness.stateHolder.canUndo)
        assertEquals(listOf(bold, link), harness.spanStates.getSpans(blockId))

        harness.stateHolder.redo()

        assertEquals(listOf(bold), harness.spanStates.getSpans(blockId))
    }

    @Test
    fun `stale no-op apply does not break active typing batch`() {
        val blockId = BlockId("b1")
        val harness = Harness(
            blockId = blockId,
            text = "abc",
            spans = emptyList(),
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
                    harness.stateHolder.captureCheckpoint(harness.textStates, harness.spanStates),
                )
            )

            val result = harness.linkActions.applyLink(
                target = LinkTarget(blockId, 10, 12),
                url = "example.com",
            )

            assertEquals(LinkValidationResult.Valid("https://example.com"), result)
            assertEquals("abcd", harness.textStates.getVisibleText(blockId))
            assertTrue(harness.spanStates.getSpans(blockId).isEmpty())

            clock.nowMs = 100L
            harness.setRuntimeText(blockId, "abcde", TextRange(5))
            harness.stateHolder.pushFrom(
                tracker.onUserTextCommit(
                    harness.stateHolder.captureCheckpoint(harness.textStates, harness.spanStates),
                )
            )

            harness.stateHolder.undo()

            assertEquals("abc", harness.textStates.getVisibleText(blockId))
            assertTrue(harness.spanStates.getSpans(blockId).isEmpty())
        } finally {
            harness.stateHolder.unregisterTextHistoryTracker(blockId, tracker)
        }
    }

    private class Harness(
        blockId: BlockId,
        text: String,
        spans: List<TextSpan>,
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
        val linkActions = LinkActionDispatcher(
            dispatchFn = { action ->
                stateHolder.dispatch(action)
            },
            textStates = textStates,
            spanStates = spanStates,
            stateHolder = stateHolder,
        )

        init {
            stateHolder.bindHistoryRuntime(textStates, spanStates)
            textStates.getOrCreate(blockId, text)
            textStates.setSelection(blockId, selection)
            spanStates.getOrCreate(blockId, spans, text.length)
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

    private fun EditorStateHolder.pushFrom(pending: PendingTextHistoryPush?) {
        if (pending == null) return
        pushHistoryEntry(pending.entry, pending.policy)
    }

    private class FakeClock {
        var nowMs: Long = 0L

        fun now(): Long = nowMs
    }
}
