package io.github.linreal.cascade.editor.richtext

import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import kotlin.math.max
import kotlin.math.min

/**
 * Style presence status for a given range.
 */
public enum class StyleStatus {
    /** The style fully covers the queried range. */
    FullyActive,

    /** The style covers part of the queried range. */
    Partial,

    /** The style is not present in the queried range. */
    Absent,
}

/**
 * Pure, stateless algorithms for manipulating [TextSpan] lists.
 *
 * All coordinates are visible-text coordinates (half-open `[start, end)` intervals).
 * This layer has no sentinel awareness — callers must convert to/from buffer coordinates.
 *
 * Complexity is block-local: every function operates on a single block's span list.
 */
internal object SpanAlgorithms {

    // ── Normalization ────────────────────────────────────────────────────

    /**
     * Normalizes a span list:
     * 1. Clamps coordinates to `[0, textLength]`.
     * 2. Drops empty spans (`start >= end` after clamping).
     * 3. Merges overlapping and adjacent same-style spans.
     * 4. Sorts by start position (then end for determinism).
     *
     * Different-style overlaps are preserved (cumulative application).
     */
    fun normalize(spans: List<TextSpan>, textLength: Int): List<TextSpan> {
        if (spans.isEmpty()) return emptyList()

        // 1 + 2: clamp and filter
        val valid = spans.mapNotNull { span ->
            val s = span.start.coerceIn(0, textLength)
            val e = span.end.coerceIn(s, textLength)
            if (s < e) TextSpan(s, e, span.style) else null
        }

        // 3: merge same-style overlaps / adjacents
        return mergeOverlapping(valid)
    }

    // ── Edit Adjustment ──────────────────────────────────────────────────

    /**
     * Adjusts span coordinates after a text edit.
     *
     * Models any edit as replacing `[editStart, editStart + deletedLength)` with
     * [insertedLength] new characters.
     *
     * Boundary rules:
     * - **Start** uses "after" bias: insertions at span start push the span right
     *   (new character is NOT styled — pending-style mechanism handles continuation).
     * - **End** uses "before" bias: insertions at span end do NOT extend the span.
     *
     * Spans that collapse to empty after adjustment are dropped.
     */
    fun adjustForEdit(
        spans: List<TextSpan>,
        editStart: Int,
        deletedLength: Int,
        insertedLength: Int,
    ): List<TextSpan> {
        require(editStart >= 0) { "editStart must be non-negative, got $editStart" }
        require(deletedLength >= 0) { "deletedLength must be non-negative, got $deletedLength" }
        require(insertedLength >= 0) { "insertedLength must be non-negative, got $insertedLength" }

        val editEnd = editStart + deletedLength
        val delta = insertedLength - deletedLength

        val adjusted = spans.mapNotNull { span ->
            val newStart = adjustStart(span.start, editStart, editEnd, delta)
            val newEnd = adjustEnd(span.end, editStart, editEnd, delta)
            if (newStart < newEnd) TextSpan(newStart, newEnd, span.style) else null
        }
        return mergeOverlapping(adjusted)
    }

    /**
     * Adjust a span start position (after bias).
     * - Strictly before edit: unchanged.
     * - At or after edit end: shift by delta.
     * - Within deleted range: collapse to editStart.
     */
    private fun adjustStart(s: Int, editStart: Int, editEnd: Int, delta: Int): Int = when {
        s < editStart -> s
        s >= editEnd -> s + delta
        else -> editStart // s in [editStart, editEnd)
    }

    /**
     * Adjust a span end position (before bias).
     * - At or before edit start: unchanged.
     * - Strictly after edit end: shift by delta.
     * - Within deleted range (exclusive of editStart): collapse to editStart.
     */
    private fun adjustEnd(e: Int, editStart: Int, editEnd: Int, delta: Int): Int = when {
        e <= editStart -> e
        e > editEnd -> e + delta
        else -> editStart // e in (editStart, editEnd]
    }

    // ── Split / Merge ────────────────────────────────────────────────────

