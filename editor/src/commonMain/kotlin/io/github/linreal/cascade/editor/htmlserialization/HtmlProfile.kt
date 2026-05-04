package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.CustomBlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import kotlin.reflect.KClass

/**
 * Configuration bundle that drives both encode and decode for [HtmlSchema].
 *
 * `HtmlProfile` is **immutable**: every `with*` / `without*` method returns a new
 * profile instance and never mutates the receiver. Profiles are typically composed
 * starting from [Default]:
 *
 * ```
 * val customProfile = HtmlProfile.Default
 *     .withTagDecoder("li", myLiDecoder)
 *     .withParserPolicy(BlockSeparator.Newline)
 *     .withUnknownTagPolicy(UnknownTagPolicy.Strip)
 * ```
 *
 * Registration is **replacement**, not chaining: registering a [TagDecoder] for the
 * same tag, or a block / span encoder for the same key, replaces the previous
 * registration. Consumers who want to extend a built-in mapping can compose their own
 * decoder that delegates to the previous one via [tagDecoderFor].
 *
 * Built-in default decode mappings, canonical encode mappings, and encode fallbacks
 * are registered on [Default]. The [supportSet] reflects the documented
 * default-profile round-trip support claim.
 *
 * Parser policy fields are consumed by the internal parser path before codec
 * dispatch, so decoders receive a profile-shaped root node list rather than raw
 * tokenizer output. Tag decoder registrations and [UnknownTagPolicy] are consumed
 * by the decode engine after that parser step.
 */
