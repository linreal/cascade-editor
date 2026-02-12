package io.github.linreal.cascade.editor.state

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.SpanAlgorithms
import io.github.linreal.cascade.editor.richtext.StyleStatus

/**
 * Manages per-block span state for rich text formatting.
 *
 * Parallels [BlockTextStates] (which manages [TextFieldState] instances)
 * but for [TextSpan] lists. Uses [MutableState] wrappers for Compose
 * snapshot reactivity — composable readers (e.g. OutputTransformation)
 * recompose when spans change.
 *
 * All coordinates are visible-text coordinates (half-open `[start, end)` intervals).
 */
@Stable
public class BlockSpanStates {

    private val states = mutableMapOf<BlockId, MutableState<List<TextSpan>>>()
    // Snapshot-aware map so pending style changes can drive Compose recomposition.
    private val pendingStyles = mutableStateMapOf<BlockId, Set<SpanStyle>>()

    // ── Lifecycle ────────────────────────────────────────────────────────

    /**
     * Gets existing span state or creates new one with initial spans.
     *
     * @param blockId The block identifier
     * @param initialSpans Initial spans (defaults to empty)
     * @param textLength Current visible text length for this block.
     *        Required to normalize/clamp incoming spans.
     * @return The observable [State] for this block's spans
     */
    public fun getOrCreate(
        blockId: BlockId,
        initialSpans: List<TextSpan> = emptyList(),
        textLength: Int,
    ): State<List<TextSpan>> {
        require(textLength >= 0) { "textLength must be non-negative, got $textLength" }
        return states.getOrPut(blockId) {
            mutableStateOf(SpanAlgorithms.normalize(initialSpans.toList(), textLength))
        }
    }

    /**
     * Gets span state if it exists.
     */
    public fun get(blockId: BlockId): State<List<TextSpan>>? = states[blockId]

    /**
     * Gets the current span list for a block, or empty list if absent.
     */
    public fun getSpans(blockId: BlockId): List<TextSpan> {
        return states[blockId]?.value ?: emptyList()
    }

    /**
     * Sets the span list for an existing block.
     * No-op if the block has no state entry.
     *
     * @param textLength Current visible text length for this block.
     *        Required to normalize/clamp incoming spans.
     */
    public fun set(
        blockId: BlockId,
        spans: List<TextSpan>,
        textLength: Int,
    ) {
        require(textLength >= 0) { "textLength must be non-negative, got $textLength" }
        states[blockId]?.value = SpanAlgorithms.normalize(spans.toList(), textLength)
    }

    /**
     * Removes span state for a deleted block.
     */
    public fun remove(blockId: BlockId) {
        states.remove(blockId)
        pendingStyles.remove(blockId)
    }

    /**
     * Cleans up states for blocks that no longer exist.
     * Call this when blocks are removed from EditorState.
     */
    public fun cleanup(existingBlockIds: Set<BlockId>) {
        val toRemove = states.keys - existingBlockIds
        toRemove.forEach { id ->
            states.remove(id)
        }
        val pendingToRemove = pendingStyles.keys - existingBlockIds
        pendingToRemove.forEach { id -> pendingStyles.remove(id) }
    }

    /**
     * Clears all states. Useful for editor reset.
     */
    public fun clear() {
        states.clear()
        pendingStyles.clear()
    }

    // ── Edit Adjustment ──────────────────────────────────────────────────

    /**
     * Adjusts span coordinates after a user text edit.
     * Delegates to [SpanAlgorithms.adjustForEdit].
     * No-op if the block has no state entry.
     */
    public fun adjustForUserEdit(
        blockId: BlockId,
        editStart: Int,
        deletedLength: Int,
        insertedLength: Int,
    ) {
        val state = states[blockId] ?: return
        state.value = SpanAlgorithms.adjustForEdit(
            spans = state.value,
            editStart = editStart,
            deletedLength = deletedLength,
            insertedLength = insertedLength,
        )
    }

    // ── Transfer ─────────────────────────────────────────────────────────

    /**
     * Splits spans at [position] for a block split operation.
     *
     * Spans before [position] remain on [sourceBlockId].
     * Spans at or after [position] (shifted to 0-based) are placed on [newBlockId].
     * Crossing spans are clipped into both.
     * Pending styles on the source block are cleared.
     *
     * No-op if the source block has no state entry.
     */
    public fun split(
        sourceBlockId: BlockId,
        newBlockId: BlockId,
        position: Int,
    ) {
        val sourceState = states[sourceBlockId] ?: return
        val (first, second) = SpanAlgorithms.splitAt(sourceState.value, position)
        sourceState.value = first
        val targetState = states.getOrPut(newBlockId) { mutableStateOf(emptyList()) }
        targetState.value = second
        pendingStyles.remove(sourceBlockId)
        pendingStyles.remove(newBlockId)
    }

