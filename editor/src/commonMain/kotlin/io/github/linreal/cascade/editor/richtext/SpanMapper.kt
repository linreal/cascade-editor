package io.github.linreal.cascade.editor.richtext

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.SpanStyle as ComposeSpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan

/**
 * Maps domain span styles to Compose text styling primitives.
 *
 * All input ranges are expected to use visible-text coordinates.
 */
internal object SpanMapper {

    private const val sentinelOffset: Int = 1
    private val combinedDecoration: TextDecoration = TextDecoration.combine(
        listOf(TextDecoration.Underline, TextDecoration.LineThrough)
    )

    /**
     * Builds an [OutputTransformation] for the provided spans.
     *
     * The transformation:
     * - maps domain styles to Compose styles,
     * - clamps every range against the current visible text length,
     * - shifts ranges by the sentinel offset used by the editor text field.
     *
     * Returns `null` when there are no renderable spans.
     */
    fun toOutputTransformation(
        spans: List<TextSpan>,
        inlineCodeBackground: Color,
        highlightBackground: Color,
        linkText: Color = Color.Unspecified,
        baseDecoration: TextDecoration? = null,
    ): OutputTransformation? {
        if (spans.isEmpty()) return null

        val mapped = mapRenderableSpans(spans, inlineCodeBackground, highlightBackground, linkText, baseDecoration)
        if (mapped.isEmpty()) return null

        return OutputTransformation {
            applyMappedStyles(mapped)
        }
    }

    /**
     * Applies rich-text styles to the current [TextFieldBuffer] from runtime spans.
     *
     * Unlike [toOutputTransformation], this is suitable for a stable OutputTransformation
     * instance that reads the latest span list on each invocation.
     *
     * [baseDecoration] is the [TextDecoration] carried by the consumer's base [TextStyle]
     * (e.g. completion strikethrough on a checked todo). Per-run [SpanStyle.textDecoration]
     * replaces the base field-by-field during Compose merging, so without combining here,
     * a base decoration would be silently dropped on any run that sets its own decoration.
     */
    fun TextFieldBuffer.applyStyles(
        spans: List<TextSpan>,
        inlineCodeBackground: Color,
        highlightBackground: Color,
        linkText: Color = Color.Unspecified,
        baseDecoration: TextDecoration? = null,
    ) {
        if (spans.isEmpty()) return

        val mapped = mapRenderableSpans(spans, inlineCodeBackground, highlightBackground, linkText, baseDecoration)
        if (mapped.isEmpty()) return

        applyMappedStyles(mapped)
    }

    private fun TextFieldBuffer.applyMappedStyles(mapped: List<RenderSpan>) {
        val visibleLength = (length - sentinelOffset).coerceAtLeast(0)
        for (span in mapped) {
            val clampedStart = span.start.coerceIn(0, visibleLength)
            val clampedEnd = span.end.coerceIn(clampedStart, visibleLength)
            if (clampedStart >= clampedEnd) continue

            addStyle(
                spanStyle = span.style,
                start = clampedStart + sentinelOffset,
                end = clampedEnd + sentinelOffset,
            )
        }
    }

    /**
     * Maps domain spans to renderable Compose span runs.
     *
     * Includes decoration overlays for underline+strikethrough intersections so both
     * decorations render cumulatively in overlapping regions.
     */
    internal fun mapRenderableSpans(
        spans: List<TextSpan>,
        inlineCodeBackground: Color,
        highlightBackground: Color,
        linkText: Color = Color.Unspecified,
        baseDecoration: TextDecoration? = null,
    ): List<RenderSpan> {
        if (spans.isEmpty()) return emptyList()

        val base = mapBaseRenderableSpans(spans, inlineCodeBackground, highlightBackground, linkText)
        if (base.isEmpty()) return emptyList()

        // Overlay must be built from the un-combined base so the equality filters on
        // Underline / LineThrough still match — combining baseDecoration into the runs
        // first would change those values and miss the run-vs-run overlap.
        val overlay = buildCombinedDecorationOverlay(base)
        val combined = if (overlay.isEmpty()) base else base + overlay

        if (baseDecoration == null || baseDecoration == TextDecoration.None) {
            return combined
        }
        return combined.map { run ->
            val runDecoration = run.style.textDecoration ?: return@map run
            run.copy(
                style = run.style.copy(
                    textDecoration = TextDecoration.combine(listOf(baseDecoration, runDecoration))
                )
            )
        }
    }

