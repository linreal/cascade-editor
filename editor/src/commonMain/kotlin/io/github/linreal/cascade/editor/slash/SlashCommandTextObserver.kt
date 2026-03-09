package io.github.linreal.cascade.editor.slash

import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.state.SlashQueryRange
import kotlin.math.min

/**
 * Observes committed visible-text changes and cursor movements to
 * manage slash command session lifecycle.
 *
 * Detects freshly typed `/`, maintains [SlashQueryRange] as the user edits,
 * and dismisses the session when the range becomes invalid.
 *
 * Only single-character insertions of `/` trigger a new session. Multi-character
 * insertions (paste, autocomplete) and programmatic text changes are excluded.
 *
 * @param blockId The block this observer is tracking.
 * @param onOpen Called when a new slash session should be opened.
 * @param onUpdate Called when the active session query/range should be updated.
 * @param onClose Called when the active session should be closed.
 * @param initialVisibleText The visible text at observer creation time.
 */
internal class SlashCommandTextObserver(
    private val blockId: BlockId,
    private val onOpen: (blockId: BlockId, queryRange: SlashQueryRange, query: String) -> Unit,
    private val onUpdate: (query: String, queryRange: SlashQueryRange) -> Unit,
    private val onClose: () -> Unit,
    initialVisibleText: String,
) {
    private var previousVisibleText: String = initialVisibleText

    /** Visible-text index of the `/` character. Negative when no session is tracked. */
    private var slashStart: Int = -1

    /** Exclusive end of the tracked range. Negative when no session is tracked. */
    private var rangeEnd: Int = -1

    /** Whether this observer is actively tracking a slash session. */
    internal val isTracking: Boolean get() = slashStart >= 0

    /**
     * Processes a committed visible-text change.
     *
     * @param currentVisibleText The new visible text (without ZWSP sentinel).
     * @param isProgrammatic True when a programmatic commit is pending for this block.
     *        Programmatic changes never open a session and may close one if the `/` is removed.
     * @param cursorPosition Collapsed cursor position in visible-text coordinates,
     *        or -1 when the selection is not collapsed.
     */
    internal fun onTextChanged(
        currentVisibleText: String,
        isProgrammatic: Boolean,
        cursorPosition: Int,
    ) {
        if (isProgrammatic) {
            handleProgrammaticChange(currentVisibleText)
            return
        }

        val edit = computeEdit(previousVisibleText, currentVisibleText)
        previousVisibleText = currentVisibleText

        if (edit == null) return

        if (!isTracking) {
            maybeOpenSession(edit, currentVisibleText)
        } else {
            updateOrCloseSession(edit, currentVisibleText, cursorPosition)
        }
    }

    /**
     * Processes a selection/cursor change that did not involve a text change.
     *
     * Closes the session when the cursor or selection extends outside the tracked range.
     *
     * @param selectionStart Start of the visible-text selection.
     * @param selectionEnd End of the visible-text selection.
     */
    internal fun onSelectionChanged(selectionStart: Int, selectionEnd: Int) {
        if (!isTracking) return

        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)

        if (start < slashStart || end > rangeEnd) {
            closeSession()
        }
    }

    /**
     * Called when the observed block loses text field focus.
     */
    internal fun onFocusLost() {
        if (isTracking) {
            closeSession()
        }
    }

    /**
     * Resets internal tracking without dispatching a close action.
     *
     * Call this when the session is closed externally (command execution,
     * Escape key, anchor block deletion) to keep the observer in sync.
     */
    internal fun notifySessionClosed() {
        slashStart = -1
        rangeEnd = -1
    }

    // ---- private implementation ----

    private fun handleProgrammaticChange(currentVisibleText: String) {
        if (isTracking) {
            if (slashStart >= currentVisibleText.length ||
                currentVisibleText[slashStart] != '/'
            ) {
                closeSession()
            }
        }
        previousVisibleText = currentVisibleText
    }

    private fun maybeOpenSession(edit: TextEdit, text: String) {
        // Only trigger on single-character insertion of '/'
        if (edit.deletedLength != 0 || edit.insertedLength != 1) return
        if (text[edit.start] != '/') return

        slashStart = edit.start
        rangeEnd = edit.start + 1
        onOpen(blockId, SlashQueryRange(slashStart, rangeEnd), "")
    }

    private fun updateOrCloseSession(edit: TextEdit, text: String, cursor: Int) {
        val editEnd = edit.start + edit.deletedLength
        val delta = edit.insertedLength - edit.deletedLength

        // Adjust tracked positions based on where the edit occurred
        when {
            // Edit entirely before the tracked range: shift both
            editEnd <= slashStart -> {
                slashStart += delta
                rangeEnd += delta
            }
            // Edit within or at the edge of the tracked range: adjust end
            edit.start >= slashStart && edit.start <= rangeEnd -> {
                rangeEnd = (rangeEnd + delta).coerceAtLeast(slashStart + 1)
            }
            // Edit starts before slashStart and overlaps: adjust both
            edit.start < slashStart -> {
                slashStart = (slashStart + delta).coerceAtLeast(0)
                rangeEnd = (rangeEnd + delta).coerceAtLeast(slashStart + 1)
            }
            // Edit entirely after range: no adjustment needed
        }

        // Validate: '/' must still exist at slashStart
        if (slashStart < 0 || slashStart >= text.length || text[slashStart] != '/') {
            closeSession()
            return
        }

        // Clamp rangeEnd to text bounds
        rangeEnd = rangeEnd.coerceIn(slashStart + 1, text.length + 1)

        // Validate cursor position (when available)
        if (cursor >= 0 && (cursor < slashStart || cursor > rangeEnd)) {
            closeSession()
            return
        }

        val queryEnd = rangeEnd.coerceAtMost(text.length)
        val query = if (slashStart + 1 < queryEnd) {
            text.substring(slashStart + 1, queryEnd)
        } else {
            ""
        }
        onUpdate(query, SlashQueryRange(slashStart, queryEnd))
    }

    private fun closeSession() {
        slashStart = -1
        rangeEnd = -1
        onClose()
    }

    private fun computeEdit(previous: String, current: String): TextEdit? {
        if (previous == current) return null

        val minLength = min(previous.length, current.length)
        var prefixLength = 0
        while (prefixLength < minLength && previous[prefixLength] == current[prefixLength]) {
            prefixLength++
        }

        val maxSuffix = min(
            previous.length - prefixLength,
            current.length - prefixLength,
        )
        var suffixLength = 0
        while (
            suffixLength < maxSuffix &&
            previous[previous.length - 1 - suffixLength] == current[current.length - 1 - suffixLength]
        ) {
            suffixLength++
        }

        val deletedLength = previous.length - prefixLength - suffixLength
        val insertedLength = current.length - prefixLength - suffixLength
        if (deletedLength == 0 && insertedLength == 0) return null

        return TextEdit(
            start = prefixLength,
            deletedLength = deletedLength.coerceAtLeast(0),
            insertedLength = insertedLength.coerceAtLeast(0),
        )
    }

    internal data class TextEdit(
        val start: Int,
        val deletedLength: Int,
        val insertedLength: Int,
    )
}
