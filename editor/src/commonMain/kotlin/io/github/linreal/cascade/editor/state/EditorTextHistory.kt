package io.github.linreal.cascade.editor.state

import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import kotlin.time.TimeSource

private const val TEXT_HISTORY_MERGE_WINDOW_MS: Long = 500L

private val TextHistoryClockOrigin = TimeSource.Monotonic.markNow()
private val MergeAdjacentBlockTextEntriesPolicy = MergePolicy.TryMerge(::mergeAdjacentBlockTextEntries)

/**
 * History push prepared from a live text commit.
 */
internal data class PendingTextHistoryPush(
    val entry: HistoryEntry,
    val policy: MergePolicy,
)

/**
 * Holder-facing bridge for commands that mutate a block outside the text field's
 * `snapshotFlow` observer.
 *
 * Examples: selected-range formatting, collapsed pending-style toggles, and
 * future structural integrations that need to terminate or resync the active
 * typing batch without replaying through `TextBlockField`.
 */
internal interface TextHistoryTrackerSink {
    fun noteBatchBreaker()
    fun syncToCheckpoint(checkpoint: EditorCheckpoint)

    /**
     * Replay-only fast path for local block restores.
     *
     * Callers should use this when they already know the exact restored block
     * payload and UI target, rather than paying for a fresh full checkpoint.
     */
    fun syncToBlockContent(
        blockId: BlockId,
        content: BlockContent.Text,
        ui: EditingUiState,
    )
}

/**
 * Tracks the current text-history batch for one live text field.
 *
 * The tracker owns only capture/coalescing state. Callers remain responsible
 * for deciding when a committed text snapshot represents a real user edit.
 */
internal class TextEditHistoryTracker(
    initialCheckpoint: EditorCheckpoint,
    private val nowMs: () -> Long = {
        TextHistoryClockOrigin.elapsedNow().inWholeMilliseconds
    },
) : TextHistoryTrackerSink {
    private val coalescer = TextEditCoalescer(nowMs)
    private var baselineCheckpoint: EditorCheckpoint = initialCheckpoint

    override fun noteBatchBreaker() {
        coalescer.noteBatchBreaker()
    }

    /**
     * Re-anchors the tracker to an externally produced document/UI snapshot.
     *
     * This is the safe path when a command updates the block without producing a
     * committed text diff through the live text observer.
     */
    override fun syncToCheckpoint(checkpoint: EditorCheckpoint) {
        coalescer.noteBatchBreaker()
        baselineCheckpoint = checkpoint
    }

    /**
     * Fast re-anchor used by one-block replay.
     *
     * Replay already knows the exact block payload being restored, so patching
     * the existing baseline avoids another full-document checkpoint capture
     * while `isApplyingHistory` is active.
     */
    override fun syncToBlockContent(
        blockId: BlockId,
        content: BlockContent.Text,
        ui: EditingUiState,
    ) {
        coalescer.noteBatchBreaker()
        baselineCheckpoint = baselineCheckpoint.copy(
            blocks = baselineCheckpoint.blocks.replaceTextBlockContent(blockId, content),
            ui = ui,
        )
    }

    internal fun noteExplicitPaste() {
        coalescer.noteExplicitPaste()
    }

    internal fun noteSelectionChanged(
        selection: TextRange,
        ui: EditingUiState,
    ) {
        coalescer.noteSelectionChanged(selection)
        baselineCheckpoint = baselineCheckpoint.copy(ui = ui)
    }

    internal fun noteFocusChanged(checkpoint: EditorCheckpoint) {
        coalescer.noteBatchBreaker()
        baselineCheckpoint = checkpoint
    }

    internal fun onProgrammaticCommit(checkpoint: EditorCheckpoint) {
        syncToCheckpoint(checkpoint)
    }

    internal fun onUserTextCommit(after: EditorCheckpoint): PendingTextHistoryPush? {
        val entry = buildHistoryEntryFromCheckpoints(
            before = baselineCheckpoint,
            after = after,
        )
        baselineCheckpoint = after
        if (entry.isNoOp()) return null
        return PendingTextHistoryPush(
            entry = entry,
            policy = coalescer.policyFor(entry),
        )
    }
}

/**
 * Decides whether a committed text entry can continue the active typing batch.
 */
internal class TextEditCoalescer(
    private val nowMs: () -> Long,
) {
    private var lastMergeableEdit: MergeableTextEdit? = null
    private var lastMergeTimestampMs: Long = 0L
    private var batchBreakerPending: Boolean = false
    private var explicitPastePending: Boolean = false

    internal fun noteBatchBreaker() {
        batchBreakerPending = true
        lastMergeableEdit = null
        explicitPastePending = false
    }

    internal fun noteExplicitPaste() {
        explicitPastePending = true
        batchBreakerPending = true
        lastMergeableEdit = null
    }

    internal fun noteSelectionChanged(selection: TextRange) {
        if (!selection.collapsed) {
            noteBatchBreaker()
            return
        }

        // A collapsed selection only preserves the batch when the caret stayed
        // exactly where the previous mergeable edit predicted the next input.
        val expectedCaret = lastMergeableEdit?.expectedCaretBeforeNextEdit ?: return
        if (selection.start != expectedCaret || selection.end != expectedCaret) {
            noteBatchBreaker()
        }
    }

    /**
     * Resolves the stack merge policy for the freshly captured entry.
     *
     * Multi-character insertions are treated as paste-like by default unless a
     * future input path provides stronger origin metadata.
     */
    internal fun policyFor(entry: HistoryEntry): MergePolicy {
        val incoming = (entry as? BlockTextEntry)?.let(::analyzeMergeableTextEdit)
        val pasteLikeIncoming = incoming is InsertForwardEdit &&
            (explicitPastePending || incoming.insertedLength > 1)
        val mergeCandidate = incoming?.takeUnless { pasteLikeIncoming }
        val timestampMs = nowMs()

        val shouldTryMerge = mergeCandidate != null &&
            !batchBreakerPending &&
            lastMergeableEdit != null &&
            timestampMs - lastMergeTimestampMs <= TEXT_HISTORY_MERGE_WINDOW_MS &&
            canMergeTextEdits(
                previous = requireNotNull(lastMergeableEdit),
                incoming = mergeCandidate,
            )

        val policy = if (shouldTryMerge) {
            MergeAdjacentBlockTextEntriesPolicy
        } else {
            MergePolicy.Isolate
        }

        lastMergeableEdit = mergeCandidate
        lastMergeTimestampMs = if (mergeCandidate != null) timestampMs else 0L
        batchBreakerPending = false
        explicitPastePending = false
        return policy
    }
}

