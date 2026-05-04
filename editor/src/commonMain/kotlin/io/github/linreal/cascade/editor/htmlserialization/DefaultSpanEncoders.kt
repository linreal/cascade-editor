package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.SpanStyle

internal object DefaultSpanEncoders {

    internal val Bold: SpanEncoder<SpanStyle.Bold> = SpanEncoder {
        HtmlTagPair(open = "<strong>", close = "</strong>")
    }

    internal val Italic: SpanEncoder<SpanStyle.Italic> = SpanEncoder {
        HtmlTagPair(open = "<em>", close = "</em>")
    }

    internal val Underline: SpanEncoder<SpanStyle.Underline> = SpanEncoder {
        HtmlTagPair(open = "<u>", close = "</u>")
    }

    internal val StrikeThrough: SpanEncoder<SpanStyle.StrikeThrough> = SpanEncoder {
        HtmlTagPair(open = "<s>", close = "</s>")
    }

    internal val InlineCode: SpanEncoder<SpanStyle.InlineCode> = SpanEncoder {
        HtmlTagPair(open = "<code>", close = "</code>")
    }

    internal val Link: SpanEncoder<SpanStyle.Link> = SpanEncoder { style ->
        HtmlTagPair(
            open = """<a href="${Html.escapeAttr(style.url)}" rel="noreferrer">""",
            close = "</a>",
        )
    }

    internal val Highlight: SpanEncoder<SpanStyle.Highlight> = SpanEncoder { style ->
        HtmlTagPair(
            open = """<mark data-cascade-highlight="${style.colorArgb.toEightDigitUpperHex()}">""",
            close = "</mark>",
        )
    }

    /**
     * HTML highlight output is a stable AARRGGBB attribute. `colorArgb` is stored as
     * `Long`, so constrain it to the low 32 bits before formatting.
     */
    private fun Long.toEightDigitUpperHex(): String {
        val lowerArgbBits = this and 0xFFFF_FFFFL
        return lowerArgbBits.toString(radix = 16).padStart(length = 8, padChar = '0').uppercase()
    }
}
