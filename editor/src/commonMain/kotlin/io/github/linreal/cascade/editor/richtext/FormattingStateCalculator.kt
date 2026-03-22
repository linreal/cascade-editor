package io.github.linreal.cascade.editor.richtext

import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.SpanStyle.Companion.kindMatches
import io.github.linreal.cascade.editor.core.TextSpan
import kotlin.math.max
import kotlin.math.min

/**
 * Pure function that computes [FormattingState] from raw inputs.
 */
internal object FormattingStateCalculator {

    internal fun compute(
        focusedBlockId: BlockId?,
        focusedBlockType: BlockType?,
        hasBlockSelection: Boolean,
        isDragging: Boolean,
        visibleSelectionStart: Int,
        visibleSelectionEnd: Int,
        spans: List<TextSpan>,
        pendingStyles: Set<SpanStyle>?,
        trackedStyles: List<SpanStyle>,
    ): FormattingState {
        val canFormat = focusedBlockId != null
            && focusedBlockType?.supportsText == true
            && focusedBlockType !is BlockType.Code
            && !hasBlockSelection
            && !isDragging

        if (!canFormat) {
            return FormattingState(
                styles = emptyMap(),
                canFormat = false,
                focusedBlockId = focusedBlockId,
                selectionCollapsed = visibleSelectionStart == visibleSelectionEnd,
            )
        }

        val normalizedStart = min(visibleSelectionStart, visibleSelectionEnd)
        val normalizedEnd = max(visibleSelectionStart, visibleSelectionEnd)
        val collapsed = normalizedStart == normalizedEnd

        val styles: Map<SpanStyle, StyleStatus> = if (collapsed) {
            computeCollapsedStyles(normalizedStart, spans, pendingStyles, trackedStyles)
        } else {
            computeRangedStyles(normalizedStart, normalizedEnd, spans, trackedStyles)
        }

        return FormattingState(
            styles = styles,
            canFormat = true,
            focusedBlockId = focusedBlockId,
            selectionCollapsed = collapsed,
        )
    }

    private fun computeCollapsedStyles(
        position: Int,
        spans: List<TextSpan>,
        pendingStyles: Set<SpanStyle>?,
        trackedStyles: List<SpanStyle>,
    ): Map<SpanStyle, StyleStatus> {
        if (pendingStyles != null) {
            // Pending styles are canonical — present = FullyActive, absent = Absent
            return trackedStyles.associateWith { style ->
                if (pendingStyles.any { kindMatches(it, style) }) StyleStatus.FullyActive else StyleStatus.Absent
            }
        }

        // Continuation semantics: inherit from position - 1
        val activeStyles = if (position > 0) {
            SpanAlgorithms.activeStylesAt(spans, position - 1)
        } else {
            emptySet()
        }
        return trackedStyles.associateWith { style ->
            if (activeStyles.any { kindMatches(it, style) }) StyleStatus.FullyActive else StyleStatus.Absent
        }
    }

    private fun computeRangedStyles(
        rangeStart: Int,
        rangeEnd: Int,
        spans: List<TextSpan>,
        trackedStyles: List<SpanStyle>,
    ): Map<SpanStyle, StyleStatus> {
        return trackedStyles.associateWith { style ->
            SpanAlgorithms.queryStyleStatus(spans, rangeStart, rangeEnd, style)
        }
    }
}
