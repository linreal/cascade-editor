package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.style.TextOverflow
import io.github.linreal.cascade.editor.CascadeErrorReporter
import io.github.linreal.cascade.editor.CrashPolicy

private const val DEFAULT_MAX_BLOCKS: Int = 4
private const val DEFAULT_MAX_LINES_PER_TEXT_BLOCK: Int = 3

/**
 * Presentation policy for a stateless, non-scrollable Cascade document preview.
 *
 * A preview renders immutable block snapshots directly. It never mutates or
 * normalizes its input, appends an editor scaffold block, or provides an
 * authorization boundary. Use editor read-only mode when focus, text-field
 * selection, or a later transition back to editing is required.
 *
 * [maxBlocks] and [maxLinesPerTextBlock] are positive bounds. `null` means that
 * dimension is unbounded; prefer the named [Unbounded] preset when both bounds
 * should be removed. An unbounded preview still does not scroll and eagerly
 * composes every supplied block, so it should not be used for grid cards or
 * large documents.
 *
 * @param maxBlocks Maximum number of source blocks to render, or `null` for no
 * block-count bound.
 * @param maxLinesPerTextBlock Maximum visible lines in each text block, or
 * `null` for no per-block line bound.
 * @param textScale Positive multiplier applied on top of the host font scale
 * to every `sp` text rendered inside the preview, including custom preview
 * renderers. `dp` values are not scaled directly, but text remeasures normally,
 * so wrapping and intrinsic preview size may change.
 * @param textOverflow Overflow behavior applied when a text block reaches its
 * line bound.
 * @param textSelectionEnabled Whether static preview text may opt into Compose
 * selection. Disabled by default to preserve surrounding card interactions.
 * @param linksEnabled Whether preview renderers may delegate link activation to
 * the host.
 * @param crashPolicy Policy used by preview measurement, placement, and draw
 * containment. Composition-time custom-renderer exceptions remain a trusted
 * extension boundary and are not caught in-tree.
 * @param onInternalError Optional host reporter for contained preview failures.
 */
@ExperimentalCascadePreviewApi
@Immutable
public data class CascadeDocumentPreviewConfig(
    val maxBlocks: Int? = DEFAULT_MAX_BLOCKS,
    val maxLinesPerTextBlock: Int? = DEFAULT_MAX_LINES_PER_TEXT_BLOCK,
    val textScale: Float = 1f,
    val textOverflow: TextOverflow = TextOverflow.Ellipsis,
    val textSelectionEnabled: Boolean = false,
    val linksEnabled: Boolean = true,
    val crashPolicy: CrashPolicy = CrashPolicy.ContainAndReport,
    val onInternalError: CascadeErrorReporter? = null,
) {
    init {
        require(maxBlocks == null || maxBlocks > 0) {
            "maxBlocks must be greater than zero when bounded; use null or " +
                "CascadeDocumentPreviewConfig.Unbounded for no block limit (was $maxBlocks)"
        }
        require(maxLinesPerTextBlock == null || maxLinesPerTextBlock > 0) {
            "maxLinesPerTextBlock must be greater than zero when bounded; use null or " +
                "CascadeDocumentPreviewConfig.Unbounded for no line limit " +
                "(was $maxLinesPerTextBlock)"
        }
        require(textScale.isFinite() && textScale > 0f) {
            "textScale must be finite and greater than zero (was $textScale)"
        }
    }

    public companion object {
        /** Bounded 4-block × 3-line preset for grids, feeds, and search results. */
        public val GridCard: CascadeDocumentPreviewConfig = CascadeDocumentPreviewConfig()

        /** Default preview policy. Currently identical to [GridCard]. */
        public val Default: CascadeDocumentPreviewConfig = GridCard

        /**
         * Explicit no-limit preset for small, static documents.
         *
         * This removes presentation limits only; the preview remains
         * non-scrollable and eagerly composes all supplied blocks.
         */
        public val Unbounded: CascadeDocumentPreviewConfig = CascadeDocumentPreviewConfig(
            maxBlocks = null,
            maxLinesPerTextBlock = null,
        )
    }
}
