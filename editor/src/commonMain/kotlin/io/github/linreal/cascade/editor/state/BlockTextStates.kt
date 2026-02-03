package io.github.linreal.cascade.editor.state

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.delete
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.core.BlockId

private const val ZWSP = "\u200B"

/**
 * Manages TextFieldState instances for text-capable blocks.
 * This is the single source of truth for text content during editing.
 *
 * Each text block gets its own TextFieldState, which persists across
 * recompositions and can be directly manipulated for operations like
 * merge, split, and programmatic text updates.
 */
@Stable
public class BlockTextStates {
    private val states = mutableMapOf<BlockId, TextFieldState>()

    /**
     * Gets existing state or creates new one with initial text.
     * The ZWSP sentinel is automatically prepended for backspace detection.
     *
     * @param blockId The block identifier
     * @param initialText Initial text content (without ZWSP)
     * @param initialCursorPosition Initial cursor position in visible text coordinates.
     *        Defaults to 0 (start of text). Only used when creating new state.
     * @return The TextFieldState for this block
     */
    public fun getOrCreate(
        blockId: BlockId,
        initialText: String,
        initialCursorPosition: Int = 0
    ): TextFieldState {
        return states.getOrPut(blockId) {
            TextFieldState(initialText = "$ZWSP$initialText").also { state ->
                // Set initial cursor position (+1 for ZWSP offset)
                val safePosition = initialCursorPosition.coerceIn(0, initialText.length)
                state.edit {
                    selection = TextRange(safePosition + 1)
                }
            }
        }
    }

    /**
     * Gets state if it exists.
     */
    public fun get(blockId: BlockId): TextFieldState? = states[blockId]

    /**
     * Removes state for a deleted block.
     */
    public fun remove(blockId: BlockId) {
        states.remove(blockId)
    }

    /**
     * Gets visible text (without ZWSP sentinel) for a block.
     */
    public fun getVisibleText(blockId: BlockId): String? {
        return states[blockId]?.text?.toString()?.removePrefix(ZWSP)
    }

    /**
     * Extracts all text content for serialization/persistence.
     * Returns map of blockId to visible text (without ZWSP).
     */
    public fun extractAllText(): Map<BlockId, String> {
        return states.mapValues { (_, state) ->
            state.text.toString().removePrefix(ZWSP)
        }
    }

    /**
     * Merges text from source block into target block.
     * Appends source text to target and places cursor at the merge point.
     *
     * @param sourceId Block whose text will be appended
     * @param targetId Block that will receive the merged text
     * @return The cursor position (in visible text coordinates) after merge,
     *         or null if merge couldn't be performed
     */
    public fun mergeInto(sourceId: BlockId, targetId: BlockId): Int? {
        val sourceState = states[sourceId] ?: return null
        val targetState = states[targetId] ?: return null

        val sourceText = sourceState.text.toString().removePrefix(ZWSP)
        val targetText = targetState.text.toString().removePrefix(ZWSP)
        val cursorPosition = targetText.length // Position in visible text

        targetState.edit {
            append(sourceText)
            // +1 for ZWSP offset
            selection = TextRange(cursorPosition + 1)
        }

        remove(sourceId)
        return cursorPosition
    }

    /**
     * Sets text and cursor position for a block.
     * Used for programmatic updates (e.g., undo/redo, paste).
     *
     * @param blockId Target block
     * @param text New text content (without ZWSP)
     * @param cursorPosition Cursor position in visible text coordinates, or null for end
     */
    public fun setText(blockId: BlockId, text: String, cursorPosition: Int? = null) {
        val state = states[blockId] ?: return
        state.edit {
            // Clear and replace all content
            delete(0, length)
            append("$ZWSP$text")
            // +1 for ZWSP offset
            selection = TextRange((cursorPosition ?: text.length) + 1)
        }
    }

    /**
     * Sets cursor position for a block.
     *
     * @param blockId Target block
     * @param cursorPosition Cursor position in visible text coordinates
     */
    public fun setCursorPosition(blockId: BlockId, cursorPosition: Int) {
        val state = states[blockId] ?: return
        val maxPosition = (state.text.length - 1).coerceAtLeast(0) // -1 for ZWSP
        val safePosition = cursorPosition.coerceIn(0, maxPosition)
        state.edit {
            selection = TextRange(safePosition + 1) // +1 for ZWSP
        }
    }

    /**
     * Cleans up states for blocks that no longer exist.
     * Call this when blocks are removed from EditorState.
     */
    public fun cleanup(existingBlockIds: Set<BlockId>) {
        val toRemove = states.keys - existingBlockIds
        toRemove.forEach { states.remove(it) }
    }

    /**
     * Clears all states. Useful for editor reset.
     */
    public fun clear() {
        states.clear()
    }
}