    /**
     * Builds direct render runs before cumulative decoration overlays are added.
     *
     * Link ranges are collected first because links provide their own underline;
     * explicit underline spans need to be clipped around those ranges.
     */
    private fun mapBaseRenderableSpans(
        spans: List<TextSpan>,
        inlineCodeBackground: Color,
        highlightBackground: Color,
        linkText: Color,
    ): List<RenderSpan> {
        val linkIntervals = spans.mapNotNull { span ->
            if (span.style is SpanStyle.Link) Interval(span.start, span.end) else null
        }
        return spans.flatMap { span ->
            val composeStyle = toComposeSpanStyle(span.style, inlineCodeBackground, highlightBackground, linkText)
                ?: return@flatMap emptyList()

            if (span.style == SpanStyle.Underline && linkIntervals.isNotEmpty()) {
                return@flatMap subtractIntervals(
                    source = Interval(span.start, span.end),
                    cuts = linkIntervals,
                ).map { interval ->
                    RenderSpan(start = interval.start, end = interval.end, style = composeStyle)
                }
            }

            listOf(RenderSpan(start = span.start, end = span.end, style = composeStyle))
        }
    }

    /**
     * Splits [source] around [cuts] so explicit underline runs do not stack on
     * top of link-provided underline decoration in shared ranges.
     */
    private fun subtractIntervals(source: Interval, cuts: List<Interval>): List<Interval> {
        if (source.start >= source.end) return emptyList()

        val result = mutableListOf<Interval>()
        var cursor = source.start

        for (cut in mergeIntervals(cuts)) {
            if (cut.end <= cursor || cut.start >= source.end) continue

            val clippedStart = cut.start.coerceAtLeast(source.start)
            val clippedEnd = cut.end.coerceAtMost(source.end)
            if (cursor < clippedStart) {
                result += Interval(cursor, clippedStart)
            }
            cursor = maxOf(cursor, clippedEnd)
            if (cursor >= source.end) break
        }

        if (cursor < source.end) {
            result += Interval(cursor, source.end)
        }
        return result
    }

    private fun buildCombinedDecorationOverlay(base: List<RenderSpan>): List<RenderSpan> {
        val underlineIntervals = mergeIntervals(
            base
                .filter { it.style.textDecoration == TextDecoration.Underline }
                .map { Interval(it.start, it.end) }
        )
        val strikeIntervals = mergeIntervals(
            base
                .filter { it.style.textDecoration == TextDecoration.LineThrough }
                .map { Interval(it.start, it.end) }
        )
        if (underlineIntervals.isEmpty() || strikeIntervals.isEmpty()) return emptyList()

        val overlapIntervals = intersectIntervals(underlineIntervals, strikeIntervals)
        if (overlapIntervals.isEmpty()) return emptyList()

        return overlapIntervals.map { interval ->
            RenderSpan(
                start = interval.start,
                end = interval.end,
                style = ComposeSpanStyle(textDecoration = combinedDecoration),
            )
        }
    }

    private fun intersectIntervals(
        first: List<Interval>,
        second: List<Interval>,
    ): List<Interval> {
        val intersections = mutableListOf<Interval>()
        var firstIndex = 0
        var secondIndex = 0

        while (firstIndex < first.size && secondIndex < second.size) {
            val left = first[firstIndex]
            val right = second[secondIndex]
            val start = maxOf(left.start, right.start)
            val end = minOf(left.end, right.end)
            if (start < end) {
                intersections += Interval(start, end)
            }

            when {
                left.end < right.end -> firstIndex++
                right.end < left.end -> secondIndex++
                else -> {
                    firstIndex++
                    secondIndex++
                }
            }
        }

        return intersections
    }

    private fun mergeIntervals(intervals: List<Interval>): List<Interval> {
        if (intervals.isEmpty()) return emptyList()
        val sorted = intervals.sortedWith(compareBy({ it.start }, { it.end }))
        val merged = mutableListOf<Interval>()

        var current = sorted.first()
        for (index in 1 until sorted.size) {
            val next = sorted[index]
            if (next.start <= current.end) {
                current = Interval(current.start, maxOf(current.end, next.end))
            } else {
                merged += current
                current = next
            }
        }
        merged += current
        return merged
    }

    /**
     * Pure domain-to-Compose style mapping.
     */
    fun toComposeSpanStyle(
        style: SpanStyle,
        inlineCodeBackground: Color,
        highlightBackground: Color,
        linkText: Color = Color.Unspecified,
    ): ComposeSpanStyle? {
        return when (style) {
            SpanStyle.Bold -> ComposeSpanStyle(fontWeight = FontWeight.Bold)
            SpanStyle.Italic -> ComposeSpanStyle(fontStyle = FontStyle.Italic)
            SpanStyle.Underline -> ComposeSpanStyle(textDecoration = TextDecoration.Underline)
            SpanStyle.StrikeThrough -> ComposeSpanStyle(textDecoration = TextDecoration.LineThrough)
            SpanStyle.InlineCode -> ComposeSpanStyle(
                fontFamily = FontFamily.Monospace,
                background = inlineCodeBackground,
            )
            is SpanStyle.Highlight -> ComposeSpanStyle(background = highlightBackground)
            is SpanStyle.Link -> ComposeSpanStyle(
                color = linkText,
                textDecoration = TextDecoration.Underline,
            )
            is SpanStyle.Custom -> null
        }
    }

    internal data class RenderSpan(
        val start: Int,
        val end: Int,
        val style: ComposeSpanStyle,
    )

    private data class Interval(
        val start: Int,
        val end: Int,
    )
}
