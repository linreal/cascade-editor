package io.github.linreal.cascade.editor.htmlserialization

/** Defensive input bounds for HTML decode, preventing OOM on pathological input. */
@ExperimentalCascadeHtmlApi
public data class HtmlDecodeLimits(
    // Maximum input length in UTF-16 code units. Larger inputs are rejected
    val maxInputChars: Int = DEFAULT_MAX_INPUT_CHARS,
) {
    public companion object {
        // ~4M chars: far above any realistic document, still below OOM territory
        public const val DEFAULT_MAX_INPUT_CHARS: Int = 4_000_000

        public val Default: HtmlDecodeLimits = HtmlDecodeLimits()
    }
}
