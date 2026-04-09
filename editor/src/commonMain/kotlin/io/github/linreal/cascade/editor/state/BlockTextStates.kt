package io.github.linreal.cascade.editor.state

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
     * Monotonically increasing counter, incremented on [clear].
     * Used as a `remember` key so composables re-fetch from the map after a bulk reset.
     */
    internal var generation: Int by mutableIntStateOf(0)
        private set
    // Last-write-wins: only the most recent programmatic text per block is tracked.
    // Current callers (mergeInto, setText) never issue multiple programmatic edits
    // on the same block before the observer consumes the entry.
    private val pendingProgrammaticCommits = mutableMapOf<BlockId, String>()

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
        pendingProgrammaticCommits.remove(blockId)
    }

    /**
     * Gets visible text (without ZWSP sentinel) for a block.
     */
    public fun getVisibleText(blockId: BlockId): String? {
        return states[blockId]?.visibleTextInfo()?.text
    }

    /**
     * Gets the current selection for a block in visible-text coordinates.
     *
     * The returned range excludes the internal sentinel offset and preserves
     * selection direction for reversed ranges.
     */
    public fun getSelection(blockId: BlockId): TextRange? {
        val state = states[blockId] ?: return null
        val visibleTextInfo = state.visibleTextInfo()
        val visibleLength = visibleTextInfo.text.length
        val rawSelection = state.selection
        return TextRange(
            start = (rawSelection.start - visibleTextInfo.sentinelOffset).coerceIn(0, visibleLength),
            end = (rawSelection.end - visibleTextInfo.sentinelOffset).coerceIn(0, visibleLength),
        )
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
        val mergedText = targetText + sourceText
        val cursorPosition = targetText.length // Position in visible text

        targetState.edit {
            append(sourceText)
            // +1 for ZWSP offset
            selection = TextRange(cursorPosition + 1)
        }

        pendingProgrammaticCommits[targetId] = mergedText
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
        val currentVisible = state.text.toString().removePrefix(ZWSP)
        state.edit {
            // Clear and replace all content
            delete(0, length)
            append("$ZWSP$text")
            // +1 for ZWSP offset
            selection = TextRange((cursorPosition ?: text.length) + 1)
        }
        // Only register a programmatic commit when the text actually changed.
        // When text is identical (e.g. split at end of block), Compose won't fire
        // a snapshot notification, so the commit would never be consumed, causing
        // subsequent user edits to be misidentified as programmatic.
        if (text != currentVisible) {
            pendingProgrammaticCommits[blockId] = text
        }
    }

    /**
     * Sets an arbitrary selection for a block in visible-text coordinates.
     *
     * This is used by history replay and other programmatic cursor/selection
     * restoration paths. Selection-only updates do not register a programmatic
     * text commit because the visible text is unchanged.
     */
    public fun setSelection(blockId: BlockId, selection: TextRange) {
        val state = states[blockId] ?: return
        val visibleTextInfo = state.visibleTextInfo()
        val visibleLength = visibleTextInfo.text.length
        val safeSelection = TextRange(
            start = selection.start.coerceIn(0, visibleLength),
            end = selection.end.coerceIn(0, visibleLength),
        )
        state.edit {
            this.selection = TextRange(
                safeSelection.start + visibleTextInfo.sentinelOffset,
                safeSelection.end + visibleTextInfo.sentinelOffset,
            )
        }
    }

    /**
     * Consumes the expected visible text for the next programmatic commit on [blockId].
     *
     * Returns null when no programmatic commit is pending.
     */
    public fun consumeProgrammaticCommit(blockId: BlockId): String? {
        return pendingProgrammaticCommits.remove(blockId)
    }

    /**
     * Returns true if a programmatic commit is pending for [blockId].
     *
     * Non-destructive peek — does not consume the entry. Use this when
     * multiple observers need to know about a pending commit before the
     * authoritative consumer calls [consumeProgrammaticCommit].
     */
    internal fun hasPendingProgrammaticCommit(blockId: BlockId): Boolean {
        return pendingProgrammaticCommits.containsKey(blockId)
    }

    /**
     * Replaces a visible-text range with [replacement] and updates the cursor.
     *
     * Registers the result as a programmatic commit so that
     * `SpanMaintenanceTextObserver` skips/rebases this change.
     *
     * @param blockId Target block.
     * @param start Start offset in visible-text coordinates (inclusive).
     * @param endExclusive End offset in visible-text coordinates (exclusive).
     * @param replacement Text to insert at the replaced range. Empty string = pure deletion.
     * @param cursorPositionAfter Cursor position in visible-text coordinates after the edit.
     *        When null, the cursor is placed at the end of the inserted replacement.
     * @return The new visible text, or null if the block does not exist.
     */
    public fun replaceVisibleRange(
        blockId: BlockId,
        start: Int,
        endExclusive: Int,
        replacement: String,
        cursorPositionAfter: Int? = null,
    ): String? {
        val state = states[blockId] ?: return null
        val visible = state.text.toString().removePrefix(ZWSP)
        val safeStart = start.coerceIn(0, visible.length)
        val safeEnd = endExclusive.coerceIn(safeStart, visible.length)

        val newVisible = visible.substring(0, safeStart) + replacement + visible.substring(safeEnd)
        val cursor = cursorPositionAfter ?: (safeStart + replacement.length)

        state.edit {
            delete(0, length)
            append("$ZWSP$newVisible")
            selection = TextRange(cursor.coerceIn(0, newVisible.length) + 1) // +1 for ZWSP
        }

        pendingProgrammaticCommits[blockId] = newVisible
        return newVisible
    }

    /**
     * Sets cursor position for a block.
     *
     * @param blockId Target block
     * @param cursorPosition Cursor position in visible text coordinates
     */
    public fun setCursorPosition(blockId: BlockId, cursorPosition: Int) {
        setSelection(blockId, TextRange(cursorPosition))
    }

    /**
     * Cleans up states for blocks that no longer exist.
     * Call this when blocks are removed from EditorState.
     */
    public fun cleanup(existingBlockIds: Set<BlockId>) {
        val toRemove = states.keys - existingBlockIds
        toRemove.forEach {
            states.remove(it)
            pendingProgrammaticCommits.remove(it)
        }
        val staleCommits = pendingProgrammaticCommits.keys - existingBlockIds
        staleCommits.forEach { pendingProgrammaticCommits.remove(it) }
    }

    /**
     * Clears all states. Useful for editor reset.
     */
    public fun clear() {
        states.clear()
        pendingProgrammaticCommits.clear()
        generation++
    }
}

private data class VisibleTextInfo(
    val text: String,
    val sentinelOffset: Int,
)

private fun TextFieldState.visibleTextInfo(): VisibleTextInfo {
    val rawText = text.toString()
    val sentinelOffset = if (rawText.startsWith(ZWSP)) 1 else 0
    val visibleText = if (sentinelOffset == 0) rawText else rawText.substring(1)
    return VisibleTextInfo(
        text = visibleText,
        sentinelOffset = sentinelOffset,
    )
}
