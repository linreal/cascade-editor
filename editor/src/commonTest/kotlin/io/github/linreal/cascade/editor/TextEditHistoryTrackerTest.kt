package io.github.linreal.cascade.editor

import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.state.BlockTextEntry
import io.github.linreal.cascade.editor.state.EditorCheckpoint
import io.github.linreal.cascade.editor.state.EditingUiState
import io.github.linreal.cascade.editor.state.HistoryEntry
import io.github.linreal.cascade.editor.state.HistoryManager
import io.github.linreal.cascade.editor.state.PendingTextHistoryPush
import io.github.linreal.cascade.editor.state.TextEditHistoryTracker
import kotlin.test.Test
import kotlin.test.assertEquals

class TextEditHistoryTrackerTest {

    private val blockId = BlockId("block")

    @Test
    fun `continuous forward typing coalesces into one entry`() {
        val clock = FakeClock()
        val tracker = TextEditHistoryTracker(
            initialCheckpoint = checkpoint("", TextRange(0)),
            nowMs = clock::now,
        )
        val manager = HistoryManager()

        clock.nowMs = 0L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("a", TextRange(1))))

        clock.nowMs = 100L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("ab", TextRange(2))))

        clock.nowMs = 200L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("abc", TextRange(3))))

        assertEquals(1, manager.undoDepth)

        var replayed: HistoryEntry? = null
        manager.undo { replayed = it }
        val merged = replayed as BlockTextEntry
        assertEquals("", merged.before.text)
        assertEquals("abc", merged.after.text)
    }

    @Test
    fun `pause beyond merge window starts a new batch`() {
        val clock = FakeClock()
        val tracker = TextEditHistoryTracker(
            initialCheckpoint = checkpoint("", TextRange(0)),
            nowMs = clock::now,
        )
        val manager = HistoryManager()

        clock.nowMs = 0L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("a", TextRange(1))))

        clock.nowMs = 600L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("ab", TextRange(2))))

        assertEquals(2, manager.undoDepth)
    }

    @Test
    fun `caret jump breaks typing coalescing`() {
        val clock = FakeClock()
        val tracker = TextEditHistoryTracker(
            initialCheckpoint = checkpoint("", TextRange(0)),
            nowMs = clock::now,
        )
        val manager = HistoryManager()

        clock.nowMs = 0L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("a", TextRange(1))))

        tracker.noteSelectionChanged(
            selection = TextRange(0),
            ui = editingUiState(TextRange(0)),
        )

        clock.nowMs = 100L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("ba", TextRange(1))))

        assertEquals(2, manager.undoDepth)
    }

    @Test
    fun `insert to delete direction change breaks typing coalescing`() {
        val clock = FakeClock()
        val tracker = TextEditHistoryTracker(
            initialCheckpoint = checkpoint("", TextRange(0)),
            nowMs = clock::now,
        )
        val manager = HistoryManager()

        clock.nowMs = 0L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("a", TextRange(1))))

        clock.nowMs = 100L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("", TextRange(0))))

        assertEquals(2, manager.undoDepth)
    }

    @Test
    fun `focus change breaks typing coalescing`() {
        val clock = FakeClock()
        val tracker = TextEditHistoryTracker(
            initialCheckpoint = checkpoint("", TextRange(0)),
            nowMs = clock::now,
        )
        val manager = HistoryManager()

        clock.nowMs = 0L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("a", TextRange(1))))

        tracker.noteFocusChanged(
            checkpoint("a", TextRange(1)).copy(
                ui = EditingUiState(
                    focusedBlockId = null,
                    focusedTextSelection = null,
                    focusedPendingStyles = emptySet(),
                )
            )
        )
        tracker.noteFocusChanged(checkpoint("a", TextRange(1)))

        clock.nowMs = 100L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("ab", TextRange(2))))

        assertEquals(2, manager.undoDepth)
    }

    @Test
    fun `selection expansion breaks typing coalescing`() {
        val clock = FakeClock()
        val tracker = TextEditHistoryTracker(
            initialCheckpoint = checkpoint("xy", TextRange(2)),
            nowMs = clock::now,
        )
        val manager = HistoryManager()

        clock.nowMs = 0L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("x", TextRange(1))))

        tracker.noteSelectionChanged(
            selection = TextRange(0, 1),
            ui = editingUiState(TextRange(0, 1)),
        )
        tracker.noteSelectionChanged(
            selection = TextRange(0),
            ui = editingUiState(TextRange(0)),
        )

        clock.nowMs = 100L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("", TextRange(0))))

        assertEquals(2, manager.undoDepth)
    }

    @Test
    fun `explicit paste starts a fresh batch and blocks following merge`() {
        val clock = FakeClock()
        val tracker = TextEditHistoryTracker(
            initialCheckpoint = checkpoint("", TextRange(0)),
            nowMs = clock::now,
        )
        val manager = HistoryManager()

        clock.nowMs = 0L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("a", TextRange(1))))

        tracker.noteExplicitPaste()

        clock.nowMs = 100L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("abc", TextRange(3))))

        clock.nowMs = 150L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("abcd", TextRange(4))))

        assertEquals(3, manager.undoDepth)
    }

    @Test
    fun `multi character insertion fallback starts a fresh batch and blocks following merge`() {
        val clock = FakeClock()
        val tracker = TextEditHistoryTracker(
            initialCheckpoint = checkpoint("", TextRange(0)),
            nowMs = clock::now,
        )
        val manager = HistoryManager()

        clock.nowMs = 0L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("a", TextRange(1))))

        clock.nowMs = 100L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("abc", TextRange(3))))

        clock.nowMs = 150L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("abcd", TextRange(4))))

        assertEquals(3, manager.undoDepth)
    }

    @Test
    fun `delete forward coalesces while caret stays in place`() {
        val clock = FakeClock()
        val tracker = TextEditHistoryTracker(
            initialCheckpoint = checkpoint("abc", TextRange(0)),
            nowMs = clock::now,
        )
        val manager = HistoryManager()

        clock.nowMs = 0L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("bc", TextRange(0))))

        clock.nowMs = 100L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("c", TextRange(0))))

        assertEquals(1, manager.undoDepth)

        var replayed: HistoryEntry? = null
        manager.undo { replayed = it }
        val merged = replayed as BlockTextEntry
        assertEquals("abc", merged.before.text)
        assertEquals("c", merged.after.text)
    }

    @Test
    fun `block replay sync does not push history and resets coalescing`() {
        val clock = FakeClock()
        val tracker = TextEditHistoryTracker(
            initialCheckpoint = checkpoint("", TextRange(0)),
            nowMs = clock::now,
        )
        val manager = HistoryManager()

        clock.nowMs = 0L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("a", TextRange(1))))

        tracker.syncToBlockContent(
            blockId = blockId,
            content = BlockContent.Text(""),
            ui = editingUiState(TextRange(0)),
        )

        assertEquals(1, manager.undoDepth)

        clock.nowMs = 100L
        manager.pushFrom(tracker.onUserTextCommit(checkpoint("x", TextRange(1))))

        assertEquals(2, manager.undoDepth)
    }

    private fun HistoryManager.pushFrom(pending: PendingTextHistoryPush?) {
        if (pending == null) return
        push(pending.entry, pending.policy)
    }

    private fun checkpoint(
        text: String,
        selection: TextRange,
    ): EditorCheckpoint {
        return EditorCheckpoint(
            blocks = listOf(
                Block(
                    id = blockId,
                    type = BlockType.Paragraph,
                    content = BlockContent.Text(text),
                )
            ),
            ui = editingUiState(selection),
        )
    }

    private fun editingUiState(selection: TextRange): EditingUiState {
        return EditingUiState(
            focusedBlockId = blockId,
            focusedTextSelection = selection,
            focusedPendingStyles = emptySet(),
        )
    }

    private class FakeClock {
        var nowMs: Long = 0L

        fun now(): Long = nowMs
    }
}
