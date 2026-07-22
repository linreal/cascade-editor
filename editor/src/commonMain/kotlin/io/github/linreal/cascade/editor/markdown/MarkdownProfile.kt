package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.CustomBlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import kotlin.reflect.KClass

/**
 * Configuration bundle that drives both encode and decode for the Markdown
 * codec.
 *
 * `MarkdownProfile` is **immutable**: every `with*` / `without*` method returns
 * a new profile instance and never mutates the receiver.
 *
 * Decode grammar is supplied by the internal JetBrains Markdown dependency;
 * profiles configure Cascade-owned policies and the canonical writer. Encoder
 * registration (class-keyed plus custom-typeId-keyed) matches the HTML codec's
 * semantics. No registration call mutates [supportSet].
 */
@ExperimentalCascadeMarkdownApi
public class MarkdownProfile internal constructor(
    internal val blockEncoders: Map<KClass<out BlockType>, MarkdownBlockEncoder<*>>,
    internal val customBlockEncoders: Map<String, MarkdownBlockEncoder<*>>,
    internal val spanEncoders: Map<KClass<out SpanStyle>, MarkdownSpanEncoder<*>>,
    internal val customSpanEncoders: Map<String, MarkdownSpanEncoder<SpanStyle.Custom>>,
    internal val blockGroupEncoders: List<NamedMarkdownBlockGroupEncoder>,
    internal val encoderBlockFallback: MarkdownBlockEncoder<BlockType>?,
    internal val encoderSpanFallback: MarkdownSpanEncoder<SpanStyle>?,
    internal val htmlInMarkdown: HtmlInMarkdown,
    internal val unsupportedSyntax: UnsupportedSyntax,
    internal val newlineSemantics: NewlineSemantics,
    internal val softBreak: SoftBreak,
    internal val hardBreakEncode: HardBreakEncode,
    internal val entityDecode: EntityDecode,
    public val supportSet: MarkdownProfileSupportSet,
) {

    // Built-in encoders (class-keyed)

    /**
     * Register [encoder] for the [BlockType] class `T`. Replaces any
     * previously-registered encoder for the same class.
     */
    public inline fun <reified T : BlockType> withMarkdownBlockEncoder(
        encoder: MarkdownBlockEncoder<T>,
    ): MarkdownProfile = withMarkdownBlockEncoderInternal(T::class, encoder)

    /**
     * Register [encoder] for the [SpanStyle] class `T`. Replaces any
     * previously-registered encoder for the same class.
     */
    public inline fun <reified T : SpanStyle> withMarkdownSpanEncoder(
        encoder: MarkdownSpanEncoder<T>,
    ): MarkdownProfile = withMarkdownSpanEncoderInternal(T::class, encoder)

    @PublishedApi
    internal fun withMarkdownBlockEncoderInternal(
        typeClass: KClass<out BlockType>,
        encoder: MarkdownBlockEncoder<*>,
    ): MarkdownProfile = copyWith(blockEncoders = blockEncoders + (typeClass to encoder))

    @PublishedApi
    internal fun withMarkdownSpanEncoderInternal(
        styleClass: KClass<out SpanStyle>,
        encoder: MarkdownSpanEncoder<*>,
    ): MarkdownProfile = copyWith(spanEncoders = spanEncoders + (styleClass to encoder))

    // Custom-typed encoders (typeId-keyed)

    /**
     * Register a block encoder for a custom block type carried by either
     * `block.type.typeId` (when the type implements [CustomBlockType]) or by
     * `BlockContent.Custom.typeId`.
     */
    public fun withCustomMarkdownBlockEncoder(
        typeId: String,
        encoder: MarkdownBlockEncoder<*>,
    ): MarkdownProfile =
        copyWith(customBlockEncoders = customBlockEncoders + (typeId to encoder))

    /** Register a span encoder for a [SpanStyle.Custom] keyed by `typeId`. */
    public fun withCustomMarkdownSpanEncoder(
        typeId: String,
        encoder: MarkdownSpanEncoder<SpanStyle.Custom>,
    ): MarkdownProfile =
        copyWith(customSpanEncoders = customSpanEncoders + (typeId to encoder))

    // Block group encoders

    /**
     * Register a group [encoder] under [name]. Group encoders keep
     * registration order; re-registering a name removes the old registration
     * and appends the new one at the tail.
     */
    public fun withMarkdownBlockGroupEncoder(
        name: String,
        encoder: MarkdownBlockGroupEncoder,
    ): MarkdownProfile {
        val withoutName = blockGroupEncoders.filterNot { it.name == name }
        return copyWith(
            blockGroupEncoders = withoutName + NamedMarkdownBlockGroupEncoder(name, encoder),
        )
    }

    /** Remove the group encoder registered under [name], if any. */
    public fun withoutMarkdownBlockGroupEncoder(name: String): MarkdownProfile =
        copyWith(blockGroupEncoders = blockGroupEncoders.filterNot { it.name == name })

    // Encode fallbacks

    /** Set the block-level encode fallback invoked when no encoder matches. */
    public fun withEncoderBlockFallback(fallback: MarkdownBlockEncoder<BlockType>): MarkdownProfile =
        copyWith(encoderBlockFallback = fallback)

    /** Set the span-level encode fallback invoked when no encoder matches. */
    public fun withEncoderSpanFallback(fallback: MarkdownSpanEncoder<SpanStyle>): MarkdownProfile =
        copyWith(encoderSpanFallback = fallback)

    // Policies

    /** Override the [HtmlInMarkdown] policy. */
    public fun withHtmlInMarkdown(policy: HtmlInMarkdown): MarkdownProfile =
        copyWith(htmlInMarkdown = policy).rebindDefaultDocumentClaim()

    /**
     * When a default-built support set is installed (its analyzer claim is
     * present), rebind it to *this* profile so a policy change
     * (newline/HTML) produces an honest claim resolved from the new policies,
     * rather than a stale claim frozen against the profile that first built it.
     * A custom [withSupportSet] set has no analyzer claim and is left as-is.
     */
    private fun rebindDefaultDocumentClaim(): MarkdownProfile {
        if (supportSet.analyzerDocumentClaim == null) return this
        return copyWith(supportSet = markdownDefaultSupportSet { this })
    }

    /** Override the [UnsupportedSyntax] policy. */
    public fun withUnsupportedSyntax(policy: UnsupportedSyntax): MarkdownProfile =
        copyWith(unsupportedSyntax = policy)

    /** Override the [NewlineSemantics] policy. */
    public fun withNewlineSemantics(policy: NewlineSemantics): MarkdownProfile =
        copyWith(newlineSemantics = policy).rebindDefaultDocumentClaim()

    /** Override the [SoftBreak] policy. */
    public fun withSoftBreak(policy: SoftBreak): MarkdownProfile =
        copyWith(softBreak = policy)

    /** Override the [HardBreakEncode] policy. */
    public fun withHardBreakEncode(policy: HardBreakEncode): MarkdownProfile =
        copyWith(hardBreakEncode = policy)

    /** Override the [EntityDecode] policy. */
    public fun withEntityDecode(policy: EntityDecode): MarkdownProfile =
        copyWith(entityDecode = policy)

    /**
     * Compose the strict, no-HTML opt-out. A strict profile can **never** emit
     * raw HTML through any path. This method:
     *
     * - flips [HtmlInMarkdown] to [HtmlInMarkdown.WarnAndStrip] (embedded HTML
     *   is stripped to inner text on decode, with a `DataLoss` warning);
     * - unregisters the HTML-emitting span encoders (`Underline` → `<u>`,
     *   `Highlight` → `<mark>`), so those styles degrade through the span
     *   fallback instead;
     * - replaces the `md.preservedHtml` and HTML-kind `md.preserved` verbatim
     *   encoders so preserved raw HTML is dropped rather than re-emitted;
     * - installs a no-HTML default support set (Underline/Highlight are not
     *   claimed).
     *
     * ### Security
     *
     * The codec is not a sanitizer. A default profile can round-trip raw
     * `<script>` verbatim; a strict `withoutHtmlBridge()` profile guarantees no
     * raw HTML is emitted through span encoders, verbatim preserved blocks, the
     * `md.preservedHtml` path, or the generic fallback. Hosts that render
     * untrusted Markdown into a WebView should either sanitize at render time or
     * adopt this profile.
     *
     * ### Call order
     *
     * Call this **before** a consumer's final [withSupportSet]: it installs its
     * own strict support set, so a later `withSupportSet` (yours) wins, while a
     * `withSupportSet` placed before this call would be overwritten.
     */
    public fun withoutHtmlBridge(): MarkdownProfile {
        val strict = copyWith(
            htmlInMarkdown = HtmlInMarkdown.WarnAndStrip,
            spanEncoders = spanEncoders -
                SpanStyle.Underline::class -
                SpanStyle.Highlight::class,
            customBlockEncoders = customBlockEncoders +
                (MARKDOWN_PRESERVED_HTML_TYPE_ID to DefaultMarkdownBlockEncoders.StrictDropHtml) +
                (MARKDOWN_PRESERVED_TYPE_ID to DefaultMarkdownBlockEncoders.StrictPreserved),
        )
        // The default claim resolves empty-text / HTML-span support from
        // `strict` at call time (WarnAndStrip ⇒ no HTML spans claimed).
        return strict.copyWith(supportSet = markdownDefaultSupportSet { strict })
    }

    // Support set

    /**
     * Replace the executable round-trip support claim exposed by [supportSet].
     * Registrations never widen the claim implicitly — custom encoder profiles call
     * this explicitly as their final composition step.
     */
    public fun withSupportSet(supportSet: MarkdownProfileSupportSet): MarkdownProfile =
        copyWith(supportSet = supportSet)

    // Internal lookup helpers used by the engines + contract tests.

    /**
     * Resolve the encoder for [block]. Custom block types and custom block
     * contents are looked up by `typeId`; built-in types by class.
     */
    internal fun blockEncoderFor(block: Block): MarkdownBlockEncoder<*>? {
        val type = block.type
        if (type is CustomBlockType) {
            customBlockEncoders[type.typeId]?.let { return it }
        }
        val content = block.content
        if (content is BlockContent.Custom) {
            customBlockEncoders[content.typeId]?.let { return it }
        }
        return blockEncoders[type::class]
    }

    /**
     * Resolve the encoder for [style]. Custom span styles are looked up by
     * `typeId`; built-in styles by class.
     */
    internal fun spanEncoderFor(style: SpanStyle): MarkdownSpanEncoder<*>? {
        if (style is SpanStyle.Custom) return customSpanEncoders[style.typeId]
        return spanEncoders[style::class]
    }

    private fun copyWith(
        blockEncoders: Map<KClass<out BlockType>, MarkdownBlockEncoder<*>> = this.blockEncoders,
        customBlockEncoders: Map<String, MarkdownBlockEncoder<*>> = this.customBlockEncoders,
        spanEncoders: Map<KClass<out SpanStyle>, MarkdownSpanEncoder<*>> = this.spanEncoders,
        customSpanEncoders: Map<String, MarkdownSpanEncoder<SpanStyle.Custom>> = this.customSpanEncoders,
        blockGroupEncoders: List<NamedMarkdownBlockGroupEncoder> = this.blockGroupEncoders,
        encoderBlockFallback: MarkdownBlockEncoder<BlockType>? = this.encoderBlockFallback,
        encoderSpanFallback: MarkdownSpanEncoder<SpanStyle>? = this.encoderSpanFallback,
        htmlInMarkdown: HtmlInMarkdown = this.htmlInMarkdown,
        unsupportedSyntax: UnsupportedSyntax = this.unsupportedSyntax,
        newlineSemantics: NewlineSemantics = this.newlineSemantics,
        softBreak: SoftBreak = this.softBreak,
        hardBreakEncode: HardBreakEncode = this.hardBreakEncode,
        entityDecode: EntityDecode = this.entityDecode,
        supportSet: MarkdownProfileSupportSet = this.supportSet,
    ): MarkdownProfile = MarkdownProfile(
        blockEncoders = blockEncoders,
        customBlockEncoders = customBlockEncoders,
        spanEncoders = spanEncoders,
        customSpanEncoders = customSpanEncoders,
        blockGroupEncoders = blockGroupEncoders,
        encoderBlockFallback = encoderBlockFallback,
        encoderSpanFallback = encoderSpanFallback,
        htmlInMarkdown = htmlInMarkdown,
        unsupportedSyntax = unsupportedSyntax,
        newlineSemantics = newlineSemantics,
        softBreak = softBreak,
        hardBreakEncode = hardBreakEncode,
        entityDecode = entityDecode,
        supportSet = supportSet,
    )

    public companion object {

        /**
         * Default GFM-oriented profile.
         *
         * CommonMark/GFM recognition is provided by JetBrains Markdown. The
         * adapter adds Cascade's conservative preservation recognizers and
         * maps supported AST nodes into editor blocks and spans.
         *
         * Canonical encode defaults: class-keyed encoders
         * for Paragraph/Heading/Quote/Code/Divider, the `listOutline` group
         * encoder claiming `BulletList` + `NumberedList` + `Todo`, a verbatim
         * `md.preserved` custom encoder, and warning-emitting block/span
         * fallbacks.
         *
         * Policy defaults:
         * [HtmlInMarkdown.Bridge] with [HtmlProfile.Default],
         * [UnsupportedSyntax.Preserve], [NewlineSemantics.CommonMark],
         * [SoftBreak.Space], [HardBreakEncode.Backslash],
         * [EntityDecode.Standard].
         */
        public val Default: MarkdownProfile = MarkdownProfile(
            blockEncoders = mapOf(
                BlockType.Paragraph::class to DefaultMarkdownBlockEncoders.Paragraph,
                BlockType.Heading::class to DefaultMarkdownBlockEncoders.Heading,
                BlockType.Quote::class to DefaultMarkdownBlockEncoders.Quote,
                BlockType.Code::class to DefaultMarkdownBlockEncoders.Code,
                BlockType.Divider::class to DefaultMarkdownBlockEncoders.Divider,
            ),
            customBlockEncoders = mapOf(
                MARKDOWN_PRESERVED_TYPE_ID to DefaultMarkdownBlockEncoders.Preserved,
                // Block-level preserved HTML shares the verbatim encoder; a
                // strict `withoutHtmlBridge()` profile replaces both so raw HTML
                // can never be emitted.
                MARKDOWN_PRESERVED_HTML_TYPE_ID to DefaultMarkdownBlockEncoders.Preserved,
            ),
            // HTML span encoders: Underline → <u>, Highlight →
            // <mark data-cascade-highlight="…">. These make Underline/Highlight
            // round-trip against the inline HTML bridge on the default profile.
            spanEncoders = mapOf(
                SpanStyle.Underline::class to MarkdownHtmlSpanEncoders.Underline,
                SpanStyle.Highlight::class to MarkdownHtmlSpanEncoders.Highlight,
            ),
            customSpanEncoders = emptyMap(),
            blockGroupEncoders = listOf(
                NamedMarkdownBlockGroupEncoder("listOutline", DefaultMarkdownListOutlineEncoder),
            ),
            encoderBlockFallback = DefaultMarkdownEncoderFallbacks.Block,
            encoderSpanFallback = DefaultMarkdownEncoderFallbacks.Span,
            htmlInMarkdown = HtmlInMarkdown.Bridge(HtmlProfile.Default),
            unsupportedSyntax = UnsupportedSyntax.Preserve,
            newlineSemantics = NewlineSemantics.CommonMark,
            softBreak = SoftBreak.Space,
            hardBreakEncode = HardBreakEncode.Backslash,
            entityDecode = EntityDecode.Standard,
            // The default claim resolves empty-text-block / HTML-span support
            // from the owning profile at call time and verifies candidates by
            // encoding through it. The provider is lazy, so
            // referencing `Default` here is safe: it is only dereferenced when
            // `supportsDocument` is later called.
            supportSet = markdownDefaultSupportSet { Default },
        )
    }
}

/** Internal carrier pairing a registered [MarkdownBlockGroupEncoder] with its name. */
internal data class NamedMarkdownBlockGroupEncoder(
    val name: String,
    val encoder: MarkdownBlockGroupEncoder,
)
