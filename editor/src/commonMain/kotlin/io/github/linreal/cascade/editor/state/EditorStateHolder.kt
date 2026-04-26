package io.github.linreal.cascade.editor.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId

/**
 * Compose-friendly mutable holder for editor state.
 *
 * Provides a stable reference that can be passed down the composition tree
 * while the underlying state changes trigger recomposition.
 *
 * Uses unidirectional data flow: state changes only through dispatched actions.
 */
@Stable
public class EditorStateHolder(initialState: EditorState = EditorState.Empty) {
    private var _state by mutableStateOf(initialState.ensureTrailingTextBlock())
    private val history = HistoryManager()
    private var historyTextStates: BlockTextStates? = null
    private var historySpanStates: BlockSpanStates? = null
    private var structuralHistoryTransactionDepth: Int = 0
    // Active per-block text trackers owned by rendered TextBlockField instances.
    // External commands use this registry to break/resync typing batches.
    // This registry is intentionally plain mutable state because all access is
    // currently constrained to the synchronous Compose/UI thread.
    private val textHistoryTrackers = mutableMapOf<BlockId, MutableSet<TextHistoryTrackerSink>>()

    /**
     * Main-thread replay guard for synchronous undo/redo application in v1.
     *
     * This is intentionally a plain boolean rather than snapshot/atomic state
     * because replay currently happens synchronously on the Compose/UI thread.
     */
    internal var isApplyingHistory: Boolean = false
        private set

    /**
     * The current immutable state snapshot.
     */
    public val state: EditorState get() = _state

    /**
     * Returns `true` when there is at least one undo step available.
     */
    public val canUndo: Boolean
        get() = history.canUndo

    /**
     * Returns `true` when there is at least one redo step available.
     */
    public val canRedo: Boolean
        get() = history.canRedo

    internal val boundHistoryTextStates: BlockTextStates?
        get() = historyTextStates

    internal val boundHistorySpanStates: BlockSpanStates?
        get() = historySpanStates

    /**
     * Dispatches an action to update the state.
     *
     * Direct external dispatch calls bypass history capture in v1. Built-in editor
     * flows must route through explicit history-aware integration points.
     */
    public fun dispatch(action: EditorAction) {
        _state = action.reduce(_state).ensureTrailingTextBlock()
        cleanupStaleTextHistoryTrackers(_state.blocks)
    }

    /**
     * Replaces the entire state. Use with caution.
     * Prefer dispatching actions for state changes.
     *
     * This is treated as a hard document replacement and clears undo/redo history.
     */
    public fun setState(newState: EditorState) {
        replaceState(newState, clearHistory = true)
    }

    /**
     * Applies the previous history entry if one exists.
     *
     * Re-entrant calls during active replay are ignored.
     */
    public fun undo() {
        if (isApplyingHistory) return
        history.undo { entry ->
            applyHistoryEntry(entry, HistoryDirection.Undo)
        }
    }

    /**
     * Re-applies the next history entry if one exists.
     *
     * Re-entrant calls during active replay are ignored.
     */
    public fun redo() {
        if (isApplyingHistory) return
        history.redo { entry ->
            applyHistoryEntry(entry, HistoryDirection.Redo)
        }
    }

    internal fun pushHistoryEntry(
        entry: HistoryEntry,
        policy: MergePolicy = MergePolicy.Isolate,
    ) {
        if (isApplyingHistory) return
        history.push(entry, policy)
    }

    internal fun clearHistory() {
        history.clear()
    }

    internal fun bindHistoryRuntime(
        textStates: BlockTextStates,
        spanStates: BlockSpanStates,
    ) {
        historyTextStates = textStates
        historySpanStates = spanStates
    }

    internal fun unbindHistoryRuntime(
        textStates: BlockTextStates,
        spanStates: BlockSpanStates,
    ) {
        if (historyTextStates === textStates && historySpanStates === spanStates) {
            historyTextStates = null
            historySpanStates = null
        }
    }

    /**
     * Registers a live text-history tracker for [blockId].
     *
     * Multiple trackers are allowed defensively, although the normal editor flow
     * has at most one rendered `TextBlockField` per block.
     */
    internal fun registerTextHistoryTracker(
        blockId: BlockId,
        tracker: TextHistoryTrackerSink,
    ) {
        textHistoryTrackers.getOrPut(blockId) { linkedSetOf() }.add(tracker)
    }

    /**
     * Removes a tracker previously registered for [blockId].
     */
    internal fun unregisterTextHistoryTracker(
        blockId: BlockId,
        tracker: TextHistoryTrackerSink,
    ) {
        val trackers = textHistoryTrackers[blockId] ?: return
        trackers.remove(tracker)
        if (trackers.isEmpty()) {
            textHistoryTrackers.remove(blockId)
        }
    }

