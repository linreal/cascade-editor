package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.core.isValidIndentationOutline
import io.github.linreal.cascade.editor.core.renumberNumberedLists
import io.github.linreal.cascade.editor.richtext.SpanAlgorithms

/**
 * Executable claim of which blocks, spans, and whole documents a
 * [MarkdownProfile] commits to round-tripping through encode/decode.
 *
 * Unlike the HTML analog this carries an explicit **document predicate**:
 * Markdown document support depends on profile policies and span range
 * relationships (overlap/code-span residue, empty-paragraph encodability,
 * ambiguous emphasis) that cannot be inferred from the block and span
 * predicates alone. The default document predicate proves its claim
 * *executably*: it runs the encode-side verification (encode the candidate
 * and require zero [MarkdownFidelityImpact.DataLoss] / [MarkdownFidelityImpact.Fatal]
 * warnings) rather than re-deriving the codec's overlap rules.
 *
 * The support set is a plain replaceable value on the profile: no registration
 * call ever mutates or widens it — dialect profiles pair their registrations
 * with an explicit [MarkdownProfile.withSupportSet].
 *
 * @param supportsBlockPredicate whole-[Block] predicate; it receives the block
 *   (type + attributes + content) and must **not** unconditionally reject
 *   [BlockContent.Custom] — a custom support set can claim custom block types.
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
     * The cheap, encode-free structural half of the document claim for
     * default-built support sets (outline, numbering, span/block claims, empty
     * text), or null for custom support sets. `analyze` reuses this so it can
     * satisfy the support component of `nativeEditingSafe` from its single
     * encode instead of triggering the document predicate's own verification
     * encode, avoiding a second encode.
     */
    internal var structuralDocumentClaim: ((List<Block>) -> Boolean)? = null
        private set

    internal fun withStructuralClaim(claim: (List<Block>) -> Boolean): MarkdownProfileSupportSet {
        structuralDocumentClaim = claim
        return this
    }

    public companion object {

        /**
         * Placeholder support set claiming nothing. Used as the profile slot
         * before a real claim is installed; the codec never advertises support
         * it has not proven.
         */
        public val None: MarkdownProfileSupportSet = MarkdownProfileSupportSet(
            supportsBlockPredicate = { false },
            supportsSpanPredicate = { false },
            supportsDocumentPredicate = { false },
        )
    }
}

/**
 * Build the default-profile support-set claim, fully **resolved from the owning
 * profile at predicate-call time** (never frozen at build): empty-text-block
 * support tracks [NewlineSemantics.HardBreak], and HTML span claims
 * (Underline/Highlight) track whether [HtmlInMarkdown.Bridge] is active — so
 * `Default.withNewlineSemantics(HardBreak)` / `withHtmlInMarkdown(...)` produce
 * an honest claim once the profile rebinds this set (see
 * `MarkdownProfile.rebindDefaultDocumentClaim`).
 *
 * Block claim: `Paragraph`, `Heading(1..6)` at depth 0, `BulletList`,
 * `NumberedList(n>=1)`, `Todo`, `Quote`/`Code` at depth 0, `Divider` (empty
 * content, depth 0); indentation `0..MAX_INDENTATION_LEVEL`. Preserved and
 * other custom/unknown block types are **not** claimed.
 *
 * Span claim: `Bold`, `Italic`, `StrikeThrough`, `InlineCode`; `Underline` and
 * `Highlight` (color in `0..0xFFFF_FFFF`) only when the HTML bridge is active
 * (they round-trip through `<u>`/`<mark>`); `Link` only for a nonblank target;
 * `Custom` excluded.
 *
 * Document claim (beyond per-block/per-span): normalized indentation outline,
 * stable numbering, normalized non-overlapping spans, no nested links, no `\r`
 * in text (it does not round-trip), no empty text blocks outside HardBreak,
 * the HardBreak merge/front-matter exclusions (see [hardBreakShapeIsClaimable]),
 * and a clean verification encode (routed through the no-throw
 * [MarkdownSchema.encodeWithReport]).
 *
 * [profileProvider] is invoked lazily (never during profile construction) so a
 * profile can install its own default claim without an initialization cycle.
 */
internal fun markdownDefaultSupportSet(
    profileProvider: () -> MarkdownProfile,
): MarkdownProfileSupportSet {
    val spanClaim: (SpanStyle) -> Boolean = { style ->
        isDefaultSupportedSpan(style, claimHtmlSpans = profileProvider().htmlInMarkdown is HtmlInMarkdown.Bridge)
    }
    val structural: (List<Block>) -> Boolean = { blocks ->
        isDefaultStructurallySupportedDocument(blocks, profileProvider(), spanClaim)
    }
    return MarkdownProfileSupportSet(
        supportsBlockPredicate = { block -> isDefaultSupportedBlock(block) },
        supportsSpanPredicate = spanClaim,
        supportsDocumentPredicate = { blocks ->
            structural(blocks) && documentEncodesCleanly(blocks, profileProvider)
        },
    ).withStructuralClaim(structural)
}

