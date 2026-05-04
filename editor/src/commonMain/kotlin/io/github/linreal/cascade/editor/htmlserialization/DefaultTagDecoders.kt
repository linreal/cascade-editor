package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.richtext.LinkUrlPolicy
import io.github.linreal.cascade.editor.richtext.LinkValidationResult

internal object DefaultTagDecoders {

    private val ListItemDecoder: TagDecoder = TagDecoder(::decodeListItem)

    internal val All: Map<String, TagDecoder> = buildMap {
        put("p", textBlockDecoder(BlockType.Paragraph, trimEdges = true, collapseInternalSpaces = true))
        for (level in 1..6) {
            put("h$level", textBlockDecoder(BlockType.Heading(level), trimEdges = true, collapseInternalSpaces = true))
        }
        put("blockquote", textBlockDecoder(BlockType.Quote, trimEdges = true, collapseInternalSpaces = true))
        put("pre", TagDecoder(::decodePre))
        put("ul", TagDecoder { ctx, _, children -> decodeList(ctx, tag = "ul", children = children) })
        put("ol", TagDecoder { ctx, _, children -> decodeList(ctx, tag = "ol", children = children) })
        put("li", ListItemDecoder)
        put("hr", TagDecoder { _, _, _ -> TagDecodeResult.AsBlock(Block.divider()) })
        put("br", TagDecoder(::decodeLineBreak))
        put("strong", inlineStyleDecoder(SpanStyle.Bold))
        put("b", inlineStyleDecoder(SpanStyle.Bold))
        put("em", inlineStyleDecoder(SpanStyle.Italic))
        put("i", inlineStyleDecoder(SpanStyle.Italic))
        put("u", inlineStyleDecoder(SpanStyle.Underline))
        put("s", inlineStyleDecoder(SpanStyle.StrikeThrough))
        put("strike", inlineStyleDecoder(SpanStyle.StrikeThrough))
        put("del", inlineStyleDecoder(SpanStyle.StrikeThrough))
        put("code", TagDecoder(::decodeCode))
        put("a", TagDecoder(::decodeLink))
        put("mark", TagDecoder(::decodeMark))
    }

    private fun textBlockDecoder(
        type: BlockType,
        trimEdges: Boolean,
        collapseInternalSpaces: Boolean,
    ): TagDecoder = TagDecoder { ctx, attrs, children ->
        val inline = ctx.collectInlineText(
            children = children,
            trimEdges = trimEdges,
            collapseInternalSpaces = collapseInternalSpaces,
        )
        val block = textBlock(type = type, inline = inline).withCascadeIndentation(ctx, attrs)
        TagDecodeResult.AsBlock(block)
    }

    private fun decodePre(
        ctx: TagDecodeContext,
        attrs: Map<String, String>,
        children: List<HtmlNodeView>,
    ): TagDecodeResult {
        val text = collectPreText(children).dropSingleTrailingNewline()
        val block = Block(
            id = BlockId.generate(),
            type = BlockType.Code,
            content = BlockContent.Text(text = text, spans = emptyList()),
        ).withCascadeIndentation(ctx, attrs)
        return TagDecodeResult.AsBlock(block)
    }

    /**
     * Decode a whole list subtree directly so nested list depth is carried as an
     * explicit parameter instead of implicit mutable state on the generic walker.
     */
    private fun decodeList(
        ctx: TagDecodeContext,
        tag: String,
        children: List<HtmlNodeView>,
        depth: Int = BlockAttributes.MIN_INDENTATION_LEVEL,
    ): TagDecodeResult =
        TagDecodeResult.AsBlocks(decodeListBlocks(ctx, tag, children, depth))

    private fun decodeListBlocks(
        ctx: TagDecodeContext,
        tag: String,
        children: List<HtmlNodeView>,
        depth: Int,
    ): List<Block> {
        val blocks = mutableListOf<Block>()
        for (child in children) {
            when {
                child is HtmlNodeView.Element && child.tag == "li" -> {
                    blocks += if (ctx.hasCustomListItemDecoder()) {
                        ctx.decodeBlocks(listOf(child))
                    } else {
                        decodeListItemBlocks(ctx, listTag = tag, item = child, depth = depth)
                    }
                }

                child is HtmlNodeView.Element && child.tag.isListTag -> {
                    blocks += decodeListBlocks(ctx, tag = child.tag, children = child.children, depth = depth + 1)
                }

                child is HtmlNodeView.Text && child.text.isBlank() -> Unit

                else -> blocks += ctx.decodeBlocks(listOf(child))
            }
        }
        return blocks
    }

