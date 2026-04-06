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
     * Extension point for custom span styles.
     *
     * @param typeId Identifier for the custom style type
     * @param payload Opaque JSON string. Core layer must not parse or inspect this value.
     *   Serialization layer owns the String <-> JsonElement conversion
     *   and must canonicalize (parse then re-encode) at the persistence boundary.
     */
    @Immutable
    public data class Custom(val typeId: String, val payload: String? = null) : SpanStyle

    public companion object {
        /**
         * Returns a grouping key for [style].
         *
         * Styles that should be treated as equivalent regardless of parameter
         * values (e.g. [Highlight] with any `colorArgb`) return a shared key.
         * All other styles return themselves (data-class equality).
         *
         * Uses an exhaustive `when` on the sealed interface — the compiler will
         * error here when a new [SpanStyle] subclass is added, forcing the
         * developer to decide whether it needs kind-based grouping.
         *
         * Also used by [SpanAlgorithms.mergeOverlapping] for span grouping.
         */
        public fun kindKey(style: SpanStyle): Any = when (style) {
            is Bold -> Bold
            is Italic -> Italic
            is Underline -> Underline
            is StrikeThrough -> StrikeThrough
            is InlineCode -> InlineCode
            is Highlight -> Highlight::class // all Highlights share one key
            is Custom -> style               // different typeId/payload = different keys
        }

        /**
         * Type-based style matching.
         *
         * For parameterized styles like [Highlight], returns `true` when both are
         * the same kind regardless of parameter values (`colorArgb`). This lets the
         * toolbar detect and toggle highlights even when the theme color differs
         * from the color stored in the span data.
         *
         * For all other styles (data objects, [Custom]) falls back to
         * normal equality.
         */
        public fun kindMatches(a: SpanStyle, b: SpanStyle): Boolean =
            kindKey(a) == kindKey(b)
    }
}
