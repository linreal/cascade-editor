package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.isValidIndentationOutline
import io.github.linreal.cascade.editor.core.renumberNumberedLists

/**
 * Executable claim of which blocks, spans, and whole documents an [HtmlProfile]
 * commits to round-tripping through [HtmlSchema.encode] / [HtmlSchema.decode].
 *
 * Predicates are evaluated by **value**, not by class — that's necessary because
 * support can depend on parameterized values such as [BlockType.Heading.level],
 * [BlockType.NumberedList.number], [SpanStyle.Highlight.colorArgb], custom type IDs,
 * and per-block [BlockAttributes.indentationLevel].
 *
 * Round-trip tests can generate documents constrained to a profile's support set;
 * outside the support set, behavior is profile-defined and may include fallback paths
 * or [HtmlEncodeWarning.DroppedAttribute] drops.
 *
 * @param supportsBlock Returns true if [Block] (type + attributes) is in the
 *   support set.
 * @param supportsSpan Returns true if [SpanStyle] is in the support set.
 */
@ExperimentalCascadeHtmlApi
public class HtmlProfileSupportSet(
    public val supportsBlockPredicate: (Block) -> Boolean,
    public val supportsSpanPredicate: (SpanStyle) -> Boolean,
) {


    /** True when [block]'s type and attributes are in the profile's support set. */
    public fun supportsBlock(block: Block): Boolean = supportsBlockPredicate(block)

    /** True when [style] is in the profile's support set. */
    public fun supportsSpan(style: SpanStyle): Boolean = supportsSpanPredicate(style)

    /**
     * True when [blocks] is a complete editor-normalized document inside the profile's
     * support set.
     *
     * Beyond per-block and per-span checks this also rejects:
     *
     * - Stale [BlockType.NumberedList] numbering — the document must already match
     *   what `renumberNumberedLists()` would produce. Arbitrary stale numbers are out
     *   of support even when the block type is supported, because round-trip would
     *   silently rewrite the numbers and the equality assertion would fail.
     * - Non-normalized indentation outlines — depths must lie in the supported range
     *   and unsupported block types must sit at depth `0`. This is the parent/child
     *   invariant enforced by the post-decode normalization step (see
     *   `ARCHITECTURE.md`).
     */
    public fun supportsDocument(blocks: List<Block>): Boolean {
        if (!blocks.isValidIndentationOutline()) return false
        if (renumberNumberedLists(blocks) !== blocks) return false

        for (block in blocks) {
            if (!supportsBlockPredicate(block)) return false
            val content = block.content
            if (!block.hasSupportedContentShape()) return false
            if (content is BlockContent.Text && block.type.supportsSpans) {
                for (span in content.spans) {
                    if (!supportsSpanPredicate(span.style)) return false
                }
            }
        }

        return true
    }

    public companion object {
        /**
         * Default-profile support set:
         *
         * - Built-in block types: `Paragraph`, `Heading(1..6)`, `BulletList`,
         *   `NumberedList`, `Code`, `Quote`, `Divider`. `Todo` is excluded because
         *   the default profile registers no canonical HTML mapping for it.
         * - Indentation `0..MAX_INDENTATION_LEVEL` on the subset of supported block
         *   types that the editor's capability matrix allows to indent. Other
         *   supported types must sit at depth `0`.
         * - Built-in span styles including [SpanStyle.Highlight] with any
         *   `colorArgb` and [SpanStyle.Link] with any URL. [SpanStyle.Custom] is
         *   excluded.
         */
        public val Default: HtmlProfileSupportSet = HtmlProfileSupportSet(
            supportsBlockPredicate = ::isDefaultProfileSupportedBlock,
            supportsSpanPredicate = ::isDefaultProfileSupportedSpan,
        )

        private fun isDefaultProfileSupportedBlock(block: Block): Boolean {
            val depth = block.attributes.indentationLevel
            if (depth < BlockAttributes.MIN_INDENTATION_LEVEL ||
                depth > BlockAttributes.MAX_INDENTATION_LEVEL
            ) {
                return false
            }
            return when (val type = block.type) {
                BlockType.Paragraph -> true
                is BlockType.Heading -> depth == BlockAttributes.MIN_INDENTATION_LEVEL && type.level in 1..6
                BlockType.BulletList -> true
                is BlockType.NumberedList -> type.number >= 1
                BlockType.Code -> depth == BlockAttributes.MIN_INDENTATION_LEVEL
                BlockType.Quote -> depth == BlockAttributes.MIN_INDENTATION_LEVEL
                BlockType.Divider -> depth == BlockAttributes.MIN_INDENTATION_LEVEL && block.content is BlockContent.Empty
                is BlockType.Todo -> false
                else -> false
            }
        }

        private fun isDefaultProfileSupportedSpan(style: SpanStyle): Boolean = when (style) {
            SpanStyle.Bold,
            SpanStyle.Italic,
            SpanStyle.Underline,
            SpanStyle.StrikeThrough,
            SpanStyle.InlineCode -> true
            is SpanStyle.Highlight -> true
            is SpanStyle.Link -> true
            is SpanStyle.Custom -> false
        }
    }
}

/**
 * Rejects block/content combinations that the support-set round-trip contract cannot
 * preserve even when the block type itself is supported.
 */
private fun Block.hasSupportedContentShape(): Boolean = when (val blockContent = content) {
    is BlockContent.Text -> {
        type.supportsText &&
            (type.supportsSpans || blockContent.spans.isEmpty())
    }

    BlockContent.Empty -> !type.supportsText
    is BlockContent.Custom -> false
}
