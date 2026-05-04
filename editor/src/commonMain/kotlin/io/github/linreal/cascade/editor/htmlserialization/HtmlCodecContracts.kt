package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan

/**
 * Public view of a parsed HTML node passed to [TagDecoder]s.
 *
 * Mirrors the internal `HtmlNode` AST one-to-one but with a stable public surface so
 * the parser internals can evolve. Source-position fields are UTF-16 code-unit offsets
 * into the original `rawSource` string; `rawSource.substring(sourceStart, sourceEndExclusive)`
 * returns the verbatim original slice.
 */
@ExperimentalCascadeHtmlApi
public sealed interface HtmlNodeView {

    /** UTF-16 code-unit offset where this node starts in the original source. */
    public val sourceStart: Int

    /** UTF-16 code-unit offset, half-open, where this node ends in the original source. */
    public val sourceEndExclusive: Int

    /**
     * Element node — a tag with attributes and children.
     *
     * @property tag Lowercased tag name.
     * @property attrs Lowercased attribute names. Values are not lowercased.
     * @property children Children in document order; may include other [Element]s and [Text]s.
     */
    public data class Element(
        val tag: String,
        val attrs: Map<String, String>,
        val children: List<HtmlNodeView>,
        override val sourceStart: Int,
        override val sourceEndExclusive: Int,
    ) : HtmlNodeView

    /**
     * Text node — character data between tags.
     *
     * @property text Post-entity-decode text content (when the active [EntityDecode]
     *   policy is [EntityDecode.Standard]; raw text otherwise).
     * @property sourceStart Original (pre-decode) source start offset.
     * @property sourceEndExclusive Original (pre-decode) source end offset, half-open.
     */
    public data class Text(
        val text: String,
        override val sourceStart: Int,
        override val sourceEndExclusive: Int,
    ) : HtmlNodeView
}

/**
 * Result of a [TagDecoder] invocation. The walker uses this plus current context
 * (block vs inline) to decide what to do with the decoded payload.
 *
 * Returning [AsBlock] / [AsBlocks] in inline context emits
 * [HtmlDecodeWarning.BlockInInlineContext]; text/spans of any [BlockContent.Text]
 * payload are flattened into the surrounding inline run; non-text block payload is
 * dropped.
 */
@ExperimentalCascadeHtmlApi
public sealed interface TagDecodeResult {

    /** A single block produced by the decoder (e.g. a paragraph). */
    public data class AsBlock(val block: Block) : TagDecodeResult

    /** Multiple blocks produced by the decoder (e.g. `<ul>` expanding to many list items). */
    public data class AsBlocks(val blocks: List<Block>) : TagDecodeResult

    /** Inline text plus arbitrary spans (e.g. `<em>foo<strong>bar</strong></em>`). */
    public data class AsText(val text: String, val spans: List<TextSpan>) : TagDecodeResult

    /** Decoder elects to drop this node and its descendants entirely. */
    public data object Drop : TagDecodeResult
}

/**
 * Inline payload returned by helper methods on [TagDecodeContext]. Spans use visible
 * coordinates relative to [text], the same convention used by [BlockContent.Text].
 */
@ExperimentalCascadeHtmlApi
public data class InlineFragment(
    val text: String,
    val spans: List<TextSpan>,
)

/**
 * Single-method functional interface a profile registers for an HTML tag.
 *
 * Decoders receive **already-parsed** child nodes plus a context object that exposes
 * helpers for recursive decoding, inline text collection, raw-source slicing, and
 * warning emission. Decoders must never throw on input — the engine wraps every call
 * in a `try/catch` boundary that surfaces failures as
 * [HtmlDecodeWarning.DecoderException], but well-behaved decoders should not rely on
 * that net.
 */
@ExperimentalCascadeHtmlApi
public fun interface TagDecoder {

    /**
     * @param ctx Context exposing recursive decode and helper methods.
     * @param attrs Lowercased attribute names; values are not lowercased.
     * @param children Already-parsed child nodes in document order.
     */
    public fun decode(
        ctx: TagDecodeContext,
        attrs: Map<String, String>,
        children: List<HtmlNodeView>,
    ): TagDecodeResult
}

