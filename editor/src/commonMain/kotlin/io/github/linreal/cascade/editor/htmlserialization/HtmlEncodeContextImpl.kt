package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.SpanAlgorithms

internal class HtmlEncodeContextImpl(
    private val profile: HtmlProfile,
    private val warnings: MutableList<HtmlEncodeWarning>,
) : HtmlEncodeContext() {

    override fun encodeInline(block: Block): String {
        val content = block.content as? BlockContent.Text ?: return ""
        return encodeInlineFragment(
            text = content.text,
            spans = content.spans,
            preserveNewlines = block.type == BlockType.Code,
        )
    }

    override fun encodeTextOnly(block: Block): String {
        val content = block.content as? BlockContent.Text ?: return ""
        return escapeText(content.text)
    }

    override fun encodeInlineFragment(
        text: String,
        spans: List<TextSpan>,
        preserveNewlines: Boolean,
    ): String {
        if (text.isEmpty()) return ""

        val encodedSpans = SpanAlgorithms.normalize(spans, text.length)
            .mapIndexedNotNull { index, span -> span.toEncodedSpan(index) }
        if (encodedSpans.isEmpty()) return encodeTextSegment(text, preserveNewlines)

        val boundaries = mutableSetOf(0, text.length)
        for (span in encodedSpans) {
            boundaries += span.start
            boundaries += span.end
        }

        val builder = StringBuilder(text.length)
        var openSpans = emptyList<EncodedSpan>()
        val boundaryList = boundaries.sorted()
        for (boundaryIndex in 0 until boundaryList.lastIndex) {
            val start = boundaryList[boundaryIndex]
            val end = boundaryList[boundaryIndex + 1]
            if (start == end) continue

            val activeSpans = encodedSpans
                .filter { it.start <= start && it.end >= end }
                .sortedWith(compareBy<EncodedSpan> { it.start }.thenByDescending { it.end }.thenBy { it.index })

            val sharedPrefixSize = openSpans.sharedPrefixSize(activeSpans)
            for (index in openSpans.lastIndex downTo sharedPrefixSize) {
                builder.append(openSpans[index].tagPair.close)
            }
            for (index in sharedPrefixSize until activeSpans.size) {
                builder.append(activeSpans[index].tagPair.open)
            }

            builder.append(encodeTextSegment(text.substring(start, end), preserveNewlines))
            openSpans = activeSpans
        }

        for (index in openSpans.lastIndex downTo 0) {
            builder.append(openSpans[index].tagPair.close)
        }
        return builder.toString()
    }

    override fun escapeText(s: String): String = Html.escapeText(s)

    override fun escapeAttr(s: String): String = Html.escapeAttr(s)

    override fun warn(warning: HtmlEncodeWarning) {
        warnings += warning
    }

    private fun TextSpan.toEncodedSpan(index: Int): EncodedSpan? {
        if (start >= end) return null
        val tagPair = encodeSpanTag(style)
        if (tagPair.open.isEmpty() && tagPair.close.isEmpty()) return null
        return EncodedSpan(
            start = start,
            end = end,
            index = index,
            tagPair = tagPair,
        )
    }

    private fun encodeSpanTag(style: SpanStyle): HtmlTagPair {
        val primary = profile.spanEncoderFor(style)
        if (primary != null) {
            try {
                return primary.encodeUnchecked(style)
            } catch (throwable: Throwable) {
                warn(encoderExceptionWarning(styleTypeId(style), throwable))
            }
        }
        val fallback = profile.encoderSpanFallback ?: return HtmlTagPair(open = "", close = "")
        return try {
            fallback.encode(style)
        } catch (throwable: Throwable) {
            warn(encoderExceptionWarning(styleTypeId(style), throwable))
            HtmlTagPair(open = "", close = "")
        }
    }

    private fun encodeTextSegment(text: String, preserveNewlines: Boolean): String {
        if (preserveNewlines || '\n' !in text) return escapeText(text)

        val builder = StringBuilder(text.length)
        var segmentStart = 0
        for (index in text.indices) {
            if (text[index] == '\n') {
                builder.append(escapeText(text.substring(segmentStart, index)))
                builder.append("<br>")
                segmentStart = index + 1
            }
        }
        builder.append(escapeText(text.substring(segmentStart)))
        return builder.toString()
    }

    private fun List<EncodedSpan>.sharedPrefixSize(other: List<EncodedSpan>): Int {
        val max = minOf(size, other.size)
        var index = 0
        while (index < max && this[index] == other[index]) {
            index++
        }
        return index
    }

    private data class EncodedSpan(
        val start: Int,
        val end: Int,
        val index: Int,
        val tagPair: HtmlTagPair,
    )
}

@Suppress("UNCHECKED_CAST")
private fun SpanEncoder<*>.encodeUnchecked(style: SpanStyle): HtmlTagPair =
    (this as SpanEncoder<SpanStyle>).encode(style)

internal fun encoderExceptionWarning(
    typeId: String?,
    throwable: Throwable,
): HtmlEncodeWarning.EncoderException =
    HtmlEncodeWarning.EncoderException(
        typeId = typeId,
        message = throwable.message ?: throwable.toString(),
    )

internal fun styleTypeId(style: SpanStyle): String = when (style) {
    SpanStyle.Bold -> "bold"
    SpanStyle.Italic -> "italic"
    SpanStyle.Underline -> "underline"
    SpanStyle.StrikeThrough -> "strike_through"
    SpanStyle.InlineCode -> "inline_code"
    is SpanStyle.Highlight -> "highlight"
    is SpanStyle.Link -> "link"
    is SpanStyle.Custom -> style.typeId
}
