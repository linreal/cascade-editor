package io.github.linreal.cascade.editor.richtext

import androidx.compose.runtime.Stable
import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.UpdateBlockContent
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.buildHistoryEntryFromCheckpoints
import io.github.linreal.cascade.editor.state.captureCheckpoint

/**
 * Coordinates runtime text/span updates with snapshot dispatch for link mutations.
 *
 * This dispatcher is link-specific because links need URL validation, optional
 * title replacement, captured-target clamping, and URL-agnostic remove behavior
 * that generic formatting actions do not model.
 */
@Stable
public class LinkActionDispatcher(
    private val dispatchFn: (EditorAction) -> Unit,
    private val textStates: BlockTextStates,
    private val spanStates: BlockSpanStates,
    private val stateHolder: EditorStateHolder? = null,
) : LinkActions {
    /**
     * Validates [url] and applies the normalized link to [target].
     *
     * The latest runtime visible text and spans are resolved at invocation time,
     * while [target] remains the captured range from the popup/session opener.
     * A valid result reports URL validation success, not guaranteed mutation:
     * stale or missing targets can still no-op safely.
     */
    override fun applyLink(
        target: LinkTarget,
        url: String,
        title: String?,
    ): LinkValidationResult {
        val validation = LinkUrlPolicy.validate(url)
        val normalizedUrl = validation.normalizedUrl ?: return validation

        // Defense-in-depth gate for non-spans target block types (e.g. Code).
        // Existing chrome-level gating happens through DefaultLinkActions/canLink, but
        // direct dispatcher callers may hold a stale LinkTarget. URL validation has
        // already run and is reported back to the caller; we just refuse to mutate.
        if (targetBlockBlocksSpanMutation(target.blockId)) return validation

        mutateLink(target.blockId) { visibleText ->
            val resolvedTarget = target.resolveAgainst(visibleText.length)
            val titleReplacement = title?.takeIf { it.isNotBlank() }
            val replacement = titleReplacement ?: if (target.isCollapsed) normalizedUrl else null

            if (!target.isCollapsed && resolvedTarget.isCollapsed && replacement == null) {
                return@mutateLink false
            }

            val originalText = visibleText
            val originalSpans = spanStates.getSpans(target.blockId)
            var currentText = visibleText
            val linkStart = resolvedTarget.start
            val linkEnd = if (replacement != null) {
                val currentTargetText = visibleText.substring(resolvedTarget.start, resolvedTarget.end)
                if (replacement != currentTargetText) {
                    currentText = textStates.replaceVisibleRange(
                        blockId = target.blockId,
                        start = resolvedTarget.start,
                        endExclusive = resolvedTarget.end,
                        replacement = replacement,
                    ) ?: return@mutateLink false
                    spanStates.adjustForRangeReplacement(
                        blockId = target.blockId,
                        start = resolvedTarget.start,
                        endExclusive = resolvedTarget.end,
                        replacementLength = replacement.length,
                    )
                }
                resolvedTarget.start + replacement.length
            } else {
                resolvedTarget.end
            }

            if (linkStart >= linkEnd) return@mutateLink false

            spanStates.applyStyle(
                blockId = target.blockId,
                rangeStart = linkStart,
                rangeEnd = linkEnd,
                style = SpanStyle.Link(normalizedUrl),
                textLength = currentText.length,
            )

            // Apply-and-compare (rather than pre-check) keeps the apply path simple
            // and lets BlockSpanStates own normalization. If the resulting text and
            // span list are structurally identical to the originals, the runtime
            // state has not changed in any user-visible way: skip snapshot/history
            // sync to avoid an empty undo entry.
            if (currentText == originalText && spanStates.getSpans(target.blockId) == originalSpans) {
                return@mutateLink false
            }

            syncSnapshot(target.blockId, currentText)
            true
        }

        return validation
    }

    /**
     * Removes link coverage from [target] after clamping it to current runtime text.
     */
    override fun removeLink(target: LinkTarget) {
        if (targetBlockBlocksSpanMutation(target.blockId)) return
        mutateLink(target.blockId) { visibleText ->
            val resolvedTarget = target.resolveAgainst(visibleText.length)
            if (resolvedTarget.isCollapsed) return@mutateLink false

            val beforeSpans = spanStates.getSpans(target.blockId)
            spanStates.removeLinkSpans(
                blockId = target.blockId,
                rangeStart = resolvedTarget.start,
                rangeEnd = resolvedTarget.end,
            )
            if (spanStates.getSpans(target.blockId) == beforeSpans) return@mutateLink false

            syncSnapshot(target.blockId, visibleText)
            true
        }
    }

    /**
     * Runs a runtime-first link mutation and captures one isolated history entry
     * when [stateHolder] is available.
     */
    private inline fun mutateLink(
        blockId: BlockId,
        mutation: (visibleText: String) -> Boolean,
    ) {
        if (spanStates.get(blockId) == null) return
        val visibleText = textStates.getVisibleText(blockId) ?: return
        val beforeCheckpoint = stateHolder?.captureCheckpoint(textStates, spanStates)

        val didMutate = mutation(visibleText)
        if (!didMutate) return

        val holder = stateHolder ?: return
        val before = beforeCheckpoint ?: return
        holder.breakTextHistoryBatch(blockId)
        val afterCheckpoint = holder.captureCheckpoint(textStates, spanStates)
        holder.pushHistoryEntry(
            buildHistoryEntryFromCheckpoints(
                before = before,
                after = afterCheckpoint,
            )
        )
        holder.syncTextHistoryTracker(blockId, afterCheckpoint)
    }

    /**
     * True only when we can prove the target block's current type opts out of spans.
     *
     * When the state holder is unavailable (e.g. unit tests that exercise the dispatcher
     * directly), or the block is no longer in the snapshot, we conservatively allow the
     * mutation — `mutateLink` already no-ops for missing runtime state.
     */
    private fun targetBlockBlocksSpanMutation(blockId: BlockId): Boolean {
        val type = stateHolder?.state?.getBlock(blockId)?.type ?: return false
        return !type.supportsSpans
    }

    /**
     * Pushes current runtime text + spans to the immutable snapshot.
     */
    private fun syncSnapshot(blockId: BlockId, visibleText: String) {
        dispatchFn(
            UpdateBlockContent(
                blockId = blockId,
                content = BlockContent.Text(
                    text = visibleText,
                    spans = spanStates.getSpans(blockId),
                ),
            )
        )
    }

    private data class ResolvedTarget(
        val start: Int,
        val end: Int,
    ) {
        val isCollapsed: Boolean
            get() = start == end
    }

    private fun LinkTarget.resolveAgainst(textLength: Int): ResolvedTarget {
        return ResolvedTarget(
            start = normalizedStart.coerceIn(0, textLength),
            end = normalizedEnd.coerceIn(0, textLength),
        )
    }
}