@ExperimentalCascadeHtmlApi
public class HtmlProfile internal constructor(
    internal val tagDecoders: Map<String, TagDecoder>,
    internal val blockEncoders: Map<KClass<out BlockType>, BlockEncoder<*>>,
    internal val customBlockEncoders: Map<String, BlockEncoder<*>>,
    internal val spanEncoders: Map<KClass<out SpanStyle>, SpanEncoder<*>>,
    internal val customSpanEncoders: Map<String, SpanEncoder<SpanStyle.Custom>>,
    internal val blockGroupEncoders: List<NamedBlockGroupEncoder>,
    internal val unknownTagPolicy: UnknownTagPolicy,
    internal val blockSeparator: BlockSeparator,
    internal val inlineRoot: InlineRoot,
    internal val entityDecode: EntityDecode,
    internal val encoderBlockFallback: BlockEncoder<BlockType>?,
    internal val encoderSpanFallback: SpanEncoder<SpanStyle>?,
    public val supportSet: HtmlProfileSupportSet,
) {

    // Tag decoders

    /**
     * Register [decoder] for tag [tag]. Tag names are matched case-insensitively;
     * the lookup key is `tag.lowercase()`.
     *
     * Replaces any previously-registered decoder for the same tag.
     */
    public fun withTagDecoder(tag: String, decoder: TagDecoder): HtmlProfile =
        copyWith(tagDecoders = tagDecoders + (tag.lowercase() to decoder))

    /** Remove the registered decoder for [tag], if any. */
    public fun withoutTagDecoder(tag: String): HtmlProfile =
        copyWith(tagDecoders = tagDecoders - tag.lowercase())

    /**
     * Look up the registered decoder for [tag], or `null` if none is registered.
     *
     * Useful as an escape hatch for consumer decoders that want to delegate to the
     * previously-registered decoder (e.g. the built-in default) before applying their
     * own override.
     */
    public fun tagDecoderFor(tag: String): TagDecoder? = tagDecoders[tag.lowercase()]

    // Built-in encoders

    /**
     * Register [encoder] for the [BlockType] class `T`.
     *
     * Multiple [BlockType.Heading] levels share one encoder slot keyed on
     * `BlockType.Heading::class`; the encoder discriminates on `block.type.level`
     * inside.
     *
     * Replaces any previously-registered encoder for the same block type class.
     */
    public inline fun <reified T : BlockType> withBlockEncoder(encoder: BlockEncoder<T>): HtmlProfile =
        withBlockEncoderInternal(T::class, encoder)

    /**
     * Register [encoder] for the [SpanStyle] class `T`.
     *
     * All [SpanStyle.Highlight] colors share one encoder slot keyed on
     * `SpanStyle.Highlight::class`; all [SpanStyle.Link] URLs share one encoder slot.
     *
     * Replaces any previously-registered encoder for the same span style class.
     */
    public inline fun <reified T : SpanStyle> withSpanEncoder(encoder: SpanEncoder<T>): HtmlProfile =
        withSpanEncoderInternal(T::class, encoder)

    @PublishedApi
    internal fun withBlockEncoderInternal(
        typeClass: KClass<out BlockType>,
        encoder: BlockEncoder<*>,
    ): HtmlProfile = copyWith(blockEncoders = blockEncoders + (typeClass to encoder))

    @PublishedApi
    internal fun withSpanEncoderInternal(
        styleClass: KClass<out SpanStyle>,
        encoder: SpanEncoder<*>,
    ): HtmlProfile = copyWith(spanEncoders = spanEncoders + (styleClass to encoder))

    // Block group encoders

    /**
     * Register [encoder] under [name] (used for later removal).
     *
     * Group encoders are kept in **registration order** — the engine queries them in
     * order when looking for a group key match, so earlier registrations take
     * precedence over later ones. Re-registering under the same [name] removes the
     * old registration and appends the new one to the tail; consumers needing the
     * old precedence should remove and re-register all encoders.
     */
    public fun withBlockGroupEncoder(name: String, encoder: BlockGroupEncoder): HtmlProfile {
        val withoutName = blockGroupEncoders.filterNot { it.name == name }
        return copyWith(blockGroupEncoders = withoutName + NamedBlockGroupEncoder(name, encoder))
    }

    /** Remove the registered group encoder named [name], if any. */
    public fun withoutBlockGroupEncoder(name: String): HtmlProfile =
        copyWith(blockGroupEncoders = blockGroupEncoders.filterNot { it.name == name })

    // Custom-typed encoders

    /**
     * Register a block encoder for a custom block type carried by either
     * `block.type.typeId` (when `block.type` implements [CustomBlockType]) or by
     * `BlockContent.Custom.typeId` (when the block uses opaque custom content).
     */
    public fun withCustomBlockEncoder(typeId: String, encoder: BlockEncoder<*>): HtmlProfile =
        copyWith(customBlockEncoders = customBlockEncoders + (typeId to encoder))

    /** Register a span encoder for a [SpanStyle.Custom] keyed by `typeId`. */
    public fun withCustomSpanEncoder(
        typeId: String,
        encoder: SpanEncoder<SpanStyle.Custom>,
    ): HtmlProfile =
        copyWith(customSpanEncoders = customSpanEncoders + (typeId to encoder))

    // Parser policies

    /** Override the [BlockSeparator] policy. */
    public fun withParserPolicy(policy: BlockSeparator): HtmlProfile =
        copyWith(blockSeparator = policy)

    /** Override the [InlineRoot] policy. */
    public fun withParserPolicy(policy: InlineRoot): HtmlProfile =
        copyWith(inlineRoot = policy)

    /** Override the [EntityDecode] policy. */
    public fun withParserPolicy(policy: EntityDecode): HtmlProfile =
        copyWith(entityDecode = policy)

    // Unknown / fallback

    /** Override the [UnknownTagPolicy] applied when no [TagDecoder] is registered. */
    public fun withUnknownTagPolicy(policy: UnknownTagPolicy): HtmlProfile =
        copyWith(unknownTagPolicy = policy)

    /** Set the block-level encode fallback invoked when no encoder matches. */
    public fun withEncoderBlockFallback(fallback: BlockEncoder<BlockType>): HtmlProfile =
        copyWith(encoderBlockFallback = fallback)

    /** Set the span-level encode fallback invoked when no encoder matches. */
    public fun withEncoderSpanFallback(fallback: SpanEncoder<SpanStyle>): HtmlProfile =
        copyWith(encoderSpanFallback = fallback)

    /**
     * Replace the executable round-trip support claim exposed by [supportSet].
     *
     * Use this when composing a dialect profile whose supported block attributes or
     * span styles differ from [Default], while reusing the same encode/decode
     * registration surface.
     */
    public fun withSupportSet(supportSet: HtmlProfileSupportSet): HtmlProfile =
        copyWith(supportSet = supportSet)

    // Internal lookup helpers used by the engine + contract tests.

    /**
     * Resolve the encoder for [block]. Custom block types and custom block contents
     * are looked up by their `typeId`; built-in types are looked up by class.
     *
     * Returns `null` when no encoder is registered for the block.
     */
    internal fun blockEncoderFor(block: Block): BlockEncoder<*>? {
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
     * Resolve the encoder for [style]. Custom span styles are looked up by `typeId`;
     * built-in styles are looked up by class.
     *
     * Returns `null` when no encoder is registered for the style.
     */
    internal fun spanEncoderFor(style: SpanStyle): SpanEncoder<*>? {
        if (style is SpanStyle.Custom) return customSpanEncoders[style.typeId]
        return spanEncoders[style::class]
    }

    private fun copyWith(
        tagDecoders: Map<String, TagDecoder> = this.tagDecoders,
        blockEncoders: Map<KClass<out BlockType>, BlockEncoder<*>> = this.blockEncoders,
        customBlockEncoders: Map<String, BlockEncoder<*>> = this.customBlockEncoders,
        spanEncoders: Map<KClass<out SpanStyle>, SpanEncoder<*>> = this.spanEncoders,
        customSpanEncoders: Map<String, SpanEncoder<SpanStyle.Custom>> = this.customSpanEncoders,
        blockGroupEncoders: List<NamedBlockGroupEncoder> = this.blockGroupEncoders,
        unknownTagPolicy: UnknownTagPolicy = this.unknownTagPolicy,
        blockSeparator: BlockSeparator = this.blockSeparator,
        inlineRoot: InlineRoot = this.inlineRoot,
        entityDecode: EntityDecode = this.entityDecode,
        encoderBlockFallback: BlockEncoder<BlockType>? = this.encoderBlockFallback,
        encoderSpanFallback: SpanEncoder<SpanStyle>? = this.encoderSpanFallback,
        supportSet: HtmlProfileSupportSet = this.supportSet,
    ): HtmlProfile = HtmlProfile(
        tagDecoders = tagDecoders,
        blockEncoders = blockEncoders,
        customBlockEncoders = customBlockEncoders,
        spanEncoders = spanEncoders,
        customSpanEncoders = customSpanEncoders,
        blockGroupEncoders = blockGroupEncoders,
        unknownTagPolicy = unknownTagPolicy,
        blockSeparator = blockSeparator,
        inlineRoot = inlineRoot,
        entityDecode = entityDecode,
        encoderBlockFallback = encoderBlockFallback,
        encoderSpanFallback = encoderSpanFallback,
        supportSet = supportSet,
    )

    public companion object {
        /** Default ARGB color used for `<mark>` tags without a valid explicit color. */
        public const val DEFAULT_HIGHLIGHT_COLOR_ARGB: Long = 0xFFFF_FF00L

        /**
         * Default HTML profile.
         *
         * Default policy settings:
         *
         * - [UnknownTagPolicy.WarnAndStrip]
         * - [BlockSeparator.BlockTags]
         * - [InlineRoot.Drop]
         * - [EntityDecode.Standard]
         *
         * Built-in tag decoders, canonical encode mappings, and encode fallbacks are
         * registered here. The [supportSet] matches the documented default-profile
         * support claim ([HtmlProfileSupportSet.Default]).
         */
        public val Default: HtmlProfile = HtmlProfile(
            tagDecoders = DefaultTagDecoders.All,
            blockEncoders = mapOf(
                BlockType.Paragraph::class to DefaultBlockEncoders.Paragraph,
                BlockType.Heading::class to DefaultBlockEncoders.Heading,
                BlockType.Quote::class to DefaultBlockEncoders.Quote,
                BlockType.Code::class to DefaultBlockEncoders.Code,
                BlockType.Divider::class to DefaultBlockEncoders.Divider,
            ),
            customBlockEncoders = emptyMap(),
            spanEncoders = mapOf(
                SpanStyle.Bold::class to DefaultSpanEncoders.Bold,
                SpanStyle.Italic::class to DefaultSpanEncoders.Italic,
                SpanStyle.Underline::class to DefaultSpanEncoders.Underline,
                SpanStyle.StrikeThrough::class to DefaultSpanEncoders.StrikeThrough,
                SpanStyle.InlineCode::class to DefaultSpanEncoders.InlineCode,
                SpanStyle.Link::class to DefaultSpanEncoders.Link,
                SpanStyle.Highlight::class to DefaultSpanEncoders.Highlight,
            ),
            customSpanEncoders = emptyMap(),
            blockGroupEncoders = listOf(NamedBlockGroupEncoder("listOutline", DefaultListOutlineEncoder)),
            unknownTagPolicy = UnknownTagPolicy.WarnAndStrip,
            blockSeparator = BlockSeparator.BlockTags,
            inlineRoot = InlineRoot.Drop,
            entityDecode = EntityDecode.Standard,
            encoderBlockFallback = DefaultEncoderFallbacks.Block,
            encoderSpanFallback = DefaultEncoderFallbacks.Span,
            supportSet = HtmlProfileSupportSet.Default,
        )
    }
}

/** Internal carrier pairing a registered [BlockGroupEncoder] with its removal name. */
internal data class NamedBlockGroupEncoder(
    val name: String,
    val encoder: BlockGroupEncoder,
)