/**
 * Context passed to [TagDecoder]s during decode.
 *
 * Concrete subclasses live in `TagDecodeContextImpl.kt`. This abstract surface is
 * the stable public contract — additional helpers can be added
 * later by adding methods on this class without breaking consumers.
 */
@ExperimentalCascadeHtmlApi
public abstract class TagDecodeContext internal constructor() {

    /** True when the decoder is being invoked in block context, false in inline context. */
    public abstract val isBlockContext: Boolean

    /** Lowercased tag name of the immediate parent element, or `null` at document root. */
    public abstract val parentTag: String?

    /** Full original input string passed to decode. Read-only. */
    public abstract val rawSource: String

    /** UTF-16 code-unit offset of the current node's `sourceStart` in [rawSource]. */
    public abstract val charOffset: Int

    /**
     * Recursively decode [children] in inline context, returning text + spans.
     *
     * Use this from decoders whose output is itself an inline span (e.g. `<strong>`),
     * so nested inline content is preserved without each decoder reimplementing the
     * span walker.
     */
    public abstract fun decodeInline(children: List<HtmlNodeView>): InlineFragment

    /**
     * Recursively decode [children] in block context, returning a list of blocks.
     *
     * Use this from decoders whose output contains nested blocks (e.g. `<blockquote>`
     * around `<p>` elements).
     */
    public abstract fun decodeBlocks(children: List<HtmlNodeView>): List<Block>

    /**
     * Collect inline text and spans from [children] with whitespace policy controls.
     *
     * @param trimEdges Trim leading/trailing whitespace from the merged text.
     * @param trimSingleTrailingNewline Drop a single trailing `\n` immediately before
     *   the closing edge (matches `<li>List\n</li>` -> `"List"` and the `<pre>` rule).
     * @param collapseInternalSpaces Collapse runs of internal spaces/tabs to a single
     *   space and normalize isolated tabs to a single space (matches the `<p>` /
     *   heading / `<blockquote>` whitespace rule).
     */
    public abstract fun collectInlineText(
        children: List<HtmlNodeView>,
        trimEdges: Boolean = false,
        trimSingleTrailingNewline: Boolean = false,
        collapseInternalSpaces: Boolean = false,
    ): InlineFragment

    /**
     * Return the verbatim source slice for [node]: equivalent to
     * `rawSource.substring(node.sourceStart, node.sourceEndExclusive)`. Used by
     * [UnknownTagPolicy.Preserve] to capture lossless raw HTML.
     */
    public abstract fun rawSliceOf(node: HtmlNodeView): String

    /**
     * Look up the active decoder registered for [tag], or `null` when no decoder is
     * registered. This gives default and custom decoders an explicit way to branch or
     * delegate based on profile configuration without depending on a concrete context
     * implementation.
     */
    public abstract fun tagDecoderFor(tag: String): TagDecoder?

    /** Record [warning] on the active decode result. */
    public abstract fun warn(warning: HtmlDecodeWarning)
}

/**
 * Context passed to every kind of encoder ([BlockEncoder], [BlockGroupEncoder],
 * [SpanEncoder] fallbacks).
 *
 * Concrete subclasses live in `HtmlEncodeContextImpl.kt`. This abstract surface is
 * the stable public contract.
 *
 * `encodeInline` deliberately takes the full [Block] (not just [BlockContent]) because
 * the newline-rendering rule depends on `block.type`: [BlockType.Code] preserves `\n`
 * verbatim while non-Code text emits `<br>`. Encoders almost always have the block in
 * scope. For exotic cases where an encoder needs to emit inline content not tied to a
 * block, use [encodeInlineFragment].
 */
public abstract class HtmlEncodeContext internal constructor() {

    /**
     * Render a block's content as inline HTML.
     *
     * - [BlockContent.Text] under non-Code [BlockType]: escaped text + spans, with
     *   embedded `\n` rendered as `<br>`.
     * - [BlockContent.Text] under [BlockType.Code]: escaped text + spans, with literal
     *   `\n` preserved (no `<br>`).
     * - [BlockContent.Empty]: empty string.
     * - [BlockContent.Custom]: empty string by default. Consumers wanting different
     *   behavior register a custom-block encoder rather than relying on this path.
     */
    public abstract fun encodeInline(block: Block): String

