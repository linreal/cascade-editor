package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
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
 * Handles keyboard events for [TextBlockField]: formatting shortcuts (Cmd/Ctrl+B/I/U)
 * and slash popup navigation (Up/Down/Enter/Escape).
 *
 * Formatting shortcuts include a deduplication guard because iOS delivers duplicate
 * KeyDown events for the same key press, which would toggle the style on then
 * immediately off.
 */
internal class TextBlockKeyHandler(
    private val formattingActions: FormattingActions?,
    private val callbacks: BlockCallbacks,
    private val isSlashAnchor: () -> Boolean,
    private val slashHighlightedCommandId: () -> SlashCommandId?,
    private val slashPopupItems: () -> List<SlashCommandItem>,
    private val slashCommandExecutor: () -> SlashCommandExecutor?,
) {
    /** Tracks which formatting key is currently held to prevent duplicate iOS KeyDown. */
    private var handledFormattingKey: Key? = null

    /**
     * Returns `true` if the event was consumed.
     */
    fun onPreviewKeyEvent(keyEvent: KeyEvent): Boolean {
        if (handleFormattingShortcut(keyEvent)) return true
        if (handleSlashPopupNav(keyEvent)) return true
        return false
    }

    // formatting shortcuts (Cmd/Ctrl + B/I/U)

    private fun handleFormattingShortcut(keyEvent: KeyEvent): Boolean {
        val isShortcutModifier = keyEvent.isMetaPressed || keyEvent.isCtrlPressed
        if (isShortcutModifier && formattingActions != null) {
            val style = formattingStyleForKey(keyEvent.key) ?: return false
            return when (keyEvent.type) {
                KeyEventType.KeyUp -> {
                    handledFormattingKey = null
                    true
                }
                KeyEventType.KeyDown -> {
                    if (handledFormattingKey != keyEvent.key) {
                        handledFormattingKey = keyEvent.key
                        formattingActions.toggleStyle(style)
                    }
                    true // consume duplicate KeyDown too
                }
                else -> true
            }
        }
        // Clear guard when the modifier is released
        if (keyEvent.type == KeyEventType.KeyUp) {
            handledFormattingKey = null
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