    /**
     * Split a list item into the item's own inline content and nested list children.
     * This keeps `<li>Parent<ul>...</ul></li>` in document order as parent item first,
     * then descendants with one greater indentation level.
     */
    private fun decodeListItemBlocks(
        ctx: TagDecodeContext,
        listTag: String,
        item: HtmlNodeView.Element,
        depth: Int,
    ): List<Block> {
        val ownInlineChildren = mutableListOf<HtmlNodeView>()
        val nestedLists = mutableListOf<HtmlNodeView.Element>()
        for (child in item.children) {
            if (child is HtmlNodeView.Element && child.tag.isListTag) {
                nestedLists += child
            } else {
                ownInlineChildren += child
            }
        }

        val inline = ctx.collectInlineText(
            children = ownInlineChildren,
            trimEdges = true,
            trimSingleTrailingNewline = true,
        )
        // An explicit cascade-indent-N class is authoritative, including N = 0,
        // because exported HTML may preserve editor free/skipped indentation depths.
        val itemDepth = cascadeIndentationFromAttrs(ctx, attrs = item.attrs, tag = "li") ?: depth
        val blocks = mutableListOf(
            textBlock(type = listTypeFor(listTag), inline = inline)
                .withIndentation(itemDepth.coerceInIndentationRange())
        )
        for (nestedList in nestedLists) {
            blocks += decodeListBlocks(
                ctx = ctx,
                tag = nestedList.tag,
                children = nestedList.children,
                depth = itemDepth + 1,
            )
        }
        return blocks
    }

    private fun decodeListItem(
        ctx: TagDecodeContext,
        attrs: Map<String, String>,
        children: List<HtmlNodeView>,
    ): TagDecodeResult {
        val inline = ctx.collectInlineText(
            children = children.filterNot { it is HtmlNodeView.Element && it.tag.isListTag },
            trimEdges = true,
            trimSingleTrailingNewline = true,
        )
        val depth = cascadeIndentationFromAttrs(ctx, attrs = attrs, tag = "li")
            ?: BlockAttributes.MIN_INDENTATION_LEVEL
        val block = textBlock(type = listTypeFor(ctx.parentTag), inline = inline)
            .withIndentation(depth.coerceInIndentationRange())
        return TagDecodeResult.AsBlock(block)
    }

    /**
     * Let dialect profiles replace only the `<li>` decoder while still reusing the
     * default `<ul>` / `<ol>` container decoders. The default profile keeps the
     * direct depth-carrying path so nested HTML lists round-trip without extra state
     * on the public decode context.
     */
    private fun TagDecodeContext.hasCustomListItemDecoder(): Boolean {
        val decoder = tagDecoderFor("li") ?: return false
        return decoder !== ListItemDecoder
    }

    private fun decodeLineBreak(
        ctx: TagDecodeContext,
        attrs: Map<String, String>,
        children: List<HtmlNodeView>,
    ): TagDecodeResult {
        if (!ctx.isBlockContext) return TagDecodeResult.AsText("\n", emptyList())

        ctx.warn(
            HtmlDecodeWarning.DroppedContent(
                reason = "Dropped block-context <br>",
                charOffset = ctx.charOffset,
            )
        )
        return TagDecodeResult.Drop
    }

    private fun decodeCode(
        ctx: TagDecodeContext,
        attrs: Map<String, String>,
        children: List<HtmlNodeView>,
    ): TagDecodeResult {
        if (ctx.parentTag == "pre") {
            // Code blocks do not support rich spans; strip inline formatting at the decode edge.
            return TagDecodeResult.AsText(collectPreText(children), emptyList())
        }
        val inline = ctx.decodeInline(children)
        return TagDecodeResult.AsText(
            text = inline.text,
            spans = inline.spans + spanForWholeText(inline.text, SpanStyle.InlineCode),
        )
    }

    private fun decodeLink(
        ctx: TagDecodeContext,
        attrs: Map<String, String>,
        children: List<HtmlNodeView>,
    ): TagDecodeResult {
        val inline = ctx.decodeInline(children)
        val href = attrs["href"]
        val validation = href?.let(LinkUrlPolicy::validate)
        val normalizedUrl = (validation as? LinkValidationResult.Valid)?.normalizedUrl
        if (normalizedUrl == null) {
            ctx.warn(
                HtmlDecodeWarning.DroppedAttribute(
                    tag = "a",
                    attr = "href",
                    reason = if (href == null) "Missing href" else "Invalid href",
                    charOffset = ctx.charOffset,
                )
            )
            return TagDecodeResult.AsText(inline.text, inline.spans)
        }

        return TagDecodeResult.AsText(
            text = inline.text,
            spans = inline.spans + spanForWholeText(inline.text, SpanStyle.Link(normalizedUrl)),
        )
    }

