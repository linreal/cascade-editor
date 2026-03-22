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

/**
 * Coordinates runtime [BlockSpanStates] updates with snapshot [EditorAction] dispatch
 * to keep both sides consistent when applying/removing/toggling styles.
 *
 * This is the recommended entry point for formatting operations (toolbar buttons,
 * keyboard shortcuts, programmatic style application). Using this dispatcher ensures:
 * - Immediate visual update via runtime [BlockSpanStates]
 * - Snapshot state sync via [UpdateBlockContent] dispatch (carries current runtime
 *   text + spans, avoiding stale-text-length mismatch)
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
        val visibleText = textStates.getVisibleText(blockId) ?: return
        spanStates.applyStyle(blockId, rangeStart, rangeEnd, style, visibleText.length)
        syncSnapshot(blockId, visibleText)
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
        val visibleText = textStates.getVisibleText(blockId) ?: return
        spanStates.removeStyle(blockId, rangeStart, rangeEnd, style)
        syncSnapshot(blockId, visibleText)
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
            return
        }

        val status = spanStates.queryStyleStatus(blockId, rangeStart, rangeEnd, style)
        when (status) {
            StyleStatus.FullyActive -> removeStyle(blockId, rangeStart, rangeEnd, style)
            else -> applyStyle(blockId, rangeStart, rangeEnd, style)
        }
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
