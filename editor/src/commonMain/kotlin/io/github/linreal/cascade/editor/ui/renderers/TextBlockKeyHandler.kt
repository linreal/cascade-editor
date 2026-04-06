package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import io.github.linreal.cascade.editor.action.CloseSlashCommand
import io.github.linreal.cascade.editor.action.HighlightSlashCommand
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.richtext.FormattingActions
import io.github.linreal.cascade.editor.slash.SlashCommandItem
import io.github.linreal.cascade.editor.slash.SlashCommandId
import io.github.linreal.cascade.editor.ui.SlashPopupDefaults
import io.github.linreal.cascade.editor.slash.SlashCommandExecutor

/**
 * Handles keyboard events for [TextBlockField]: history shortcuts
 * (Cmd/Ctrl+Z, Shift+Cmd/Ctrl+Z), formatting shortcuts (Cmd/Ctrl+B/I/U),
 * and slash popup navigation (Up/Down/Enter/Escape).
 *
 * Hardware-keyboard shortcuts include deduplication guards because iOS can deliver
 * duplicate `KeyDown` events for the same physical press.
 */
internal class TextBlockKeyHandler(
    private val formattingActions: FormattingActions?,
    private val callbacks: BlockCallbacks,
    private val isSlashAnchor: () -> Boolean,
    private val slashHighlightedCommandId: () -> SlashCommandId?,
    private val slashPopupItems: () -> List<SlashCommandItem>,
    private val slashCommandExecutor: () -> SlashCommandExecutor?,
    private val onBatchBreaker: () -> Unit,
    private val onPasteShortcutDetected: () -> Unit,
    private val onUndo: () -> Unit,
    private val onRedo: () -> Unit,
) {
    /** Tracks which formatting key is currently held to prevent duplicate iOS `KeyDown`. */
    private var handledFormattingKey: Key? = null
    /** Tracks which history key is currently held to prevent duplicate iOS `KeyDown`. */
    private var handledHistoryKey: Key? = null

    /**
     * Returns `true` if the event was consumed.
     */
    fun onPreviewKeyEvent(keyEvent: KeyEvent): Boolean {
        detectPasteShortcut(keyEvent)
        val shortcutEvent = keyEvent.toShortcutKeyEvent()
        if (onPreviewShortcutEvent(shortcutEvent)) return true
        if (handleSlashPopupNav(keyEvent)) return true
        return false
    }

    /**
     * Handles shortcut resolution after normalizing Cmd and Ctrl into one modifier.
     *
     * This helper exists so common tests can exercise shortcut behavior without
     * constructing platform-native [KeyEvent] instances.
     */
    internal fun onPreviewShortcutEvent(keyEvent: ShortcutKeyEvent): Boolean {
        if (handleHistoryShortcut(keyEvent)) return true
        if (handleFormattingShortcut(keyEvent)) return true
        return false
    }

    /**
     * Best-effort explicit paste signal for the typing coalescer.
     *
     * Compose does not currently expose reliable paste origin metadata on the
     * committed text observer path, so shortcut detection gives us a stronger
     * hint before the eventual text commit arrives.
     */
    private fun detectPasteShortcut(keyEvent: KeyEvent) {
        val isShortcutModifier = keyEvent.isMetaPressed || keyEvent.isCtrlPressed
        if (!isShortcutModifier) return
        if (keyEvent.type != KeyEventType.KeyDown) return
        if (keyEvent.key == Key.V) {
            onPasteShortcutDetected()
        }
    }

    // history shortcuts (Cmd/Ctrl + Z, Shift+Cmd/Ctrl + Z)

    private fun handleHistoryShortcut(keyEvent: ShortcutKeyEvent): Boolean {
        if (keyEvent.type == KeyEventType.KeyUp && handledHistoryKey == keyEvent.key) {
            handledHistoryKey = null
            return true
        }

        val shortcut = historyShortcutForKey(keyEvent.key, keyEvent.isShiftPressed)
        if (!keyEvent.hasShortcutModifier || shortcut == null) {
            return false
        }

        return when (keyEvent.type) {
            KeyEventType.KeyUp -> true
            KeyEventType.KeyDown -> {
                if (handledHistoryKey != keyEvent.key) {
                    handledHistoryKey = keyEvent.key
                    when (shortcut) {
                        HistoryShortcut.Undo -> onUndo()
                        HistoryShortcut.Redo -> onRedo()
                    }
                }
                true
            }
            else -> true
        }
    }

    // formatting shortcuts (Cmd/Ctrl + B/I/U)

    private fun handleFormattingShortcut(keyEvent: ShortcutKeyEvent): Boolean {
        if (keyEvent.type == KeyEventType.KeyUp && handledFormattingKey == keyEvent.key) {
            handledFormattingKey = null
            return true
        }

        if (keyEvent.hasShortcutModifier && formattingActions != null) {
            val style = formattingStyleForKey(keyEvent.key) ?: return false
            return when (keyEvent.type) {
                KeyEventType.KeyUp -> true
                KeyEventType.KeyDown -> {
                    if (handledFormattingKey != keyEvent.key) {
                        handledFormattingKey = keyEvent.key
                        onBatchBreaker()
                        formattingActions.toggleStyle(style)
                    }
                    true // consume duplicate KeyDown too
                }
                else -> true
            }
        }
        return false
    }

    // Slash popup keyboard navigation

    private fun handleSlashPopupNav(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false
        if (!isSlashAnchor()) return false

        return when (keyEvent.key) {
            Key.DirectionUp -> {
                val nextId = SlashPopupDefaults.resolveNextHighlight(
                    slashHighlightedCommandId(), slashPopupItems(), direction = -1
                )
                callbacks.dispatch(HighlightSlashCommand(nextId))
                true
            }
            Key.DirectionDown -> {
                val nextId = SlashPopupDefaults.resolveNextHighlight(
                    slashHighlightedCommandId(), slashPopupItems(), direction = 1
                )
                callbacks.dispatch(HighlightSlashCommand(nextId))
                true
            }
            Key.Enter, Key.NumPadEnter -> {
                val id = slashHighlightedCommandId()
                val item = if (id != null) slashPopupItems().firstOrNull { it.id == id } else null
                val executor = slashCommandExecutor()
                if (item != null && executor != null) {
                    onBatchBreaker()
                    executor.execute(item)
                    true
                } else {
                    false
                }
            }
            Key.Escape -> {
                callbacks.dispatch(CloseSlashCommand)
                true
            }
            else -> false
        }
    }
}