    /**
     * Splits spans at [position] into two groups for a block split.
     *
     * - Spans entirely before `position` stay in the first list.
     * - Spans entirely at or after `position` go to the second list (shifted to start at 0).
     * - Spans crossing `position` are clipped into both lists.
     *
     * @return Pair of (firstBlockSpans, secondBlockSpans).
     */
    fun splitAt(
        spans: List<TextSpan>,
        position: Int,
    ): Pair<List<TextSpan>, List<TextSpan>> {
        require(position >= 0) { "position must be non-negative, got $position" }

        val first = mutableListOf<TextSpan>()
        val second = mutableListOf<TextSpan>()

        for (span in spans) {
            when {
                span.end <= position -> first.add(span)
                span.start >= position -> second.add(
                    TextSpan(span.start - position, span.end - position, span.style)
                )
                else -> {
                    // Span crosses the split point
                    first.add(TextSpan(span.start, position, span.style))
                    second.add(TextSpan(0, span.end - position, span.style))
                }
            }
        }

        return first to second
    }

    /**
     * Merges spans from two blocks after a text merge.
     *
     * Second block's spans are shifted by [firstTextLength], then
     * adjacent/overlapping same-style spans at the boundary are merged.
     */
    fun mergeSpans(
        firstSpans: List<TextSpan>,
        secondSpans: List<TextSpan>,
        firstTextLength: Int,
    ): List<TextSpan> {
        require(firstTextLength >= 0) { "firstTextLength must be non-negative, got $firstTextLength" }

        val shifted = secondSpans.map {
            TextSpan(it.start + firstTextLength, it.end + firstTextLength, it.style)
        }
        return mergeOverlapping(firstSpans + shifted)
    }

    // ── Style Application ────────────────────────────────────────────────

    /**
     * Applies [style] to `[rangeStart, rangeEnd)`.
     *
     * If the range overlaps or is adjacent to existing spans of the same style,
     * they are merged. Different-style spans are left untouched.
     *
     * No-op if normalized range is empty after ordering/clamping.
     */
    fun applyStyle(
        spans: List<TextSpan>,
        rangeStart: Int,
        rangeEnd: Int,
        style: SpanStyle,
        textLength: Int,
    ): List<TextSpan> {
        require(textLength >= 0) { "textLength must be non-negative, got $textLength" }

        val normalizedStart = max(0, min(rangeStart, rangeEnd)).coerceAtMost(textLength)
        val normalizedEnd = max(0, max(rangeStart, rangeEnd)).coerceAtMost(textLength)
        if (normalizedStart >= normalizedEnd) return spans

        val newSpan = TextSpan(normalizedStart, normalizedEnd, style)
        return normalize(spans + newSpan, textLength)
    }

    /**
     * Removes [style] from `[rangeStart, rangeEnd)`.
     *
     * Spans of the matching style are clipped or split around the removal range.
     * Spans with other styles are untouched.
     *
     * No-op if normalized range is empty after ordering/clamping.
     */
    fun removeStyle(
        spans: List<TextSpan>,
        rangeStart: Int,
        rangeEnd: Int,
        style: SpanStyle,
    ): List<TextSpan> {
        val normalizedStart = max(0, min(rangeStart, rangeEnd))
        val normalizedEnd = max(0, max(rangeStart, rangeEnd))
        if (normalizedStart >= normalizedEnd) return spans

        return spans.flatMap { span ->
            if (span.style != style) {
                listOf(span)
            } else {
                buildList {
                    // Part before the removal range
                    if (span.start < normalizedStart && span.end > normalizedStart) {
                        add(TextSpan(span.start, minOf(span.end, normalizedStart), style))
                    } else if (span.end <= normalizedStart) {
                        add(span) // entirely before removal
                    }
                    // Part after the removal range
                    if (span.end > normalizedEnd && span.start < normalizedEnd) {
                        add(TextSpan(maxOf(span.start, normalizedEnd), span.end, style))
                    } else if (span.start >= normalizedEnd) {
                        add(span) // entirely after removal
                    }
                }
            }
        }
    }

