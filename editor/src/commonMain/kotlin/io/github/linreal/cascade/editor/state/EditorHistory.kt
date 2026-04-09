package io.github.linreal.cascade.editor.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.SpanStyle

/**
 * Focused editing context that must travel with a history entry.
 *
 * In v1 this is intentionally narrow: only the focused block, that block's
 * visible-text selection, and pending continuation styles are part of history.
 * Block selection highlight, slash UI state, and drag state are excluded.
 */
@Immutable
internal data class EditingUiState(
    val focusedBlockId: BlockId?,
    val focusedTextSelection: TextRange?,
    val focusedPendingStyles: Set<SpanStyle>,
)

/**
 * Canonical document snapshot used by structural history entries.
 *
 * [blocks] should already be resolved to the authoritative document state for
 * replay, rather than relying on live runtime holders.
 */
@Immutable
internal data class EditorCheckpoint(
    val blocks: List<Block>,
    val ui: EditingUiState,
)

/**
 * Internal history payload for undo/redo.
 *
 * Replay scope depends on the concrete subtype:
 * - [StructuralEntry] restores a full document checkpoint
 * - [BlockTextEntry] patches exactly one existing text block
 */
internal sealed interface HistoryEntry

/**
 * Full-document before/after snapshot pair for structural or multi-block edits.
 */
@Immutable
internal data class StructuralEntry(
    val before: EditorCheckpoint,
    val after: EditorCheckpoint,
) : HistoryEntry

/**
 * Compact before/after payload for edits confined to one existing text block.
 *
 * Validity of this entry shape is enforced by the caller-side predicate; this
 * type just carries the already-classified payload.
 */
@Immutable
internal data class BlockTextEntry(
    val blockId: BlockId,
    val before: BlockContent.Text,
    val after: BlockContent.Text,
    val uiBefore: EditingUiState,
    val uiAfter: EditingUiState,
) : HistoryEntry

internal fun interface HistoryEntryMerger {
    fun merge(previous: HistoryEntry, incoming: HistoryEntry): HistoryEntry?
}

/**
 * Stack-layer batching policy.
 *
 * This stays intentionally generic: the history manager knows how to isolate or
 * attempt a merge, but higher layers define when merging is semantically valid.
 */
internal sealed interface MergePolicy {
    data object Isolate : MergePolicy

    data class TryMerge(
        val merger: HistoryEntryMerger,
    ) : MergePolicy
}

internal enum class HistoryDirection {
    Undo,
    Redo,
}

/**
 * Linear undo/redo stack with depth trimming and redo invalidation.
 *
 * This class owns stack behavior only. It does not know how an entry is applied
 * to editor runtime state; callers provide the replay lambda for undo/redo.
 */
@Stable
internal class HistoryManager(
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
) {
    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()

    private var undoAvailable by mutableStateOf(false)
    private var redoAvailable by mutableStateOf(false)

    init {
        require(maxDepth > 0) { "maxDepth must be > 0, got $maxDepth" }
    }

    internal val canUndo: Boolean
        get() = undoAvailable

    internal val canRedo: Boolean
        get() = redoAvailable

    internal val undoDepth: Int
        get() = undoStack.size

    internal val redoDepth: Int
        get() = redoStack.size

    /**
     * Pushes a fresh user edit into history.
     *
     * No-op entries are dropped, redo is cleared on any accepted push, and the
     * undo side is trimmed to [maxDepth].
     */
    internal fun push(
        entry: HistoryEntry,
        policy: MergePolicy = MergePolicy.Isolate,
    ) {
        if (entry.isNoOp()) return

        when (policy) {
            MergePolicy.Isolate -> {
                undoStack.addLast(entry)
            }

            is MergePolicy.TryMerge -> {
                val previous = undoStack.lastOrNull()
                val merged = previous?.let { policy.merger.merge(it, entry) }

                when {
                    merged == null -> undoStack.addLast(entry)
                    merged.isNoOp() -> undoStack.removeLast()
                    else -> {
                        undoStack.removeLast()
                        undoStack.addLast(merged)
                    }
                }
            }
        }

        redoStack.clear()
        trimUndoStack()
        syncAvailability()
    }

    /**
     * Moves the latest undo entry to the redo stack after the caller replays it.
     */
    internal fun undo(apply: (HistoryEntry) -> Unit): Boolean {
        val entry = undoStack.lastOrNull() ?: return false
        undoStack.removeLast()
        redoStack.addLast(entry)
        try {
            apply(entry)
        } catch (t: Throwable) {
            redoStack.removeLast()
            undoStack.addLast(entry)
            syncAvailability()
            throw t
        }
        syncAvailability()
        return true
    }

    /**
     * Moves the latest redo entry back to the undo stack after replay.
     */
    internal fun redo(apply: (HistoryEntry) -> Unit): Boolean {
        val entry = redoStack.lastOrNull() ?: return false
        redoStack.removeLast()
        undoStack.addLast(entry)
        try {
            apply(entry)
        } catch (t: Throwable) {
            undoStack.removeLast()
            redoStack.addLast(entry)
            syncAvailability()
            throw t
        }
        syncAvailability()
        return true
    }

    /**
     * Clears both stacks. Used by hard document replacement paths.
     */
    internal fun clear() {
        if (!canUndo && !canRedo) return
        undoStack.clear()
        redoStack.clear()
        syncAvailability()
    }

    private fun trimUndoStack() {
        while (undoStack.size > maxDepth) {
            undoStack.removeFirst()
        }
    }

    private fun syncAvailability() {
        undoAvailable = undoStack.isNotEmpty()
        redoAvailable = redoStack.isNotEmpty()
    }

    private companion object {
        const val DEFAULT_MAX_DEPTH: Int = 100
    }
}

/**
 * Resolves the snapshot target for undo or redo without touching runtime state.
 */
internal fun HistoryEntry.resolveReplayTarget(
    currentBlocks: List<Block>,
    direction: HistoryDirection,
): Pair<List<Block>, EditingUiState> {
    return when (this) {
        is StructuralEntry -> {
            val checkpoint = if (direction == HistoryDirection.Undo) before else after
            checkpoint.blocks to checkpoint.ui
        }

        is BlockTextEntry -> {
            val content = if (direction == HistoryDirection.Undo) before else after
            val ui = if (direction == HistoryDirection.Undo) uiBefore else uiAfter
            val updatedBlocks = currentBlocks.replaceTextBlockContent(blockId, content)
            updatedBlocks to ui
        }
    }
}

/**
 * Replaces one block's text content while preserving referential identity for
 * every untouched block in the list.
 */
internal fun List<Block>.replaceTextBlockContent(
    blockId: BlockId,
    content: BlockContent.Text,
): List<Block> {
    var replaced = false
    val updated = map { block ->
        if (block.id != blockId) return@map block
        val existingContent = block.content as? BlockContent.Text
            ?: error("History replay requires existing text block $blockId")
        replaced = true
        if (existingContent == content) block else block.withContent(content)
    }
    require(replaced) { "History replay requires existing text block $blockId" }
    return updated
}

/**
 * Filters out entries that would not change document content if replayed.
 *
 * UI-only deltas are intentionally ignored here because v1 history capture is
 * defined around document mutations rather than focus-only state changes.
 */
internal fun HistoryEntry.isNoOp(): Boolean {
    return when (this) {
        is StructuralEntry -> before.blocks == after.blocks
        is BlockTextEntry -> before == after
    }
}
