package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.HighlightSlashCommand
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.richtext.FormattingActions
import io.github.linreal.cascade.editor.slash.SlashCommandAction
import io.github.linreal.cascade.editor.slash.SlashCommandExecutor
import io.github.linreal.cascade.editor.slash.SlashCommandId
import io.github.linreal.cascade.editor.slash.SlashCommandItem
import io.github.linreal.cascade.editor.slash.SlashCommandResult
import io.github.linreal.cascade.editor.state.SlashQueryRange
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `read-only formatting shortcuts are consumed without formatting or batch side effects`() {
        val harness = ShortcutHarness(policy = EditorInteractionPolicy.ReadOnly)

        assertTrue(
            harness.handler.onPreviewShortcutEvent(
                shortcutKeyEvent(
                    key = Key.B,
                    type = KeyEventType.KeyDown,
                    hasShortcutModifier = true,
                )
            )
        )

        assertEquals(emptyList<SpanStyle>(), harness.formattingActions.toggledStyles)
        assertEquals(0, harness.batchBreakerCount)
    }

    @Test
    fun `read-only history shortcuts are consumed without undo or redo`() {
        val harness = ShortcutHarness(policy = EditorInteractionPolicy.ReadOnly)

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
                    isShiftPressed = true,
                )
            )
        )

        assertEquals(0, harness.undoCount)
        assertEquals(0, harness.redoCount)
    }

    @Test
    fun `read-only paste shortcut detection does not mark paste batching`() {
        val harness = ShortcutHarness(policy = EditorInteractionPolicy.ReadOnly)

        assertFalse(
            harness.handler.onPreviewShortcutEvent(
                shortcutKeyEvent(
                    key = Key.V,
                    type = KeyEventType.KeyDown,
                    hasShortcutModifier = true,
                )
            )
        )

        assertEquals(0, harness.pasteShortcutCount)
    }

    @Test
    fun `read-only slash popup enter is ignored without executor lookup or batch side effects`() {
        val commandId = SlashCommandId("custom.read_only_probe")
        val command = SlashCommandAction(
            id = commandId,
            title = "Probe",
            description = "Probe command",
            onExecute = { SlashCommandResult.Done },
        )
        var executorLookups = 0
        val harness = ShortcutHarness(
            policy = EditorInteractionPolicy.ReadOnly,
            slashEnabled = true,
            isSlashAnchor = { true },
            slashHighlightedCommandId = { commandId },
            slashPopupItems = { listOf(command) },
            slashCommandExecutor = {
                executorLookups++
                null
            },
        )

        assertFalse(
            harness.handler.onPreviewSlashPopupKeyEvent(
                SlashPopupKeyEvent(
                    key = Key.Enter,
                    type = KeyEventType.KeyDown,
                )
            )
        )

        assertEquals(0, executorLookups)
        assertEquals(0, harness.batchBreakerCount)
    }

    @Test
    fun `disabled slash gate ignores slash popup navigation in editable policy`() {
        val commandId = SlashCommandId("custom.item")
        val command = SlashCommandAction(
            id = commandId,
            title = "Item",
            description = "Item command",
            onExecute = { SlashCommandResult.Done },
        )
        val callbacks = RecordingBlockCallbacks()
        val harness = ShortcutHarness(
            callbacks = callbacks,
            slashEnabled = false,
            isSlashAnchor = { true },
            slashHighlightedCommandId = { null },
            slashPopupItems = { listOf(command) },
        )

        assertFalse(
            harness.handler.onPreviewSlashPopupKeyEvent(
                SlashPopupKeyEvent(
                    key = Key.DirectionDown,
                    type = KeyEventType.KeyDown,
                )
            )
        )

        assertEquals(emptyList<EditorAction>(), callbacks.actions)
    }

    @Test
    fun `policy flip via Compose state propagates to captured key handler at next invocation`() {
        // Simulates the runtime contract that rememberUpdatedState provides:
        // the handler's policyProvider reads the current State<T>.value on
        // each invocation, so a previously constructed handler honors a
        // post-construction policy flip without being rebuilt.
        val formattingActions = RecordingFormattingActions()
        val policyState = mutableStateOf(EditorInteractionPolicy.Editable)
        var batchBreakerCount = 0

        val handler = TextBlockKeyHandler(
            formattingActions = formattingActions,
            policyProvider = { policyState.value },
            slashEnabledProvider = { true },
            callbacks = NoOpBlockCallbacks,
            isSlashAnchor = { false },
            slashHighlightedCommandId = { null },
            slashPopupItems = { emptyList() },
            slashCommandExecutor = { null },
            onBatchBreaker = { batchBreakerCount++ },
            onPasteShortcutDetected = {},
            onUndo = {},
            onRedo = {},
        )

        // Verify editable invocation toggles style.
        handler.onPreviewShortcutEvent(
            shortcutKeyEvent(
                key = Key.B,
                type = KeyEventType.KeyDown,
                hasShortcutModifier = true,
            )
        )
        handler.onPreviewShortcutEvent(
            shortcutKeyEvent(key = Key.B, type = KeyEventType.KeyUp)
        )

        // Flip policy through the State surface — same shape Compose runtime uses.
        policyState.value = EditorInteractionPolicy.ReadOnly

        // Re-invoke the same captured handler instance. The provider re-reads
        // policyState.value at invocation time, so no formatting occurs.
        handler.onPreviewShortcutEvent(
            shortcutKeyEvent(
                key = Key.B,
                type = KeyEventType.KeyDown,
                hasShortcutModifier = true,
            )
        )

        assertEquals(listOf<SpanStyle>(SpanStyle.Bold), formattingActions.toggledStyles)
        assertEquals(1, batchBreakerCount)
    }

    @Test
    fun `slash gate flip via Compose state suppresses popup navigation in captured handler`() {
        val commandId = SlashCommandId("custom.item")
        val command = SlashCommandAction(
            id = commandId,
            title = "Item",
            description = "Item command",
            onExecute = { SlashCommandResult.Done },
        )
        val callbacks = RecordingBlockCallbacks()
        val slashEnabledState = mutableStateOf(true)

        val handler = TextBlockKeyHandler(
            formattingActions = RecordingFormattingActions(),
            policyProvider = { EditorInteractionPolicy.Editable },
            slashEnabledProvider = { slashEnabledState.value },
            callbacks = callbacks,
            isSlashAnchor = { true },
            slashHighlightedCommandId = { null },
            slashPopupItems = { listOf(command) },
            slashCommandExecutor = { null },
            onBatchBreaker = {},
            onPasteShortcutDetected = {},
            onUndo = {},
            onRedo = {},
        )

        handler.onPreviewSlashPopupKeyEvent(
            SlashPopupKeyEvent(key = Key.DirectionDown, type = KeyEventType.KeyDown)
        )

        // Disable slash subsystem at runtime; the captured handler must drop
        // popup navigation at invocation time.
        slashEnabledState.value = false

        handler.onPreviewSlashPopupKeyEvent(
            SlashPopupKeyEvent(key = Key.DirectionDown, type = KeyEventType.KeyDown)
        )

        assertEquals<List<EditorAction>>(listOf(HighlightSlashCommand(commandId)), callbacks.actions)
    }

    @Test
    fun `editable slash popup navigation still highlights next item`() {
        val commandId = SlashCommandId("custom.item")
        val command = SlashCommandAction(
            id = commandId,
            title = "Item",
            description = "Item command",
            onExecute = { SlashCommandResult.Done },
        )
        val callbacks = RecordingBlockCallbacks()
        val harness = ShortcutHarness(
            callbacks = callbacks,
            slashEnabled = true,
            isSlashAnchor = { true },
            slashHighlightedCommandId = { null },
            slashPopupItems = { listOf(command) },
        )

        assertTrue(
            harness.handler.onPreviewSlashPopupKeyEvent(
                SlashPopupKeyEvent(
                    key = Key.DirectionDown,
                    type = KeyEventType.KeyDown,
                )
            )
        )

        assertEquals<List<EditorAction>>(listOf(HighlightSlashCommand(commandId)), callbacks.actions)
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
        val policy: EditorInteractionPolicy = EditorInteractionPolicy.Editable,
        val callbacks: BlockCallbacks = NoOpBlockCallbacks,
        val slashEnabled: Boolean = policy.canUseSlashCommands && policy.canEditText,
        val isSlashAnchor: () -> Boolean = { false },
        val slashHighlightedCommandId: () -> SlashCommandId? = { null },
        val slashPopupItems: () -> List<SlashCommandItem> = { emptyList() },
        val slashCommandExecutor: () -> SlashCommandExecutor? = { null },
    ) {
        var undoCount: Int = 0
        var redoCount: Int = 0
        var batchBreakerCount: Int = 0
        var pasteShortcutCount: Int = 0

        val handler = TextBlockKeyHandler(
            formattingActions = formattingActions,
            policyProvider = { policy },
            slashEnabledProvider = { slashEnabled },
            callbacks = callbacks,
            isSlashAnchor = isSlashAnchor,
            slashHighlightedCommandId = slashHighlightedCommandId,
            slashPopupItems = slashPopupItems,
            slashCommandExecutor = slashCommandExecutor,
            onBatchBreaker = { batchBreakerCount++ },
            onPasteShortcutDetected = { pasteShortcutCount++ },
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

    private class RecordingBlockCallbacks : BlockCallbacks {
        val actions = mutableListOf<EditorAction>()

        override fun dispatch(action: EditorAction) {
            actions += action
        }

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
