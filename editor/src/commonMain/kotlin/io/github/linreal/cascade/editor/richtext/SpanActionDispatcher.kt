package io.github.linreal.cascade.editor.richtext

import androidx.compose.runtime.Stable
import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.UpdateBlockContent
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.SpanStyle.Companion.kindMatches
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.buildHistoryEntryFromCheckpoints
import io.github.linreal.cascade.editor.state.captureCheckpoint

/**
 * Coordinates runtime [BlockSpanStates] updates with snapshot [EditorAction] dispatch
 * to keep both sides consistent when applying/removing/toggling styles.
 *
 * This is the recommended entry point for formatting operations (toolbar buttons,
 * keyboard shortcuts, programmatic style application). Using this dispatcher ensures:
 * - Immediate visual update via runtime [BlockSpanStates]
 * - Snapshot state sync via [UpdateBlockContent] dispatch (carries current runtime
 *   text + spans, avoiding stale-text-length mismatch)
 * - Optional history capture when constructed with [stateHolder]
 *
 * @param dispatchFn Function to dispatch [EditorAction]s to the state holder
 * @param textStates Text state manager for visible text length resolution
 * @param spanStates Runtime span state manager
 */
@Stable
public class SpanActionDispatcher(
    private val dispatchFn: (EditorAction) -> Unit,
    private val textStates: BlockTextStates,
    private val spanStates: BlockSpanStates,
    private val stateHolder: EditorStateHolder? = null,
) {

    /**
     * Applies [style] to `[rangeStart, rangeEnd)` in the specified block.
     * Updates runtime spans immediately, then syncs full block content to snapshot.
     *
     * No-op if the block has no runtime text state.
     */
    public fun applyStyle(
        blockId: BlockId,
        rangeStart: Int,
        rangeEnd: Int,
        style: SpanStyle,
    ) {
        mutateFormatting(
            blockId = blockId,
            captureHistory = rangeStart != rangeEnd,
        ) { visibleText ->
            spanStates.applyStyle(blockId, rangeStart, rangeEnd, style, visibleText.length)
            syncSnapshot(blockId, visibleText)
        }
    }

    /**
     * Removes [style] from `[rangeStart, rangeEnd)` in the specified block.
     * Updates runtime spans immediately, then syncs full block content to snapshot.
     *
     * No-op if the block has no runtime text state.
     */
    public fun removeStyle(
        blockId: BlockId,
        rangeStart: Int,
        rangeEnd: Int,
        style: SpanStyle,
    ) {
        mutateFormatting(
            blockId = blockId,
            captureHistory = rangeStart != rangeEnd,
        ) { visibleText ->
            spanStates.removeStyle(blockId, rangeStart, rangeEnd, style)
            syncSnapshot(blockId, visibleText)
        }
    }

    /**
     * Toggles [style] on `[rangeStart, rangeEnd)`.
     *
     * **Collapsed cursor** (`rangeStart == rangeEnd`): toggles pending style for the
     * next insertion. No snapshot dispatch — pending styles are runtime-only.
     *
     * **Non-collapsed range**: if the style is [StyleStatus.FullyActive], removes it;
     * otherwise applies it. Syncs snapshot after runtime update.
     *
     * No-op if the block has no runtime text state.
     */
    public fun toggleStyle(
        blockId: BlockId,
        rangeStart: Int,
        rangeEnd: Int,
        style: SpanStyle,
    ) {
        if (textStates.getVisibleText(blockId) == null) return

        // Collapsed cursor: toggle pending style for next insertion
        if (rangeStart == rangeEnd) {
            mutateFormatting(
                blockId = blockId,
                captureHistory = false,
            ) {
                val pending = spanStates.getPendingStyles(blockId) ?: run {
                    if (rangeStart <= 0) {
                        emptySet()
                    } else {
                        // Keep toggle semantics aligned with insertion continuation (`position - 1`).
                        spanStates.activeStylesAt(blockId, rangeStart - 1)
                    }
                }
                val existing = pending.firstOrNull { kindMatches(it, style) }
                if (existing != null) {
                    spanStates.setPendingStyles(blockId, pending - existing)
                } else {
                    spanStates.setPendingStyles(blockId, pending + style)
                }
            }
            return
        }

        val status = spanStates.queryStyleStatus(blockId, rangeStart, rangeEnd, style)
        when (status) {
            StyleStatus.FullyActive -> removeStyle(blockId, rangeStart, rangeEnd, style)
            else -> applyStyle(blockId, rangeStart, rangeEnd, style)
        }
    }

    /**
     * Shared mutation wrapper for formatting commands.
     *
     * It preserves the runtime-first behavior of the dispatcher, but when a
     * holder is available it also:
     * 1. breaks any in-flight typing batch for this block
     * 2. optionally captures a before/after history entry
     * 3. resynchronizes the block-local text tracker baseline afterward
     */
    private inline fun mutateFormatting(
        blockId: BlockId,
        captureHistory: Boolean,
        mutation: (visibleText: String) -> Unit,
    ) {
        val visibleText = textStates.getVisibleText(blockId) ?: return
        val beforeCheckpoint = if (captureHistory) {
            stateHolder?.captureCheckpoint(textStates, spanStates)
        } else {
            null
        }

        stateHolder?.breakTextHistoryBatch(blockId)
        mutation(visibleText)

        val holder = stateHolder ?: return
        val afterCheckpoint = holder.captureCheckpoint(textStates, spanStates)
        if (beforeCheckpoint != null) {
            holder.pushHistoryEntry(
                buildHistoryEntryFromCheckpoints(
                    before = beforeCheckpoint,
                    after = afterCheckpoint,
                )
            )
        }
        holder.syncTextHistoryTracker(blockId, afterCheckpoint)
    }

    /**
     * Pushes current runtime text + spans to the snapshot via [UpdateBlockContent].
     * This avoids stale-text-length mismatch that would occur if using
     * [ApplySpanStyle]/[RemoveSpanStyle] directly against the snapshot.
     */
    private fun syncSnapshot(blockId: BlockId, visibleText: String) {
        val spans = spanStates.getSpans(blockId)
        dispatchFn(UpdateBlockContent(blockId, BlockContent.Text(visibleText, spans)))
    }
}