private sealed interface MergeableTextEdit {
    val blockId: BlockId
    val start: Int
    val expectedCaretBeforeNextEdit: Int
}

private data class InsertForwardEdit(
    override val blockId: BlockId,
    override val start: Int,
    val insertedLength: Int,
) : MergeableTextEdit {
    override val expectedCaretBeforeNextEdit: Int = start + insertedLength
}

private data class DeleteBackwardEdit(
    override val blockId: BlockId,
    override val start: Int,
    val deletedLength: Int,
) : MergeableTextEdit {
    override val expectedCaretBeforeNextEdit: Int = start
}

private data class DeleteForwardEdit(
    override val blockId: BlockId,
    override val start: Int,
    val deletedLength: Int,
) : MergeableTextEdit {
    override val expectedCaretBeforeNextEdit: Int = start
}

private data class TextChange(
    val start: Int,
    val deletedLength: Int,
    val insertedLength: Int,
)

/**
 * Combines two already-validated local text entries into one larger batch.
 *
 * The merged entry keeps the original `before` side and the latest `after` side,
 * which matches how the undo stack should represent continuous typing/deleting.
 */
private fun mergeAdjacentBlockTextEntries(
    previous: HistoryEntry,
    incoming: HistoryEntry,
): HistoryEntry? {
    val previousEntry = previous as? BlockTextEntry ?: return null
    val incomingEntry = incoming as? BlockTextEntry ?: return null
    if (previousEntry.after != incomingEntry.before) return null
    if (previousEntry.uiAfter != incomingEntry.uiBefore) return null

    return previousEntry.copy(
        after = incomingEntry.after,
        uiAfter = incomingEntry.uiAfter,
    )
}

private fun canMergeTextEdits(
    previous: MergeableTextEdit,
    incoming: MergeableTextEdit,
): Boolean {
    if (previous.blockId != incoming.blockId) return false

    return when {
        previous is InsertForwardEdit && incoming is InsertForwardEdit ->
            incoming.start == previous.start + previous.insertedLength

        previous is DeleteBackwardEdit && incoming is DeleteBackwardEdit ->
            incoming.start + incoming.deletedLength == previous.start

        previous is DeleteForwardEdit && incoming is DeleteForwardEdit ->
            incoming.start == previous.start

        else -> false
    }
}

private fun analyzeMergeableTextEdit(entry: BlockTextEntry): MergeableTextEdit? {
    if (entry.uiBefore.focusedBlockId != entry.blockId) return null
    if (entry.uiAfter.focusedBlockId != entry.blockId) return null

    val beforeSelection = entry.uiBefore.focusedTextSelection ?: return null
    val afterSelection = entry.uiAfter.focusedTextSelection ?: return null
    if (!beforeSelection.collapsed || !afterSelection.collapsed) return null

    val change = computeTextChange(entry.before.text, entry.after.text) ?: return null
    if (change.deletedLength > 0 && change.insertedLength > 0) return null

    val beforeCaret = beforeSelection.start
    val afterCaret = afterSelection.start

    // Formatting-only entries also arrive as BlockTextEntry, but they do not
    // match any of the caret-movement shapes below and therefore stay isolated.
    return when {
        change.insertedLength > 0 &&
            beforeCaret == change.start &&
            afterCaret == change.start + change.insertedLength ->
            InsertForwardEdit(
                blockId = entry.blockId,
                start = change.start,
                insertedLength = change.insertedLength,
            )

        change.deletedLength > 0 &&
            beforeCaret == change.start + change.deletedLength &&
            afterCaret == change.start ->
            DeleteBackwardEdit(
                blockId = entry.blockId,
                start = change.start,
                deletedLength = change.deletedLength,
            )

        change.deletedLength > 0 &&
            beforeCaret == change.start &&
            afterCaret == change.start ->
            DeleteForwardEdit(
                blockId = entry.blockId,
                start = change.start,
                deletedLength = change.deletedLength,
            )

        else -> null
    }
}

private fun computeTextChange(
    previous: String,
    current: String,
): TextChange? {
    if (previous == current) return null

    val minLength = minOf(previous.length, current.length)
    var prefixLength = 0
    while (prefixLength < minLength && previous[prefixLength] == current[prefixLength]) {
        prefixLength++
    }

    val maxSuffix = minOf(
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

    return TextChange(
        start = prefixLength,
        deletedLength = deletedLength,
        insertedLength = insertedLength,
    )
}