    /**
     * Merges spans from [sourceId] into [targetId] after a text merge.
     *
     * Source spans are shifted by [targetTextLength] and merged with target spans.
     * Adjacent/overlapping same-style spans at the boundary are consolidated.
     * Source state is removed after merge.
     *
     * If target has no state, it is created with the shifted source spans.
     * No-op if source has no state entry.
     */
    public fun mergeInto(
        sourceId: BlockId,
        targetId: BlockId,
        targetTextLength: Int,
    ) {
        val sourceState = states[sourceId] ?: return
        val targetState = states.getOrPut(targetId) { mutableStateOf(emptyList()) }
        targetState.value = SpanAlgorithms.mergeSpans(
            firstSpans = targetState.value,
            secondSpans = sourceState.value,
            firstTextLength = targetTextLength,
        )
        states.remove(sourceId)
        pendingStyles.remove(sourceId)
    }

    // ── Style Operations ─────────────────────────────────────────────────

    /**
     * Applies [style] to the range `[rangeStart, rangeEnd)` in the given block.
     * No-op if the block has no state entry.
     */
    public fun applyStyle(
        blockId: BlockId,
        rangeStart: Int,
        rangeEnd: Int,
        style: SpanStyle,
        textLength: Int,
    ) {
        val state = states[blockId] ?: return
        state.value = SpanAlgorithms.applyStyle(
            spans = state.value,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            style = style,
            textLength = textLength,
        )
    }

    /**
     * Removes [style] from the range `[rangeStart, rangeEnd)` in the given block.
     * No-op if the block has no state entry.
     */
    public fun removeStyle(
        blockId: BlockId,
        rangeStart: Int,
        rangeEnd: Int,
        style: SpanStyle,
    ) {
        val state = states[blockId] ?: return
        state.value = SpanAlgorithms.removeStyle(
            spans = state.value,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            style = style,
        )
    }

    /**
     * Toggles [style] on the range `[rangeStart, rangeEnd)` in the given block.
     * If fully active, removes; otherwise applies.
     * No-op if the block has no state entry.
     */
    public fun toggleStyle(
        blockId: BlockId,
        rangeStart: Int,
        rangeEnd: Int,
        style: SpanStyle,
        textLength: Int,
    ) {
        val state = states[blockId] ?: return
        state.value = SpanAlgorithms.toggleStyle(
            spans = state.value,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            style = style,
            textLength = textLength,
        )
    }

    // ── Queries ──────────────────────────────────────────────────────────

    /**
     * Queries the presence of [style] in the range `[rangeStart, rangeEnd)`.
     * Returns [StyleStatus.Absent] if the block has no state entry.
     */
    public fun queryStyleStatus(
        blockId: BlockId,
        rangeStart: Int,
        rangeEnd: Int,
        style: SpanStyle,
    ): StyleStatus {
        val state = states[blockId] ?: return StyleStatus.Absent
        return SpanAlgorithms.queryStyleStatus(
            spans = state.value,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            style = style,
        )
    }

    /**
     * Returns all styles active at a collapsed cursor [position].
     * Returns empty set if the block has no state entry.
     */
    public fun activeStylesAt(
        blockId: BlockId,
        position: Int,
    ): Set<SpanStyle> {
        val state = states[blockId] ?: return emptySet()
        return SpanAlgorithms.activeStylesAt(
            spans = state.value,
            position = position,
        )
    }

    // ── Pending Styles ───────────────────────────────────────────────────

    /**
     * Gets pending continuation styles for a block.
     * Returns null if no pending styles are set.
     */
    public fun getPendingStyles(blockId: BlockId): Set<SpanStyle>? {
        return pendingStyles[blockId]?.toSet()
    }

    /**
     * Sets pending continuation styles for a block.
     * These styles will be applied to newly typed characters.
     */
    public fun setPendingStyles(blockId: BlockId, styles: Set<SpanStyle>) {
        pendingStyles[blockId] = styles.toSet()
    }

    /**
     * Clears pending continuation styles for a block.
     */
    public fun clearPendingStyles(blockId: BlockId) {
        pendingStyles.remove(blockId)
    }

    /**
     * Resolves the effective styles for an insertion at [position].
     *
     * If pending styles are set, uses those (and clears them).
     * Otherwise falls back to inheriting styles from `position - 1`
     * (common editor UX: typing at the end of a bold range continues bold).
     * Position 0 with no pending styles returns empty set.
     */
    public fun resolveStylesForInsertion(
        blockId: BlockId,
        position: Int,
    ): Set<SpanStyle> {
        val pending = pendingStyles.remove(blockId)
        if (pending != null) return pending.toSet()

        if (position <= 0) return emptySet()
        return activeStylesAt(blockId, position - 1)
    }
}
