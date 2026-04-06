package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.richtext.FormattingActions
import io.github.linreal.cascade.editor.state.SlashQueryRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextBlockKeyHandlerTest {

    @Test
    fun `cmd ctrl z invokes undo once per physical press`() {
        val harness = ShortcutHarness()

        assertTrue(
            harness.handler.onPreviewShortcutEvent(
                shortcutKeyEvent(
                    key = Key.Z,
                    type = KeyEventType.KeyDown,
                    hasShortcutModifier = true,
                )
            )
        )
        assertTrue(
            harness.handler.onPreviewShortcutEvent(
                shortcutKeyEvent(
                    key = Key.Z,
                    type = KeyEventType.KeyDown,
                    hasShortcutModifier = true,
                )
            )
        )
        assertTrue(
            harness.handler.onPreviewShortcutEvent(
                shortcutKeyEvent(
                    key = Key.Z,
                    type = KeyEventType.KeyUp,
                )
            )
        )
        assertTrue(
            harness.handler.onPreviewShortcutEvent(
                shortcutKeyEvent(
                    key = Key.Z,
                    type = KeyEventType.KeyDown,
                    hasShortcutModifier = true,
                )
            )
        )

        assertEquals(2, harness.undoCount)
        assertEquals(0, harness.redoCount)
    }

    @Test
    fun `shift cmd ctrl z invokes redo including iOS raw z key`() {
        val harness = ShortcutHarness()
        val iOSRawZKey = Key(0x1DL)

        assertTrue(
            harness.handler.onPreviewShortcutEvent(
                shortcutKeyEvent(
                    key = iOSRawZKey,
                    type = KeyEventType.KeyDown,
                    hasShortcutModifier = true,
                    isShiftPressed = true,
                )
            )
        )

        assertEquals(0, harness.undoCount)
        assertEquals(1, harness.redoCount)
    }

    @Test
    fun `formatting shortcuts still toggle styles once per physical press`() {
        val harness = ShortcutHarness(
            formattingActions = RecordingFormattingActions()
        )

        assertTrue(
            harness.handler.onPreviewShortcutEvent(
                shortcutKeyEvent(
                    key = Key.B,
                    type = KeyEventType.KeyDown,
                    hasShortcutModifier = true,
                )
            )
        )
        assertTrue(
            harness.handler.onPreviewShortcutEvent(
                shortcutKeyEvent(
                    key = Key.B,
                    type = KeyEventType.KeyDown,
                    hasShortcutModifier = true,
                )
            )
        )
        assertTrue(
            harness.handler.onPreviewShortcutEvent(
                shortcutKeyEvent(
                    key = Key.B,
                    type = KeyEventType.KeyUp,
                )
            )
        )

        assertEquals(listOf<SpanStyle>(SpanStyle.Bold), harness.formattingActions.toggledStyles)
        assertEquals(1, harness.batchBreakerCount)
        assertEquals(0, harness.undoCount)
        assertEquals(0, harness.redoCount)
    }

    private fun shortcutKeyEvent(
        key: Key,
        type: KeyEventType,
        hasShortcutModifier: Boolean = false,
        isShiftPressed: Boolean = false,
    ): ShortcutKeyEvent {
        return ShortcutKeyEvent(
            key = key,
            type = type,
            hasShortcutModifier = hasShortcutModifier,
            isShiftPressed = isShiftPressed,
        )
    }

    private class ShortcutHarness(
        val formattingActions: RecordingFormattingActions = RecordingFormattingActions(),
    ) {
        var undoCount: Int = 0
        var redoCount: Int = 0
        var batchBreakerCount: Int = 0

        val handler = TextBlockKeyHandler(
            formattingActions = formattingActions,
            callbacks = NoOpBlockCallbacks,
            isSlashAnchor = { false },
            slashHighlightedCommandId = { null },
            slashPopupItems = { emptyList() },
            slashCommandExecutor = { null },
            onBatchBreaker = { batchBreakerCount++ },
            onPasteShortcutDetected = {},
            onUndo = { undoCount++ },
            onRedo = { redoCount++ },
        )
    }

    private class RecordingFormattingActions : FormattingActions {
        val toggledStyles = mutableListOf<SpanStyle>()

        override fun toggleStyle(style: SpanStyle) {
            toggledStyles += style
        }

        override fun applyStyle(style: SpanStyle) = Unit

        override fun removeStyle(style: SpanStyle) = Unit
    }

    private data object NoOpBlockCallbacks : BlockCallbacks {
        override fun dispatch(action: EditorAction) = Unit

        override fun onFocus(blockId: BlockId) = Unit

        override fun onEnter(blockId: BlockId, cursorPosition: Int) = Unit

        override fun onBackspaceAtStart(blockId: BlockId) = Unit

        override fun onDeleteAtEnd(blockId: BlockId) = Unit

        override fun onClick(blockId: BlockId) = Unit

        override fun onLongClick(blockId: BlockId) = Unit

        override fun onDragStart(blockId: BlockId, touchOffsetY: Float) = Unit

        override fun onSlashCommand(
            blockId: BlockId,
            queryRange: SlashQueryRange,
            initialQuery: String,
        ) = Unit
    }
}