    private fun decodeMark(
        ctx: TagDecodeContext,
        attrs: Map<String, String>,
        children: List<HtmlNodeView>,
    ): TagDecodeResult {
        val inline = ctx.decodeInline(children)
        val color = attrs["data-cascade-highlight"]?.parseHighlightColorOrNull()
        if (attrs.containsKey("data-cascade-highlight") && color == null) {
            ctx.warn(
                HtmlDecodeWarning.InvalidAttribute(
                    tag = "mark",
                    attr = "data-cascade-highlight",
                    value = attrs.getValue("data-cascade-highlight"),
                    reason = "Expected 8 hex digits",
                    charOffset = ctx.charOffset,
                )
            )
        }
        return TagDecodeResult.AsText(
            text = inline.text,
            spans = inline.spans + spanForWholeText(
                text = inline.text,
                style = SpanStyle.Highlight(color ?: HtmlProfile.DEFAULT_HIGHLIGHT_COLOR_ARGB),
            ),
        )
    }

    private fun inlineStyleDecoder(style: SpanStyle): TagDecoder = TagDecoder { ctx, _, children ->
        val inline = ctx.decodeInline(children)
        TagDecodeResult.AsText(
            text = inline.text,
            spans = inline.spans + spanForWholeText(inline.text, style),
        )
    }

    private fun textBlock(type: BlockType, inline: InlineFragment): Block =
        Block(
            id = BlockId.generate(),
            type = type,
            content = BlockContent.Text(text = inline.text, spans = inline.spans),
        )

    private fun listTypeFor(parentTag: String?): BlockType =
        if (parentTag == "ol") BlockType.NumberedList(number = 1) else BlockType.BulletList

    private fun spanForWholeText(text: String, style: SpanStyle): List<TextSpan> =
        if (text.isEmpty()) emptyList() else listOf(TextSpan(0, text.length, style))

    private fun collectPreText(children: List<HtmlNodeView>): String = buildString {
        appendPreText(children)
    }

    private fun StringBuilder.appendPreText(children: List<HtmlNodeView>) {
        for (child in children) {
            when (child) {
                is HtmlNodeView.Text -> append(child.text)
                is HtmlNodeView.Element -> {
                    if (child.tag == "br") {
                        append('\n')
                    } else {
                        appendPreText(child.children)
                    }
                }
            }
        }
    }

    private fun Block.withCascadeIndentation(
        ctx: TagDecodeContext,
        attrs: Map<String, String>,
    ): Block {
        if (!type.supportsIndentation || type == BlockType.BulletList || type is BlockType.NumberedList) {
            return this
        }
        val parsed = cascadeIndentationFromAttrs(ctx, attrs = attrs, tag = tagNameForWarning(type))
        if (parsed == null) {
            return this
        }
        return withIndentation(parsed)
    }

    private fun Block.withIndentation(level: Int): Block =
        withAttributes(attributes.copy(indentationLevel = level.coerceInIndentationRange()))

    private fun Int.coerceInIndentationRange(): Int =
        coerceIn(BlockAttributes.MIN_INDENTATION_LEVEL, BlockAttributes.MAX_INDENTATION_LEVEL)

    private fun cascadeIndentationFromAttrs(
        ctx: TagDecodeContext,
        attrs: Map<String, String>,
        tag: String,
    ): Int? {
        val classValue = attrs["class"] ?: return null
        val parsed = classValue.cascadeIndentationClass()
        if (parsed == null && classValue.contains(CASCADE_INDENT_CLASS_PREFIX)) {
            ctx.warn(
                HtmlDecodeWarning.InvalidAttribute(
                    tag = tag,
                    attr = "class",
                    value = classValue,
                    reason = "Invalid cascade indentation class",
                    charOffset = ctx.charOffset,
                )
            )
        }
        return parsed
    }

    private fun String.cascadeIndentationClass(): Int? {
        val classes = split(' ', '\t', '\n', '\r', '\u000C')
        for (className in classes) {
            if (!className.startsWith(CASCADE_INDENT_CLASS_PREFIX)) continue
            val level = className.removePrefix(CASCADE_INDENT_CLASS_PREFIX).toIntOrNull() ?: return null
            if (level !in BlockAttributes.MIN_INDENTATION_LEVEL..BlockAttributes.MAX_INDENTATION_LEVEL) return null
            return level
        }
        return null
    }

    private fun String.parseHighlightColorOrNull(): Long? {
        if (length != 8) return null
        var value = 0L
        for (char in this) {
            val digit = char.digitToIntOrNull(radix = 16) ?: return null
            value = (value shl 4) or digit.toLong()
        }
        return value
    }

    private fun String.dropSingleTrailingNewline(): String =
        if (lastOrNull() == '\n') dropLast(1) else this

    private fun tagNameForWarning(type: BlockType): String = when (type) {
        BlockType.Paragraph -> "p"
        is BlockType.Heading -> "h${type.level}"
        BlockType.Quote -> "blockquote"
        BlockType.Code -> "pre"
        BlockType.Divider -> "hr"
        BlockType.BulletList,
        is BlockType.NumberedList -> "li"
        is BlockType.Todo -> "todo"
        else -> type.typeId
    }

    private val String.isListTag: Boolean
        get() = this == "ul" || this == "ol"
}
