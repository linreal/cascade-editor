package io.github.linreal.cascade.editor.htmlserialization

/**
 * Warnings emitted while decoding HTML through [HtmlSchema.decodeWithReport].
 *
 * Every warning carries a [charOffset] expressed as a UTF-16 code-unit offset into the
 * original `rawSource` string passed to decode. The offset points at the documented
 * source location for the warned-about node — for tag-level warnings this is the
 * starting `<` of the offending element; for content-level warnings (`DroppedContent`,
 * `BlockInInlineContext`, `DecoderException`) it points at the responsible node's
 * `sourceStart`.
 *
 * This is a sealed class — consumers should include an `else` branch in `when`
 * expressions to handle future warning subclasses gracefully.
 */
public sealed class HtmlDecodeWarning {

    /** UTF-16 code-unit offset into the original input where the warning was produced. */
    public abstract val charOffset: Int

    /** Tag with no registered decoder under [UnknownTagPolicy.WarnAndStrip] / `Preserve` / `Custom`. */
    public data class UnknownTag(
        val tag: String,
        override val charOffset: Int,
    ) : HtmlDecodeWarning()

    /** Attribute the decoder did not recognize and discarded. */
    public data class UnknownAttribute(
        val tag: String,
        val attr: String,
        override val charOffset: Int,
    ) : HtmlDecodeWarning()

    /** Closing tag without a matching open tag in scope. */
    public data class StrayClosingTag(
        val tag: String,
        override val charOffset: Int,
    ) : HtmlDecodeWarning()

    /** Tag nesting was straightened from malformed input. */
    public data class MismatchedNesting(
        val expected: String,
        val found: String,
        override val charOffset: Int,
    ) : HtmlDecodeWarning()

    /** Tag was opened but not closed before end of input or an enclosing close. */
    public data class UnclosedTag(
        val tag: String,
        override val charOffset: Int,
    ) : HtmlDecodeWarning()

    /** Attribute value failed validation and a fallback was used. */
    public data class InvalidAttribute(
        val tag: String,
        val attr: String,
        val value: String,
        val reason: String,
        override val charOffset: Int,
    ) : HtmlDecodeWarning()

    /**
     * A [TagDecodeResult.AsBlock] / `AsBlocks` was returned in inline context.
     *
     * Text content (if any) is flattened into the surrounding inline run; non-text
     * block content is dropped.
     */
    public data class BlockInInlineContext(
        val tag: String,
        override val charOffset: Int,
    ) : HtmlDecodeWarning()

    /** Content was dropped by parser policy or a tag decoder. */
    public data class DroppedContent(
        val reason: String,
        override val charOffset: Int,
    ) : HtmlDecodeWarning()

    /** An attribute was dropped because it could not be expressed in the editor model. */
    public data class DroppedAttribute(
        val tag: String,
        val attr: String,
        val reason: String,
        override val charOffset: Int,
    ) : HtmlDecodeWarning()

    /**
     * A consumer-supplied [TagDecoder] threw. The offending node falls back to
     * [TagDecodeResult.Drop]; ancestors and siblings continue decoding.
     */
    public data class DecoderException(
        val tag: String?,
        val message: String,
        override val charOffset: Int,
    ) : HtmlDecodeWarning()
}

/**
 * Warnings emitted while encoding blocks through [HtmlSchema.encodeWithReport].
 *
 * Encode-side warnings have no character offset (the input is `List<Block>`, not a
 * source string). They surface profile-defined drops (e.g. Wrike's profile dropping
 * paragraph indentation) and consumer-encoder failures.
 *
 * This is a sealed class — consumers should include an `else` branch in `when`
 * expressions to handle future warning subclasses gracefully.
 */
public sealed class HtmlEncodeWarning {

    /**
     * A consumer-supplied [BlockEncoder], [BlockGroupEncoder], or [SpanEncoder] threw,
     * or its registered fallback threw. The offending encoder is treated as if it
     * returned [HtmlEmit.Skip]; if both primary and fallback throw an empty string is
     * emitted for that block / span.
     */
    public data class EncoderException(
        val typeId: String?,
        val message: String,
    ) : HtmlEncodeWarning()

    /**
     * A block attribute (e.g. `indentationLevel`) was dropped because the active
     * profile has no encoding for it.
     */
    public data class DroppedAttribute(
        val typeId: String,
        val attr: String,
        val reason: String,
    ) : HtmlEncodeWarning()
}