    /**
     * Toggles [style] on `[rangeStart, rangeEnd)`.
     *
     * If the style is [StyleStatus.FullyActive] in the normalized range, it is removed.
     * Otherwise it is applied (covering any gaps).
     */
    fun toggleStyle(
        spans: List<TextSpan>,
        rangeStart: Int,
        rangeEnd: Int,
        style: SpanStyle,
        textLength: Int,
    ): List<TextSpan> {
        val normalizedStart = min(rangeStart, rangeEnd)
        val normalizedEnd = max(rangeStart, rangeEnd)
        if (normalizedStart >= normalizedEnd) return spans

        val status = queryStyleStatus(spans, normalizedStart, normalizedEnd, style)
        return when (status) {
            StyleStatus.FullyActive -> removeStyle(spans, normalizedStart, normalizedEnd, style)
            else -> applyStyle(spans, normalizedStart, normalizedEnd, style, textLength)
        }
    }

    // ── Style Queries ────────────────────────────────────────────────────

    /**
     * Queries the presence of [style] in `[rangeStart, rangeEnd)`.
     *
     * For a **collapsed cursor** (`rangeStart == rangeEnd`): returns [StyleStatus.FullyActive]
     * if the cursor is inside a span of the given style, [StyleStatus.Absent] otherwise.
     *
     * For a **ranged selection**: computes union coverage of matching spans within
     * the range and returns [StyleStatus.FullyActive], [StyleStatus.Partial], or
     * [StyleStatus.Absent] accordingly.
     */
    fun queryStyleStatus(
        spans: List<TextSpan>,
        rangeStart: Int,
        rangeEnd: Int,
        style: SpanStyle,
    ): StyleStatus {
        val normalizedStart = max(0, min(rangeStart, rangeEnd))
        val normalizedEnd = max(0, max(rangeStart, rangeEnd))

        if (normalizedStart == normalizedEnd) {
            // Collapsed cursor: check containment
            val inside = spans.any {
                it.style == style && it.start <= normalizedStart && it.end > normalizedStart
            }
            return if (inside) StyleStatus.FullyActive else StyleStatus.Absent
        }

        // Ranged selection: compute coverage
        val clipped = spans
            .filter { it.style == style }
            .map { maxOf(it.start, normalizedStart) to minOf(it.end, normalizedEnd) }
            .filter { it.first < it.second }
            .sortedBy { it.first }

        if (clipped.isEmpty()) return StyleStatus.Absent

        // Merge overlapping intervals and sum coverage
        var totalCoverage = 0
        var curStart = clipped[0].first
        var curEnd = clipped[0].second

        for (i in 1 until clipped.size) {
            val (s, e) = clipped[i]
            if (s <= curEnd) {
                curEnd = maxOf(curEnd, e)
            } else {
                totalCoverage += curEnd - curStart
                curStart = s
                curEnd = e
            }
        }
        totalCoverage += curEnd - curStart

        val rangeLength = normalizedEnd - normalizedStart
        return when {
            totalCoverage >= rangeLength -> StyleStatus.FullyActive
            totalCoverage > 0 -> StyleStatus.Partial
            else -> StyleStatus.Absent
        }
    }

    /**
     * Returns all styles active at a collapsed cursor [position].
     *
     * A style is active if any span with that style satisfies `start <= position < end`.
     */
    fun activeStylesAt(
        spans: List<TextSpan>,
        position: Int,
    ): Set<SpanStyle> {
        return spans
            .filter { it.start <= position && it.end > position }
            .mapTo(mutableSetOf()) { it.style }
    }

    // ── Internal Helpers ─────────────────────────────────────────────────

    /**
     * Merges overlapping and adjacent same-style spans.
     * Different-style spans are preserved independently.
     * Output is sorted by (start, end).
     */
    private fun mergeOverlapping(spans: List<TextSpan>): List<TextSpan> {
        if (spans.isEmpty()) return emptyList()

        val result = mutableListOf<TextSpan>()

        // Group by style, merge within each group
        val grouped = spans.groupBy { it.style }
        for ((style, group) in grouped) {
            val sorted = group.sortedBy { it.start }
            var current = sorted[0]
            for (i in 1 until sorted.size) {
                val next = sorted[i]
                if (next.start <= current.end) {
                    // Overlapping or adjacent — merge
                    current = TextSpan(current.start, maxOf(current.end, next.end), style)
                } else {
                    result.add(current)
                    current = next
                }
            }
            result.add(current)
        }

        return result.sortedWith(compareBy({ it.start }, { it.end }))
    }
}
