package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import io.github.linreal.cascade.editor.core.BlockId

/**
 * Holds the caret rectangle in window coordinates for the active slash session.
 *
 * Written by [TextBlockField] when the block is the slash session anchor.
 * Read by [SlashCommandPopup] for positioning.
 */
internal class SlashCaretRectHolder {
    private var ownerBlockId: BlockId? by mutableStateOf(null)
    var rect: Rect? by mutableStateOf(null)
        private set

    fun update(blockId: BlockId, caretRect: Rect) {
        ownerBlockId = blockId
        rect = caretRect
    }

    fun clear(blockId: BlockId) {
        if (ownerBlockId == blockId) {
            ownerBlockId = null
            rect = null
        }
    }

    fun clearAll() {
        ownerBlockId = null
        rect = null
    }
}

/**
 * Provides the [SlashCaretRectHolder] to the slash popup and text field.
 */
internal val LocalSlashCaretRect: ProvidableCompositionLocal<SlashCaretRectHolder> =
    compositionLocalOf { SlashCaretRectHolder() }
