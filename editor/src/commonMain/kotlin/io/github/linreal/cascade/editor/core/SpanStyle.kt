package io.github.linreal.cascade.editor.core

import androidx.compose.runtime.Immutable

/**
 * Sealed hierarchy of styles that can be applied to text spans.
 */
@Immutable
public sealed interface SpanStyle {

    public data object Bold : SpanStyle

    public data object Italic : SpanStyle

    public data object Underline : SpanStyle

    public data object StrikeThrough : SpanStyle

    public data object InlineCode : SpanStyle

    /**
     * Highlight with a specific color.
     * @param colorArgb ARGB color value as a Long (e.g. 0xFFFFFF00 for yellow)
     */
    @Immutable
    public data class Highlight(val colorArgb: Long) : SpanStyle

    /**
     * Inline hyperlink.
     * @param url The link target URL
     */
    @Immutable
    public data class Link(val url: String) : SpanStyle

    /**
     * Extension point for custom span styles.
     *
     * @param typeId Identifier for the custom style type
     * @param payload Opaque JSON string. Core layer must not parse or inspect this value.
     *   Serialization layer (Task 2) owns the String <-> JsonElement conversion
     *   and must canonicalize (parse then re-encode) at the persistence boundary.
     */
    @Immutable
    public data class Custom(val typeId: String, val payload: String? = null) : SpanStyle
}
