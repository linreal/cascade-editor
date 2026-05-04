package io.github.linreal.cascade.profiles

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.htmlserialization.BlockEncoder
import io.github.linreal.cascade.editor.htmlserialization.BlockGroupEncoder
import io.github.linreal.cascade.editor.htmlserialization.BlockSeparator
import io.github.linreal.cascade.editor.htmlserialization.EntityDecode
import io.github.linreal.cascade.editor.htmlserialization.ExperimentalCascadeHtmlApi
import io.github.linreal.cascade.editor.htmlserialization.Html
import io.github.linreal.cascade.editor.htmlserialization.HtmlEmit
import io.github.linreal.cascade.editor.htmlserialization.HtmlEncodeContext
import io.github.linreal.cascade.editor.htmlserialization.HtmlEncodeWarning
import io.github.linreal.cascade.editor.htmlserialization.HtmlNodeView
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfileSupportSet
import io.github.linreal.cascade.editor.htmlserialization.HtmlTagPair
import io.github.linreal.cascade.editor.htmlserialization.InlineRoot
import io.github.linreal.cascade.editor.htmlserialization.SpanEncoder
import io.github.linreal.cascade.editor.htmlserialization.TagDecodeResult
import io.github.linreal.cascade.editor.htmlserialization.TagDecoder

/**
 * Reference Custom HTML profile kept in the sample app so the editor module remains
 * dialect-neutral.
 */
object CustomHtmlProfile {

    @OptIn(ExperimentalCascadeHtmlApi::class)
    val Profile: HtmlProfile = HtmlProfile.Default
        .withParserPolicy(BlockSeparator.Newline)
        .withParserPolicy(InlineRoot.WrapInParagraph)
        .withParserPolicy(EntityDecode.Standard)
        .withTagDecoder("li", CustomLiDecoder)
        .withTagDecoder("strong", inlineStyleDecoder(SpanStyle.Bold))
        .withTagDecoder("b", inlineStyleDecoder(SpanStyle.Bold))
        .withTagDecoder("em", inlineStyleDecoder(SpanStyle.Italic))
        .withTagDecoder("i", inlineStyleDecoder(SpanStyle.Italic))
        .withTagDecoder("s", inlineStyleDecoder(SpanStyle.StrikeThrough))
        .withTagDecoder("strike", inlineStyleDecoder(SpanStyle.StrikeThrough))
        .withTagDecoder("del", inlineStyleDecoder(SpanStyle.StrikeThrough))
        .withTagDecoder("code", inlineStyleDecoder(SpanStyle.InlineCode))
        .withBlockEncoder<BlockType.Paragraph>(CustomParagraphEncoder)
        .withBlockEncoder<BlockType.Code>(CustomCodeEncoder)
        .withSpanEncoder<SpanStyle.Bold> { HtmlTagPair("<strong>", "</strong>") }
        .withSpanEncoder<SpanStyle.Italic> { HtmlTagPair("<em>", "</em>") }
        .withSpanEncoder<SpanStyle.StrikeThrough> { HtmlTagPair("<s>", "</s>") }
        .withSpanEncoder<SpanStyle.InlineCode> { HtmlTagPair("<code>", "</code>") }
        .withSpanEncoder<SpanStyle.Link> { style ->
            HtmlTagPair(
                open = """<a rel="nofollow noreferrer noopener" target="_blank" href="${Html.escapeAttr(style.url)}">""",
                close = "</a>",
            )
        }

        .withoutBlockGroupEncoder("listOutline")
        .withBlockGroupEncoder(
            name = "customBulletList",
            encoder = CustomFlatListEncoder(
                outerTag = "ul",
                groupKeyValue = "customBulletList",
                matches = { type -> type == BlockType.BulletList },
            ),
        )
        .withBlockGroupEncoder(
            name = "customNumberedList",
            encoder = CustomFlatListEncoder(
                outerTag = "ol",
                groupKeyValue = "customNumberedList",
                matches = { type -> type is BlockType.NumberedList },
            ),
        )
        .withSupportSet(CustomSupportSet)
}

private val CustomSupportSet: HtmlProfileSupportSet = HtmlProfileSupportSet(
    supportsBlockPredicate = ::isCustomSupportedBlock,
    supportsSpanPredicate = ::isCustomSupportedSpan,
)

private val CustomParagraphEncoder: BlockEncoder<BlockType.Paragraph> = BlockEncoder { ctx, block, _ ->
    if (block.attributes.indentationLevel > BlockAttributes.MIN_INDENTATION_LEVEL) {
        ctx.warn(
            HtmlEncodeWarning.DroppedAttribute(
                typeId = block.type.typeId,
                attr = "indentationLevel",
                reason = "Custom HTML only supports indentation on list items",
            )
        )
    }
    val inline = ctx.encodeInline(block)
    HtmlEmit.Raw(if (inline.isEmpty()) "<p></p>" else "$inline\n")
}