private fun isDefaultSupportedBlock(block: Block): Boolean {
    val depth = block.attributes.indentationLevel
    if (depth < BlockAttributes.MIN_INDENTATION_LEVEL || depth > BlockAttributes.MAX_INDENTATION_LEVEL) {
        return false
    }
    return when (val type = block.type) {
        BlockType.Paragraph -> true
        is BlockType.Heading -> depth == BlockAttributes.MIN_INDENTATION_LEVEL && type.level in 1..6
        BlockType.BulletList -> true
        is BlockType.NumberedList -> type.number >= 1
        is BlockType.Todo -> true
        BlockType.Quote -> depth == BlockAttributes.MIN_INDENTATION_LEVEL
        BlockType.Code -> depth == BlockAttributes.MIN_INDENTATION_LEVEL
        BlockType.Divider ->
            depth == BlockAttributes.MIN_INDENTATION_LEVEL && block.content is BlockContent.Empty
        // UnknownBlockType (preserved / custom) and any other type: not claimed
        // by the default set. A custom support set may claim these by typeId.
        else -> false
    }
}

private fun isDefaultSupportedSpan(style: SpanStyle, claimHtmlSpans: Boolean): Boolean = when (style) {
    SpanStyle.Bold,
    SpanStyle.Italic,
    SpanStyle.StrikeThrough,
    SpanStyle.InlineCode -> true
    // Underline / Highlight only round-trip through the HTML bridge; a strict
    // (no-HTML) profile does not claim them.
    SpanStyle.Underline -> claimHtmlSpans
    is SpanStyle.Highlight -> claimHtmlSpans && style.colorArgb in 0L..0xFFFF_FFFFL
    is SpanStyle.Link -> style.url.isNotBlank()
    is SpanStyle.Custom -> false
}

private fun isDefaultStructurallySupportedDocument(
    blocks: List<Block>,
    profile: MarkdownProfile,
    spanClaim: (SpanStyle) -> Boolean,
): Boolean {
    if (!blocks.isValidIndentationOutline()) return false
    if (renumberNumberedLists(blocks) !== blocks) return false

    val hardBreak = profile.newlineSemantics == NewlineSemantics.HardBreak

    for (block in blocks) {
        if (!isDefaultSupportedBlock(block)) return false
        val content = block.content
        when (content) {
            is BlockContent.Text -> {
                if (!block.type.supportsText) return false
                // `\r` never round-trips: the source layer treats it as a line
                // terminator, so `a\rb` re-decodes altered.
                if (content.text.indexOf('\r') >= 0) return false
                if (!hardBreak && content.text.isEmpty()) return false
                if (!block.type.supportsSpans) {
                    if (content.spans.isNotEmpty()) return false
                } else {
                    if (!spansAreClaimable(content.spans, content.text.length, spanClaim)) return false
                }
            }
            BlockContent.Empty -> if (block.type.supportsText) return false
            is BlockContent.Custom -> return false
        }
    }
    if (hardBreak && !hardBreakShapeIsClaimable(blocks)) return false
    return true
}

/**
 * HardBreak-mode shape constraints that guarantee `decode(encode(x)) == x`
 * (the claim, not the generator, is the round-trip boundary):
 *
 * - container blocks (`Quote`/`BulletList`/`NumberedList`/`Todo`) have ambiguous
 *   single-`\n` boundaries (lazy continuation / item merging) and are not
 *   claimed in HardBreak;
 * - two adjacent non-empty `Paragraph`s merge into one on decode;
 * - a leading `Divider` plus any later `Divider` is captured by the
 *   document-start front-matter recognizer (`---` … `---`).
 */
private fun hardBreakShapeIsClaimable(blocks: List<Block>): Boolean {
    for (block in blocks) {
        when (block.type) {
            BlockType.Quote, BlockType.BulletList, is BlockType.NumberedList, is BlockType.Todo -> return false
            else -> Unit
        }
    }
    for (i in 0 until blocks.size - 1) {
        if (blocks[i].isNonEmptyParagraph() && blocks[i + 1].isNonEmptyParagraph()) return false
    }
    if (blocks.firstOrNull()?.type == BlockType.Divider && blocks.drop(1).any { it.type == BlockType.Divider }) {
        return false
    }
    return true
}

private fun Block.isNonEmptyParagraph(): Boolean =
    type == BlockType.Paragraph && (content as? BlockContent.Text)?.text?.isNotEmpty() == true

/**
 * The verification predicate: a clean encode (no DataLoss/Fatal impact) is
 * the executable claim that every text/span/code layout round-trips. Routed
 * through the public no-throw [MarkdownSchema.encodeWithReport] so a throwing
 * consumer encoder cannot escape the claim check.
 */
private fun documentEncodesCleanly(
    blocks: List<Block>,
    profileProvider: () -> MarkdownProfile,
): Boolean {
    val result = MarkdownSchema.encodeWithReport(blocks, profileProvider())
    if (result.isAborted) return false
    return result.warnings.none {
        it.impact == MarkdownFidelityImpact.DataLoss || it.impact == MarkdownFidelityImpact.Fatal
    }
}

/** Spans must be normalized, in range, span-supported, and free of nested links. */
private fun spansAreClaimable(
    spans: List<TextSpan>,
    textLength: Int,
    spanClaim: (SpanStyle) -> Boolean,
): Boolean {
    for (span in spans) {
        if (!spanClaim(span.style)) return false
    }
    if (spans != SpanAlgorithms.normalize(spans, textLength)) return false
    // Nested links: two Link spans must not overlap (a link inside a link is
    // invalid Markdown; the encoder cannot round-trip it).
    val links = spans.filter { it.style is SpanStyle.Link }
    for (i in links.indices) {
        for (j in i + 1 until links.size) {
            if (links[i].start < links[j].end && links[j].start < links[i].end) return false
        }
    }
    return true
}
