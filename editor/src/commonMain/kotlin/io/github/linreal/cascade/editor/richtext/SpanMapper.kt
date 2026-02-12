package io.github.linreal.cascade.editor.richtext

import androidx.compose.foundation.text.input.OutputTransformation
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
    // TODO: inject via theme when theming/styling API is implemented
    private val linkColor: Color = Color(0xFF0B57D0)
    private val inlineCodeBackground: Color = Color(0x14000000)

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
    fun toOutputTransformation(spans: List<TextSpan>): OutputTransformation? {
        if (spans.isEmpty()) return null

        val mapped = spans.mapNotNull { span ->
            val composeStyle = toComposeSpanStyle(span.style) ?: return@mapNotNull null
            MappedSpan(start = span.start, end = span.end, style = composeStyle)
        }
        if (mapped.isEmpty()) return null

        return OutputTransformation {
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
    }

    /**
     * Pure domain-to-Compose style mapping.
     *
     * TODO: Underline + StrikeThrough on the same range will not stack visually —
     *  Compose applies each SpanStyle independently and the later TextDecoration
     *  overwrites the earlier one. Fix by detecting overlapping decoration spans
     *  at the OutputTransformation builder level and using
     *  TextDecoration.combine(listOf(Underline, LineThrough)).
     */
    fun toComposeSpanStyle(style: SpanStyle): ComposeSpanStyle? {
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

    private data class MappedSpan(
        val start: Int,
        val end: Int,
        val style: ComposeSpanStyle,
    )
}
