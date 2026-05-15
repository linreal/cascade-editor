package io.github.linreal.cascade.editor.richtext

import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy
import kotlin.math.max
import kotlin.math.min

/**
 * Pure function that computes [LinkState] from raw focused-block inputs.
 */
internal object LinkStateCalculator {

    /**
     * Computes the current link state without reading Compose or editor holders.
     *
     * Collapsed cursors only resolve a link when the cursor is strictly inside
     * the link range. Ranged selections resolve one existing URL only when the
     * whole range is covered by link spans with exactly one URL.
     *
     * [policy] controls whether link editing is enabled while leaving
     * non-mutating target and existing-link metadata available for chrome.
     */
    internal fun compute(
        focusedBlockId: BlockId?,
        focusedBlockType: BlockType?,
        hasBlockSelection: Boolean,
        isDragging: Boolean,
        visibleText: String,
        visibleSelectionStart: Int,
        visibleSelectionEnd: Int,
        spans: List<TextSpan>,
        policy: EditorInteractionPolicy = EditorInteractionPolicy.Editable,
    ): LinkState {
        if (
            focusedBlockId == null ||
            focusedBlockType?.supportsText != true ||
            !focusedBlockType.supportsSpans ||
            hasBlockSelection ||
            isDragging
        ) {
            return if (focusedBlockId == null) {
                LinkState.Empty
            } else {
                LinkState.Empty.copy(focusedBlockId = focusedBlockId)
            }
        }

        val rangeStart = min(visibleSelectionStart, visibleSelectionEnd).coerceIn(0, visibleText.length)
        val rangeEnd = max(visibleSelectionStart, visibleSelectionEnd).coerceIn(0, visibleText.length)
        val collapsed = rangeStart == rangeEnd
        val target = LinkTarget(focusedBlockId, rangeStart, rangeEnd)
        val linkSpans = spans.filter { it.style is SpanStyle.Link }
        val resolution = if (collapsed) {
            resolveCollapsed(
                blockId = focusedBlockId,
                position = rangeStart,
                visibleText = visibleText,
                linkSpans = linkSpans,
            )
        } else {
            resolveRange(
                blockId = focusedBlockId,
                rangeStart = rangeStart,
                rangeEnd = rangeEnd,
                visibleText = visibleText,
                linkSpans = linkSpans,
            )
        }

        return LinkState(
            canLink = policy.canEditLinks,
            focusedBlockId = focusedBlockId,
            target = target,
            targetText = visibleText.substring(rangeStart, rangeEnd),
            selectionCollapsed = collapsed,
            existingUrl = resolution.existingUrl,
            existingLinkRange = resolution.existingLinkRange,
            existingLinkText = resolution.existingLinkText,
            isInsideLink = collapsed && resolution.existingUrl != null,
            intersectsLink = resolution.intersectsLink,
        )
    }

    private fun resolveCollapsed(
        blockId: BlockId,
        position: Int,
        visibleText: String,
        linkSpans: List<TextSpan>,
    ): LinkResolution {
        val span = linkSpans.firstOrNull { it.start < position && position < it.end }
            ?: return LinkResolution.Empty
        val link = span.style as SpanStyle.Link
        val range = LinkTarget(blockId, span.start, span.end)
        return LinkResolution(
            existingUrl = link.url,
            existingLinkRange = range,
            existingLinkText = visibleText.substring(
                span.start.coerceIn(0, visibleText.length),
                span.end.coerceIn(0, visibleText.length),
            ),
            intersectsLink = true,
        )
    }

    private fun resolveRange(
        blockId: BlockId,
        rangeStart: Int,
        rangeEnd: Int,
        visibleText: String,
        linkSpans: List<TextSpan>,
    ): LinkResolution {
        val intersections = linkSpans.mapNotNull { span ->
            val start = max(span.start, rangeStart)
            val end = min(span.end, rangeEnd)
            if (start >= end) return@mapNotNull null
            LinkIntersection(
                rangeStart = start,
                rangeEnd = end,
                sourceStart = span.start,
                sourceEnd = span.end,
                url = (span.style as SpanStyle.Link).url,
            )
        }

        if (intersections.isEmpty()) return LinkResolution.Empty

        val urls = intersections.mapTo(linkedSetOf()) { it.url }
        if (urls.size != 1) {
            return LinkResolution(intersectsLink = true)
        }

        val coveredLength = mergedCoverageLength(intersections)
        if (coveredLength != rangeEnd - rangeStart) {
            return LinkResolution(intersectsLink = true)
        }

        val linkRangeStart = intersections.minOf { it.sourceStart }
        val linkRangeEnd = intersections.maxOf { it.sourceEnd }
        return LinkResolution(
            existingUrl = urls.single(),
            existingLinkRange = LinkTarget(blockId, linkRangeStart, linkRangeEnd),
            existingLinkText = visibleText.substring(
                linkRangeStart.coerceIn(0, visibleText.length),
                linkRangeEnd.coerceIn(0, visibleText.length),
            ),
            intersectsLink = true,
        )
    }

    private fun mergedCoverageLength(intersections: List<LinkIntersection>): Int {
        val sorted = intersections.sortedBy { it.rangeStart }
        var total = 0
        var currentStart = sorted.first().rangeStart
        var currentEnd = sorted.first().rangeEnd

        for (index in 1 until sorted.size) {
            val next = sorted[index]
            if (next.rangeStart <= currentEnd) {
                currentEnd = max(currentEnd, next.rangeEnd)
            } else {
                total += currentEnd - currentStart
                currentStart = next.rangeStart
                currentEnd = next.rangeEnd
            }
        }
        total += currentEnd - currentStart
        return total
    }

    private data class LinkIntersection(
        val rangeStart: Int,
        val rangeEnd: Int,
        val sourceStart: Int,
        val sourceEnd: Int,
        val url: String,
    )

    private data class LinkResolution(
        val existingUrl: String? = null,
        val existingLinkRange: LinkTarget? = null,
        val existingLinkText: String? = null,
        val intersectsLink: Boolean = false,
    ) {
        companion object {
            val Empty: LinkResolution = LinkResolution()
        }
    }
}
