package io.github.linreal.cascade.editor.richtext

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle as ComposeSpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
        linkColor: Color,
        inlineCodeBackground: Color,
    ): OutputTransformation? {
        if (spans.isEmpty()) return null

        val mapped = mapRenderableSpans(spans, linkColor, inlineCodeBackground)
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
     */
    fun TextFieldBuffer.applyStyles(
        spans: List<TextSpan>,
        linkColor: Color,
        inlineCodeBackground: Color,
    ) {
        if (spans.isEmpty()) return

        val mapped = mapRenderableSpans(spans, linkColor, inlineCodeBackground)
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
        linkColor: Color,
        inlineCodeBackground: Color,
    ): List<RenderSpan> {
        if (spans.isEmpty()) return emptyList()

        val base = spans.mapNotNull { span ->
            val composeStyle = toComposeSpanStyle(span.style, linkColor, inlineCodeBackground) ?: return@mapNotNull null
            RenderSpan(start = span.start, end = span.end, style = composeStyle)
        }
        if (base.isEmpty()) return emptyList()

        val overlay = buildCombinedDecorationOverlay(base)
        return if (overlay.isEmpty()) base else base + overlay
    }

    private fun buildCombinedDecorationOverlay(base: List<RenderSpan>): List<RenderSpan> {
        val underlineRuns = base.filter { it.style.textDecoration == TextDecoration.Underline }
        val strikeRuns = base.filter { it.style.textDecoration == TextDecoration.LineThrough }
        if (underlineRuns.isEmpty() || strikeRuns.isEmpty()) return emptyList()

        val overlapIntervals = mutableListOf<Interval>()
        for (underline in underlineRuns) {
            for (strike in strikeRuns) {
                val start = maxOf(underline.start, strike.start)
                val end = minOf(underline.end, strike.end)
                if (start < end) {
                    overlapIntervals += Interval(start, end)
                }
            }
        }
        if (overlapIntervals.isEmpty()) return emptyList()

        return mergeIntervals(overlapIntervals).map { interval ->
            RenderSpan(
                start = interval.start,
                end = interval.end,
                style = ComposeSpanStyle(textDecoration = combinedDecoration),
            )
        }
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
        linkColor: Color,
        inlineCodeBackground: Color,
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
            is SpanStyle.Highlight -> ComposeSpanStyle(background = Color(style.colorArgb))
            is SpanStyle.Link -> ComposeSpanStyle(
                color = linkColor,
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