/**
 * Normalized shortcut event used by [TextBlockKeyHandler].
 *
 * Cmd and Ctrl are intentionally collapsed into [hasShortcutModifier] because v1
 * editor shortcuts treat them as equivalent on different platforms.
 */
internal data class ShortcutKeyEvent(
    val key: Key,
    val type: KeyEventType,
    val hasShortcutModifier: Boolean,
    val isShiftPressed: Boolean,
)

private enum class HistoryShortcut {
    Undo,
    Redo,
}

/**
 * Maps a [Key] to a formatting [SpanStyle], checking both Android/Desktop key codes
 * and iOS USB HID key codes (Compose Multiplatform on iOS passes raw HID codes
 * instead of mapping to Android-equivalent values).
 */
private fun formattingStyleForKey(key: Key): SpanStyle? = when (key) {
    Key.B -> SpanStyle.Bold
    Key.I -> SpanStyle.Italic
    Key.U -> SpanStyle.Underline
    else -> when (key.keyCode) {
        // iOS hardware keyboard: USB HID usage codes (b=0x05, i=0x0C, u=0x18)
        0x05L -> SpanStyle.Bold
        0x0CL -> SpanStyle.Italic
        0x18L -> SpanStyle.Underline
        else -> null
    }
}

private fun historyShortcutForKey(
    key: Key,
    isShiftPressed: Boolean,
): HistoryShortcut? {
    val isUndoRedoKey = when (key) {
        Key.Z -> true
        else -> key.keyCode == 0x1DL
    }
    if (!isUndoRedoKey) return null
    return if (isShiftPressed) HistoryShortcut.Redo else HistoryShortcut.Undo
}

private fun KeyEvent.toShortcutKeyEvent(): ShortcutKeyEvent {
    return ShortcutKeyEvent(
        key = key,
        type = type,
        hasShortcutModifier = isMetaPressed || isCtrlPressed,
        isShiftPressed = isShiftPressed,
    )
}
