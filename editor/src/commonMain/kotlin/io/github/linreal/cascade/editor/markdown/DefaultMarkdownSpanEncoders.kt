package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.SpanStyle

/**
 * Default span-emission resolution used by [MarkdownInlineRenderer]. Per span
 * style, in precedence order:
 *
 * 1. `SpanStyle.Link` → native `[text](target)` inline form (angle destination
 *    when needed), always — reference style is canonicalized away;
 * 2. an explicit [MarkdownSpanEncoder] registration
 *    ([MarkdownProfile.withMarkdownSpanEncoder] /
 *    `withCustomMarkdownSpanEncoder`) → its [MarkdownMarkPair], emitted
 *    without encode-side verification (consumer responsibility; the HTML
 *    bridge encoders land here);
 * 3. a built-in Bold/Italic/Strike mapping → canonical marker emission;
 * 4. `SpanStyle.InlineCode` → backtick fences via
 *    [Markdown.codeSpanDelimiters];
 * 5. otherwise → the registered span fallback (text kept, marks dropped) plus
 *    a [MarkdownEncodeWarning.UnsupportedSpan] warning.
 *
 * Decode grammar is fixed to CommonMark/GFM; custom span encoders remain
 * available through explicit [MarkdownMarkPair] registrations.
 */
internal object DefaultMarkdownSpanEncoders {

    /**
     * Canonical CommonMark/GFM marker for a built-in style.
     */
    fun canonicalMarkerFor(@Suppress("UNUSED_PARAMETER") profile: MarkdownProfile, style: SpanStyle): String? =
        when (style) {
            SpanStyle.Bold -> "**"
            SpanStyle.Italic -> "*"
            SpanStyle.StrikeThrough -> "~~"
            else -> null
        }

    /**
     * An alternate marker for [style] using a different delimiter character
     * (the `*` → `_` italic auto-switch), or null.
     */
    fun alternateMarkerFor(
        @Suppress("UNUSED_PARAMETER") profile: MarkdownProfile,
        style: SpanStyle,
        excludeChar: Char,
    ): String? = when (style) {
        SpanStyle.Bold -> "__".takeIf { excludeChar != '_' }
        SpanStyle.Italic -> "_".takeIf { excludeChar != '_' }
        else -> null
    }

    /** The style a matched pair of [marker] decodes to, per the profile table. */
    fun decodedStyleFor(@Suppress("UNUSED_PARAMETER") profile: MarkdownProfile, marker: String): SpanStyle? =
        when (marker) {
            "**", "__" -> SpanStyle.Bold
            "*", "_" -> SpanStyle.Italic
            "~~" -> SpanStyle.StrikeThrough
            else -> null
        }

    /** Native inline-link mark pair: `[` … `](target)`. */
    fun linkMarkPair(url: String): MarkdownMarkPair =
        MarkdownMarkPair(open = "[", close = "](" + Markdown.escapeLinkDestination(url) + ")")

}
