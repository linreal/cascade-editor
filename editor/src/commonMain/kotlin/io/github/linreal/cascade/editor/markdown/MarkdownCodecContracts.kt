package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle

/** Result of a Markdown encoder invocation. */
@ExperimentalCascadeMarkdownApi
public sealed interface MarkdownEmit {
    /** Content-only emission of one canonical unit. */
    public data class Raw(val markdown: String) : MarkdownEmit

    /** Character-exact emission of a preserved source unit. */
    public data class Verbatim(val markdown: String) : MarkdownEmit

    /** Skip this encoder; defer to the registered fallback. */
    public data object Skip : MarkdownEmit
}

/** Open/close marker pair produced by a span encoder. */
@ExperimentalCascadeMarkdownApi
public data class MarkdownMarkPair(val open: String, val close: String)

/** Context passed to Markdown block encoders. */
@ExperimentalCascadeMarkdownApi
public abstract class MarkdownEncodeContext internal constructor() {
    /** Render a block's content as escaped inline Markdown, split by physical line. */
    public abstract fun encodeInlineLines(block: Block): List<String>

    /** Render escaped visible text with spans ignored. */
    public abstract fun encodeTextOnly(block: Block): String

    /** Apply first/continuation prefixes to every physical line. */
    public abstract fun prefixLines(
        lines: List<String>,
        firstLinePrefix: String,
        continuationPrefix: String,
    ): List<String>

    /** Record an encode-side warning. */
    public abstract fun warn(warning: MarkdownEncodeWarning)

    internal abstract val newlineSemantics: NewlineSemantics
    internal abstract val hardBreakMarker: String?
}

/** Encoder for an individual block. */
@ExperimentalCascadeMarkdownApi
public fun interface MarkdownBlockEncoder<T : BlockType> {
    public fun encode(ctx: MarkdownEncodeContext, block: Block, content: BlockContent): MarkdownEmit
}

/** Encoder for one inline span style. */
@ExperimentalCascadeMarkdownApi
public fun interface MarkdownSpanEncoder<T : SpanStyle> {
    public fun encode(style: T): MarkdownMarkPair
}

/** Encoder for a contiguous run of blocks sharing a group key. */
@ExperimentalCascadeMarkdownApi
public interface MarkdownBlockGroupEncoder {
    public fun groupKey(block: Block): Any?
    public fun encodeGroup(ctx: MarkdownEncodeContext, blocks: List<Block>): MarkdownEmit
}
