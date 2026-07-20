package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.CustomBlockType
import io.github.linreal.cascade.editor.core.SpanStyle

/**
 * Default encode fallbacks. Unlike the HTML codec's silent
 * fallbacks, both emit a `DataLoss` warning — a silent fallback would make
 * `encodeWithReport` and `analyze` unreliable for out-of-support documents.
 */
internal object DefaultMarkdownEncoderFallbacks {

    /**
     * Block fallback: a plain paragraph carrying the block's visible text
     * where possible (custom/empty content may have none), plus an
     * [MarkdownEncodeWarning.UnsupportedBlock] warning. An empty emission is
     * dropped by the engine, never turned into a blank unit.
     */
    internal val Block: MarkdownBlockEncoder<BlockType> =
        MarkdownBlockEncoder { ctx, block, content ->
            ctx.warn(
                MarkdownEncodeWarning.UnsupportedBlock(
                    typeId = block.fallbackTypeId(),
                    reason = "no Markdown encoding produced output for this block; " +
                        "visible text was emitted as a plain paragraph",
                    blockId = block.id,
                ),
            )
            if (content is BlockContent.Text && content.text.isNotEmpty()) {
                MarkdownEmit.Raw(ctx.encodeInlineLines(block).joinToString("\n"))
            } else {
                MarkdownEmit.Raw("")
            }
        }

    /** Span fallback: text kept, marks dropped. */
    internal val Span: MarkdownSpanEncoder<SpanStyle> =
        MarkdownSpanEncoder { MarkdownMarkPair(open = "", close = "") }
}

private fun io.github.linreal.cascade.editor.core.Block.fallbackTypeId(): String {
    val type = this.type
    if (type is CustomBlockType) return type.typeId
    val content = this.content
    if (content is BlockContent.Custom) return content.typeId
    return type.typeId
}