    /**
     * Forces the live typing batch for [blockId] to end.
     *
     * Used by commands such as formatting so they never merge into surrounding
     * typing even when they do not pass back through `TextBlockField`.
     */
    internal fun breakTextHistoryBatch(blockId: BlockId) {
        textHistoryTrackers[blockId]?.toList()?.forEach { tracker ->
            tracker.noteBatchBreaker()
        }
    }

    /**
     * Re-anchors the live typing tracker for [blockId] to [checkpoint].
     *
     * This keeps future text capture aligned after commands that mutate the
     * block's spans or pending styles outside the text observer path.
     */
    internal fun syncTextHistoryTracker(
        blockId: BlockId,
        checkpoint: EditorCheckpoint,
    ) {
        textHistoryTrackers[blockId]?.toList()?.forEach { tracker ->
            tracker.syncToCheckpoint(checkpoint)
        }
    }

    /**
     * Fast replay-only tracker sync for one-block text entries.
     *
     * The replay path already knows the exact restored block payload, so it can
     * re-anchor the live tracker without capturing another full checkpoint.
     */
    internal fun syncTextHistoryTracker(
        blockId: BlockId,
        content: BlockContent.Text,
        ui: EditingUiState,
    ) {
        textHistoryTrackers[blockId]?.toList()?.forEach { tracker ->
            tracker.syncToBlockContent(blockId, content, ui)
        }
    }

    /**
     * Captures one forced [StructuralEntry] around [mutation].
     *
     * This is the v1 boundary for semantic document edits such as split/merge,
     * slash commands, list conversion, drag reorder, and todo toggles. Even if
     * the before/after delta would fit in a [BlockTextEntry], these paths stay
     * structural so each semantic command replays through the full-document
     * checkpoint pipeline.
     *
     * Open typing batches are broken before capture starts, and all surviving
     * trackers are re-anchored to the post-transaction checkpoint afterward so
     * later typing compares against the new structural baseline.
     */
    internal fun runStructuralHistoryTransaction(
        textStates: BlockTextStates? = boundHistoryTextStates,
        spanStates: BlockSpanStates? = boundHistorySpanStates,
        mutation: () -> Unit,
    ) {
        if (isApplyingHistory || structuralHistoryTransactionDepth > 0) {
            mutation()
            return
        }

        val effectiveTextStates = textStates ?: boundHistoryTextStates
        val effectiveSpanStates = spanStates ?: boundHistorySpanStates
        if (effectiveTextStates == null || effectiveSpanStates == null) {
            mutation()
            return
        }

        structuralHistoryTransactionDepth++
        try {
            breakAllTextHistoryBatches()
            val beforeCheckpoint = captureCheckpoint(effectiveTextStates, effectiveSpanStates)
            mutation()
            val afterCheckpoint = captureCheckpoint(effectiveTextStates, effectiveSpanStates)
            pushHistoryEntry(
                StructuralEntry(
                    before = beforeCheckpoint,
                    after = afterCheckpoint,
                )
            )
            syncAllTextHistoryTrackers(afterCheckpoint)
        } finally {
            structuralHistoryTransactionDepth--
        }
    }

    /**
     * Suspend-capable variant of [runStructuralHistoryTransaction].
     *
     * This exists for editor-owned command pipelines, such as slash-command
     * execution, whose public API is suspending even though they still need one
     * structural history entry around their final document mutation sequence.
     *
     * **Important:** [mutation] must not suspend between document-mutating calls.
     * The before checkpoint is captured before [mutation] runs, the after checkpoint
     * immediately after it returns. If the lambda yields to other coroutines that
     * modify editor state in between, the captured before/after pair will be
     * inconsistent. The suspend signature exists only so the outer call site can
     * be a suspend function; the document mutations inside should run synchronously
     * on the main thread without intermediate suspension points.
     */
    internal suspend fun runStructuralHistoryTransactionSuspend(
        textStates: BlockTextStates? = boundHistoryTextStates,
        spanStates: BlockSpanStates? = boundHistorySpanStates,
        mutation: suspend () -> Unit,
    ) {
        if (isApplyingHistory || structuralHistoryTransactionDepth > 0) {
            mutation()
            return
        }

        val effectiveTextStates = textStates ?: boundHistoryTextStates
        val effectiveSpanStates = spanStates ?: boundHistorySpanStates
        if (effectiveTextStates == null || effectiveSpanStates == null) {
            mutation()
            return
        }

        structuralHistoryTransactionDepth++
        try {
            breakAllTextHistoryBatches()
            val beforeCheckpoint = captureCheckpoint(effectiveTextStates, effectiveSpanStates)
            mutation()
            val afterCheckpoint = captureCheckpoint(effectiveTextStates, effectiveSpanStates)
            pushHistoryEntry(
                StructuralEntry(
                    before = beforeCheckpoint,
                    after = afterCheckpoint,
                )
            )
            syncAllTextHistoryTrackers(afterCheckpoint)
        } finally {
            structuralHistoryTransactionDepth--
        }
    }

