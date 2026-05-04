package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockType

internal object DefaultBlockEncoders {

    internal val Paragraph: BlockEncoder<BlockType.Paragraph> = BlockEncoder { ctx, block, _ ->
        HtmlEmit.Raw("${openTagWithCascadeIndentation("p", block)}${ctx.encodeInline(block)}</p>")
    }

    internal val Heading: BlockEncoder<BlockType.Heading> = BlockEncoder { ctx, block, _ ->
        val level = (block.type as BlockType.Heading).level
        HtmlEmit.Raw("<h$level>${ctx.encodeInline(block)}</h$level>")
    }

    internal val Quote: BlockEncoder<BlockType.Quote> = BlockEncoder { ctx, block, _ ->
        HtmlEmit.Raw("<blockquote>${ctx.encodeInline(block)}</blockquote>")
    }

    internal val Code: BlockEncoder<BlockType.Code> = BlockEncoder { ctx, block, _ ->
        HtmlEmit.Raw("<pre><code>${ctx.encodeTextOnly(block)}</code></pre>")
    }

    internal val Divider: BlockEncoder<BlockType.Divider> = BlockEncoder { _, _, _ ->
        HtmlEmit.Raw("<hr>")
    }
}

/**
 * Adds the default profile's non-list indentation marker to block tags that can
 * legally carry editor indentation. The class is omitted at depth zero so canonical
 * HTML stays clean for ordinary documents.
 *
 * [tagName] must be a valid tag name selected by the caller's encoder.
 */
@ExperimentalCascadeHtmlApi
public fun openTagWithCascadeIndentation(
    tagName: String,
    block: Block,
): String {
    if (!block.type.supportsIndentation ||
        block.attributes.indentationLevel == BlockAttributes.MIN_INDENTATION_LEVEL
    ) {
        return "<$tagName>"
    }

    val className = "$CASCADE_INDENT_CLASS_PREFIX${block.attributes.indentationLevel}"
    return """<$tagName class="$className">"""
}
