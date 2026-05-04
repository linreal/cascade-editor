package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle

internal object DefaultEncoderFallbacks {

    internal val Block: BlockEncoder<BlockType> = BlockEncoder { ctx, block, _ ->
        HtmlEmit.Raw("<p>${ctx.encodeInline(block)}</p>")
    }

    internal val Span: SpanEncoder<SpanStyle> = SpanEncoder {
        HtmlTagPair(open = "", close = "")
    }
}