    /**
     * Convenience wrapper for single-action structural edit sources.
     */
    internal fun dispatchStructuralAction(
        action: EditorAction,
        textStates: BlockTextStates? = boundHistoryTextStates,
        spanStates: BlockSpanStates? = boundHistorySpanStates,
    ) {
        runStructuralHistoryTransaction(textStates, spanStates) {
            dispatch(action)
        }
    }

    private fun applyHistoryEntry(
        entry: HistoryEntry,
        direction: HistoryDirection,
    ) {
        withHistoryReplay {
            when (entry) {
                is StructuralEntry -> {
                    val checkpoint = if (direction == HistoryDirection.Undo) entry.before else entry.after
                    val textStates = boundHistoryTextStates
                    val spanStates = boundHistorySpanStates

                    if (textStates != null && spanStates != null) {
                        applyCheckpoint(checkpoint, textStates, spanStates)
                        syncAllTextHistoryTrackers(checkpoint)
                    } else {
                        replaceStateForReplay(checkpoint.toReplayState())
                    }
                }

                is BlockTextEntry -> {
                    val content = if (direction == HistoryDirection.Undo) entry.before else entry.after
                    val ui = if (direction == HistoryDirection.Undo) entry.uiBefore else entry.uiAfter
                    val textStates = boundHistoryTextStates
                    val spanStates = boundHistorySpanStates

                    if (textStates != null && spanStates != null) {
                        applyBlockTextEntry(
                            blockId = entry.blockId,
                            content = content,
                            ui = ui,
                            textStates = textStates,
                            spanStates = spanStates,
                        )
                        syncTextHistoryTracker(
                            blockId = entry.blockId,
                            content = content,
                            ui = ui,
                        )
                    } else {
                        val (blocks, resolvedUi) = entry.resolveReplayTarget(_state.blocks, direction)
                        replaceStateForReplay(
                            EditorState.withBlocks(blocks).copy(
                                focusedBlockId = resolvedUi.focusedBlockId,
                                selectedBlockIds = resolvedUi.replaySelectedBlockIds(blocks),
                            )
                        )
                    }
                }
            }
        }
    }

    internal inline fun withHistoryReplay(block: () -> Unit) {
        isApplyingHistory = true
        try {
            block()
        } finally {
            isApplyingHistory = false
        }
    }

    internal fun replaceStateForReplay(newState: EditorState) {
        replaceState(newState, clearHistory = false)
    }

    private fun replaceState(
        newState: EditorState,
        clearHistory: Boolean,
    ) {
        _state = newState.ensureTrailingTextBlock()
        cleanupStaleTextHistoryTrackers(_state.blocks)
        if (clearHistory) {
            history.clear()
        }
    }

    /**
     * Tracker disposal is composition-driven, so document mutations can briefly
     * outpace `DisposableEffect` cleanup. Prune deleted blocks eagerly to keep
     * external batch-break/sync broadcasts targeted at live blocks only.
     */
    private fun cleanupStaleTextHistoryTrackers(blocks: List<Block>) {
        if (textHistoryTrackers.isEmpty()) return
        val existingBlockIds = blocks.asSequence().map { it.id }.toSet()
        textHistoryTrackers.keys.retainAll(existingBlockIds)
    }

    private fun breakAllTextHistoryBatches() {
        uniqueTextHistoryTrackers().forEach(TextHistoryTrackerSink::noteBatchBreaker)
    }

    private fun syncAllTextHistoryTrackers(checkpoint: EditorCheckpoint) {
        cleanupStaleTextHistoryTrackers(_state.blocks)
        uniqueTextHistoryTrackers().forEach { tracker ->
            tracker.syncToCheckpoint(checkpoint)
        }
    }

    private fun uniqueTextHistoryTrackers(): List<TextHistoryTrackerSink> {
        return textHistoryTrackers.values
            .asSequence()
            .flatMap { it.asSequence() }
            .distinct()
            .toList()
    }
}

/**
 * Creates and remembers an [EditorStateHolder] with the given initial blocks.
 */
@Composable
public fun rememberEditorState(initialBlocks: List<Block> = emptyList()): EditorStateHolder {
    return remember {
        EditorStateHolder(EditorState.withBlocks(initialBlocks))
    }
}

/**
 * Creates and remembers an [EditorStateHolder] with the given initial state.
 */
@Composable
public fun rememberEditorState(initialState: EditorState): EditorStateHolder {
    return remember {
        EditorStateHolder(initialState)
    }
}