private val CustomCodeEncoder: BlockEncoder<BlockType.Code> = BlockEncoder { ctx, block, _ ->
    val text = ctx.encodeTextOnly(block)
    val trailingNewline = if (text.endsWith('\n')) "" else "\n"
    HtmlEmit.Raw("<pre>$text$trailingNewline</pre>\n")
}

private val CustomLiDecoder: TagDecoder = TagDecoder { ctx, attrs, children ->
    val inline = ctx.collectInlineText(
        children = children.filterNot { it.isListContainer() },
        trimEdges = true,
        trimSingleTrailingNewline = true,
    )
    val type = if (ctx.parentTag == "ol") {
        BlockType.NumberedList(number = 1)
    } else {
        BlockType.BulletList
    }
    TagDecodeResult.AsBlock(
        Block(
            id = BlockId.generate(),
            type = type,
            content = BlockContent.Text(text = inline.text, spans = inline.spans),
            attributes = BlockAttributes(indentationLevel = attrs.customIndentationLevel()),
        )
    )
}

/**
 * Encodes one consecutive same-type list run using Custom's flat list shape.
 *
 * Depth is represented as `class="ql-indent-<level>"` on each `<li>`, not as nested
 * `<ul>` / `<ol>` elements.
 */
@OptIn(ExperimentalCascadeHtmlApi::class)
private class CustomFlatListEncoder(
    private val outerTag: String,
    private val groupKeyValue: String,
    private val matches: (BlockType) -> Boolean,
) : BlockGroupEncoder {

    override fun groupKey(block: Block): Any? =
        if (matches(block.type)) groupKeyValue else null

    override fun encodeGroup(ctx: HtmlEncodeContext, blocks: List<Block>): HtmlEmit {
        val items = blocks.joinToString(separator = "") { block ->
            val classAttr = block.attributes.indentationLevel.toCustomClassAttr()
            "<li$classAttr>${ctx.encodeInline(block)}\n</li>"
        }
        return HtmlEmit.Raw("<$outerTag>$items</$outerTag>")
    }
}

private fun isCustomSupportedBlock(block: Block): Boolean {
    val depth = block.attributes.indentationLevel
    if (depth !in BlockAttributes.MIN_INDENTATION_LEVEL..BlockAttributes.MAX_INDENTATION_LEVEL) {
        return false
    }
    return when (val type = block.type) {
        BlockType.Paragraph -> depth == BlockAttributes.MIN_INDENTATION_LEVEL
        BlockType.BulletList -> true
        is BlockType.NumberedList -> type.number >= 1
        BlockType.Code -> depth == BlockAttributes.MIN_INDENTATION_LEVEL
        else -> false
    }
}

private fun isCustomSupportedSpan(style: SpanStyle): Boolean = when (style) {
    SpanStyle.Bold,
    SpanStyle.Italic,
    SpanStyle.StrikeThrough,
    SpanStyle.InlineCode -> true
    is SpanStyle.Link -> true
    SpanStyle.Underline,
    is SpanStyle.Highlight,
    is SpanStyle.Custom -> false
}

/**
 * Extracts the first concrete `ql-indent-<level>` class from a Custom `<li>`.
 *
 * Non-numeric variants are ignored defensively because persisted indentation data is
 * always numeric in the Custom dialect.
 */
private fun Map<String, String>.customIndentationLevel(): Int {
    val classValue = this["class"] ?: return BlockAttributes.MIN_INDENTATION_LEVEL
    val parsed = Regex("""(?:^|\s)ql-indent-(\d+)(?:\s|$)""")
        .find(classValue)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
        ?: return BlockAttributes.MIN_INDENTATION_LEVEL
    return parsed.coerceIn(
        BlockAttributes.MIN_INDENTATION_LEVEL,
        BlockAttributes.MAX_INDENTATION_LEVEL,
    )
}

private fun Int.toCustomClassAttr(): String =
    if (this > BlockAttributes.MIN_INDENTATION_LEVEL) {
        " class=\"ql-indent-$this\""
    } else {
        ""
    }

private fun inlineStyleDecoder(style: SpanStyle): TagDecoder = TagDecoder { ctx, _, children ->
    val inline = ctx.decodeInline(children)
    TagDecodeResult.AsText(
        text = inline.text,
        spans = spanForWholeText(inline.text, style) + inline.spans,
    )
}

private fun spanForWholeText(text: String, style: SpanStyle): List<TextSpan> =
    if (text.isEmpty()) emptyList() else listOf(TextSpan(0, text.length, style))

private fun HtmlNodeView.isListContainer(): Boolean =
    this is HtmlNodeView.Element && (tag == "ul" || tag == "ol")
