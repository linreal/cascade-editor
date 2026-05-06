package io.github.linreal.cascade.editor.htmlserialization

/**
 * Policy that controls how the parser splits root-level content into blocks.
 *
 * Block separation has two named modes; arbitrary callbacks are intentionally not
 * supported.
 */
@ExperimentalCascadeHtmlApi
public sealed interface BlockSeparator {

    /**
     * Root-level newlines act as separators between adjacent inline runs.
     *
     * - A single root `\n` separates adjacent root inline runs without creating an
     *   empty paragraph.
     * - `N` consecutive root `\n` characters create `N - 1` empty paragraphs between
     *   the surrounding text.
     * - Spaces and tabs around a root separator are ignored.
     * - Under [EntityDecode.Standard], entity references that decode to `\n`, spaces,
     *   or tabs participate in the same separator and edge-trimming rules.
     * - Newlines inside element content are not interpreted by this policy and remain
     *   in the element's child text for the tag decoder to handle.
     */
    public data object Newline : BlockSeparator

    /** Only block-level tags determine block boundaries. Default. */
    public data object BlockTags : BlockSeparator
}

/**
 * Policy that controls what to do with inline content that appears at document root.
 *
 * Inline-at-root cases include things like a leading text run before the first block
 * tag, or a stray `<strong>...</strong>` outside any block-level container.
 */
@ExperimentalCascadeHtmlApi
public sealed interface InlineRoot {

    /** Drop inline content at root with a warning. Default. */
    public data object Drop : InlineRoot

    /** Wrap each contiguous inline run at root in a synthetic `Paragraph` block. */
    public data object WrapInParagraph : InlineRoot
}

/**
 * Policy that controls HTML entity decoding inside text and attribute values.
 */
@ExperimentalCascadeHtmlApi
public sealed interface EntityDecode {

    /**
     * Decode `&amp;`, `&lt;`, `&gt;`, `&quot;`, `&apos;`, decimal numeric entities
     * (`&#65;`), hex numeric entities (`&#x41;`), and a small named entity set
     * (`&nbsp;` and friends; the exact list is specified by the parser implementation).
     * Default.
     */
    public data object Standard : EntityDecode

    /** Pass entity references through to the codec phase verbatim. */
    public data object None : EntityDecode
}

/**
 * Profile-level policy that controls fallback behavior for tags with no registered
 * [TagDecoder].
 *
 * `Preserve` is lossless only for **block-level** unknown elements; in inline context
 * it intentionally behaves like [WarnAndStrip].
 */
@ExperimentalCascadeHtmlApi
public sealed interface UnknownTagPolicy {

    /** Drop the unknown tag, keep child text, no warning. */
    public data object Strip : UnknownTagPolicy

    /** Drop the unknown tag, keep child text, emit [HtmlDecodeWarning.UnknownTag]. */
    public data object WarnAndStrip : UnknownTagPolicy

    /**
     * Preserve block-level unknown elements as raw-HTML custom blocks.
     *
     * The preserved payload is the **raw source slice** of the original input
     * (untouched by lowercasing or entity decoding) carried by an internal
     * `PreservedHtmlBlockType` (a `CustomBlockType` with stable `typeId =
     * "html.preserved"` and payload key `rawHtml`).
     *
     * In inline context this policy intentionally behaves like [WarnAndStrip] — the
     * editor's inline model cannot losslessly store arbitrary opaque inline HTML.
     */
    public data object Preserve : UnknownTagPolicy

    /**
     * Defer to a consumer-supplied handler.
     *
     * The handler receives the unknown element node and the active decode context,
     * and returns a [TagDecodeResult] that the engine then dispatches normally.
     * The handler is invoked through the same `try/catch` boundary as registered
     * decoders, so a thrown exception surfaces as [HtmlDecodeWarning.DecoderException].
     */
    public data class Custom(
        val handler: (HtmlNodeView.Element, TagDecodeContext) -> TagDecodeResult,
    ) : UnknownTagPolicy
}