    /** Render plain text only (escaped, spans ignored). Returns `""` for non-text content. */
    public abstract fun encodeTextOnly(block: Block): String

    /**
     * Render a free-standing inline fragment.
     *
     * @param preserveNewlines When `true`, embedded `\n` is emitted literally;
     *   when `false`, embedded `\n` becomes `<br>`. Caller picks the policy
     *   because there is no surrounding [Block] to consult.
     */
    public abstract fun encodeInlineFragment(
        text: String,
        spans: List<TextSpan>,
        preserveNewlines: Boolean = false,
    ): String

    /** Equivalent to [Html.escapeText]. Provided for symmetry with [escapeAttr]. */
    public abstract fun escapeText(s: String): String

    /** Equivalent to [Html.escapeAttr]. Provided for symmetry with [escapeText]. */
    public abstract fun escapeAttr(s: String): String

    /** Record an encode-side [warning] on the active encode result. */
    public abstract fun warn(warning: HtmlEncodeWarning)
}

/**
 * Encoder for an individual block.
 *
 * Single-abstract-method [fun interface] so consumers can supply a lambda. The
 * generic `T` exists for type safety at the registration call site (see
 * [HtmlProfile.withBlockEncoder]); the engine erases it at lookup time.
 */
@ExperimentalCascadeHtmlApi
public fun interface BlockEncoder<T : BlockType> {

    /**
     * @param ctx Encoding context with inline-render and escape helpers.
     * @param block The block being encoded.
     * @param content The same [block]'s content, re-passed for ergonomic destructuring.
     * @return [HtmlEmit] result; return [HtmlEmit.Skip] to defer to the registered
     *   block fallback.
     */
    public fun encode(ctx: HtmlEncodeContext, block: Block, content: BlockContent): HtmlEmit
}

/**
 * Encoder for an inline span style.
 *
 * Single-abstract-method [fun interface]. The result is an open/close tag pair that
 * brackets the styled text; the engine handles span overlap, escaping, and `<br>`
 * insertion. The generic `T` exists for type safety at the registration call site.
 */
@ExperimentalCascadeHtmlApi
public fun interface SpanEncoder<T : SpanStyle> {

    /** @param style The span style being encoded. */
    public fun encode(style: T): HtmlTagPair
}

/**
 * Encoder for a contiguous run of consecutive blocks that share a *group key*.
 *
 * Used for HTML structures that span multiple editor blocks (consecutive list items
 * wrapping in one `<ul>` / `<ol>`, indentation outlines becoming nested HTML, etc.).
 *
 * The encoder walker iterates `List<Block>` and asks each registered group encoder
 * for a [groupKey]. The first non-null key starts a run; the walker keeps consuming
 * consecutive blocks for which the same encoder returns the same key, then calls
 * [encodeGroup] once for the whole run. While a run is open, that first encoder owns
 * the contiguous run; other group encoders are not consulted mid-run even if they
 * would also claim a later block.
 */
@ExperimentalCascadeHtmlApi
public interface BlockGroupEncoder {

    /**
     * Group identity for [block]. Returning `null` means "not part of any group I
     * handle"; the walker falls through to per-block encoding.
     *
     * Group keys are compared with `==`. Stable, lightweight markers like a constant
     * string or a class reference are recommended.
     */
    public fun groupKey(block: Block): Any?

    /** Emit HTML for a contiguous run of [blocks] sharing the same group key. */
    public fun encodeGroup(ctx: HtmlEncodeContext, blocks: List<Block>): HtmlEmit
}

/**
 * Result of an encoder invocation.
 */
@ExperimentalCascadeHtmlApi
public sealed interface HtmlEmit {

    /** Self-contained emission. Used for self-closing tags like `<hr>` and `<br>`. */
    public data class Raw(val html: String) : HtmlEmit

    /** Skip this encoder; defer to the registered fallback. */
    public data object Skip : HtmlEmit
}

/**
 * Open/close tag pair for a span style. Both halves should be valid HTML fragments;
 * the engine concatenates them around already-escaped text content.
 */
@ExperimentalCascadeHtmlApi
public data class HtmlTagPair(val open: String, val close: String)
