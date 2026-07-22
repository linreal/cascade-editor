package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle

/**
 * Executable claim of which blocks, spans, and whole documents a
 * [MarkdownProfile] commits to round-tripping through encode/decode.
 *
 * Public predicates remain replaceable so a custom profile can narrow the
 * advertised surface. Fidelity analysis additionally verifies every claim by
 * encoding and decoding the canonical output; a predicate can therefore narrow
 * native-edit support, but cannot falsely widen it.
 * [supportsBlockPredicate] receives the whole block, so custom support sets can
 * still claim custom types or content explicitly.
 */
@ExperimentalCascadeMarkdownApi
public class MarkdownProfileSupportSet(
    public val supportsBlockPredicate: (Block) -> Boolean,
    public val supportsSpanPredicate: (SpanStyle) -> Boolean,
    public val supportsDocumentPredicate: (List<Block>) -> Boolean,
) {

    /** True when [block]'s type, attributes, and content are in the support set. */
    public fun supportsBlock(block: Block): Boolean = supportsBlockPredicate(block)

    /** True when [style] is in the profile's support set. */
    public fun supportsSpan(style: SpanStyle): Boolean = supportsSpanPredicate(style)

    /** True when the whole document is inside the profile's round-trip claim. */
    public fun supportsDocument(blocks: List<Block>): Boolean = supportsDocumentPredicate(blocks)

    /**
     * Cheap claim used by `analyze` for the default set. The analyzer combines
     * it with the canonical encode result and one verification decode, avoiding
     * a duplicate encode. Custom support sets leave this null and retain their
     * public document predicate as the narrowing hook.
     */
    internal var analyzerDocumentClaim: ((List<Block>) -> Boolean)? = null
        private set

    internal fun withAnalyzerClaim(claim: (List<Block>) -> Boolean): MarkdownProfileSupportSet {
        analyzerDocumentClaim = claim
        return this
    }

    public companion object {
        /** Placeholder support set claiming nothing. */
        public val None: MarkdownProfileSupportSet = MarkdownProfileSupportSet(
            supportsBlockPredicate = { false },
            supportsSpanPredicate = { false },
            supportsDocumentPredicate = { false },
        )
    }
}

/** Build the default profile's value claims and executable document claim. */
internal fun markdownDefaultSupportSet(
    profileProvider: () -> MarkdownProfile,
): MarkdownProfileSupportSet {
    val blockClaim: (Block) -> Boolean = ::isDefaultSupportedBlock
    val spanClaim: (SpanStyle) -> Boolean = { style ->
        isDefaultSupportedSpan(style, profileProvider().htmlInMarkdown is HtmlInMarkdown.Bridge)
    }
    val shapeClaim: (List<Block>) -> Boolean = { blocks ->
        blocks.all { block -> defaultContentShapeIsSupported(block, blockClaim, spanClaim) }
    }
    return MarkdownProfileSupportSet(
        supportsBlockPredicate = blockClaim,
        supportsSpanPredicate = spanClaim,
        supportsDocumentPredicate = { blocks ->
            if (!shapeClaim(blocks)) false
            else {
                val profile = profileProvider()
                val encoded = MarkdownSchema.encodeWithReport(blocks, profile)
                markdownRoundTripMatches(blocks, encoded, profile, MarkdownCodecLimits.Default)
            }
        },
    ).withAnalyzerClaim(shapeClaim)
}

private fun isDefaultSupportedBlock(block: Block): Boolean {
    val depth = block.attributes.indentationLevel
    if (depth !in BlockAttributes.MIN_INDENTATION_LEVEL..BlockAttributes.MAX_INDENTATION_LEVEL) return false
    return when (val type = block.type) {
        BlockType.Paragraph -> true
        is BlockType.Heading -> depth == BlockAttributes.MIN_INDENTATION_LEVEL && type.level in 1..6
        BlockType.BulletList -> true
        is BlockType.NumberedList -> type.number >= 1
        is BlockType.Todo -> true
        BlockType.Quote, BlockType.Code -> depth == BlockAttributes.MIN_INDENTATION_LEVEL
        BlockType.Divider ->
            depth == BlockAttributes.MIN_INDENTATION_LEVEL && block.content is BlockContent.Empty
        else -> false
    }
}

private fun isDefaultSupportedSpan(style: SpanStyle, claimHtmlSpans: Boolean): Boolean = when (style) {
    SpanStyle.Bold,
    SpanStyle.Italic,
    SpanStyle.StrikeThrough,
    SpanStyle.InlineCode -> true
    SpanStyle.Underline -> claimHtmlSpans
    is SpanStyle.Highlight -> claimHtmlSpans && style.colorArgb in 0L..0xFFFF_FFFFL
    is SpanStyle.Link -> style.url.isNotBlank()
    is SpanStyle.Custom -> false
}

private fun defaultContentShapeIsSupported(
    block: Block,
    blockClaim: (Block) -> Boolean,
    spanClaim: (SpanStyle) -> Boolean,
): Boolean {
    if (!blockClaim(block)) return false
    return when (val content = block.content) {
        is BlockContent.Text -> {
            block.type.supportsText &&
                if (block.type.supportsSpans) content.spans.all { spanClaim(it.style) }
                else content.spans.isEmpty()
        }
        BlockContent.Empty -> !block.type.supportsText
        is BlockContent.Custom -> false
    }
}

/** True only when canonical output decodes to the same editor model, ignoring generated ids. */
internal fun markdownRoundTripMatches(
    blocks: List<Block>,
    encoded: MarkdownEncodeResult,
    profile: MarkdownProfile,
    limits: MarkdownCodecLimits,
): Boolean {
    val markdown = encoded.markdown ?: return false
    if (encoded.warnings.any(MarkdownWarning::blocksNativeEditing)) return false
    val decoded = MarkdownSchema.decodeWithReport(markdown, profile, limits)
    val decodedBlocks = decoded.blocks ?: return false
    if (decoded.warnings.any(MarkdownWarning::blocksNativeEditing)) return false
    return markdownModelsEqual(blocks, decodedBlocks)
}

private fun MarkdownWarning.blocksNativeEditing(): Boolean =
    impact == MarkdownFidelityImpact.OpaquePreservation ||
        impact == MarkdownFidelityImpact.DataLoss ||
        impact == MarkdownFidelityImpact.Fatal

internal fun markdownModelsEqual(expected: List<Block>, actual: List<Block>): Boolean =
    expected.size == actual.size && expected.indices.all { index ->
        val left = expected[index]
        val right = actual[index]
        left.type == right.type && left.content == right.content && left.attributes == right.attributes
    }
