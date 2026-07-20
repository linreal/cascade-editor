package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.UnknownBlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Inline and metadata preservation escalation.
 * Under `Preserve` there is no DataLoss decode path: every unrepresentable
 * construct escalates to opaque `md.preserved` blocks; under `WarnAndDegrade`
 * editable content lowers with DataLoss warnings.
 */
class MarkdownEscalationTest {

    private val degradeProfile =
        MarkdownProfile.Default.withUnsupportedSyntax(UnsupportedSyntax.WarnAndDegrade)

    private fun decode(
        input: String,
        profile: MarkdownProfile = MarkdownProfile.Default,
    ): MarkdownDecodeResult = MarkdownDecodeEngine.decode(input, profile)

    private fun MarkdownDecodeResult.requireBlocks(): List<Block> =
        blocks ?: fail("expected a successful decode, warnings=$warnings")

    private fun Block.preservedPayload(expectedKind: String): String {
        val type = assertIs<UnknownBlockType>(this.type)
        assertEquals("md.preserved", type.typeId)
        val content = assertIs<BlockContent.Custom>(this.content)
        assertEquals(expectedKind, content.data["kind"])
        return content.data["rawMarkdown"] as? String
            ?: fail("expected a rawMarkdown slice, got ${content.data}")
    }

    private fun MarkdownDecodeResult.assertNoDataLoss() {
        assertTrue(
            warnings.none { it.impact == MarkdownFidelityImpact.DataLoss },
            "Preserve must have no DataLoss path, got $warnings",
        )
    }

    // Inline math

    @Test
    fun inlineMathEscalatesWholeLeafUnderPreserve() {
        val result = decode("a \$x^2\$ b")
        val block = result.requireBlocks().single()
        assertEquals("a \$x^2\$ b", block.preservedPayload("inlineMath"))
        val warning = result.warnings
            .filterIsInstance<MarkdownDecodeWarning.PreservedSyntax>()
            .single()
        assertEquals("inlineMath", warning.kind)
        result.assertNoDataLoss()
    }

    @Test
    fun inlineMathStaysLiteralTextUnderWarnAndDegrade() {
        val result = decode("a \$x^2\$ b", degradeProfile)
        val block = result.requireBlocks().single()
        assertEquals(BlockType.Paragraph, block.type)
        assertEquals("a \$x^2\$ b", (block.content as BlockContent.Text).text)
        val warning = result.warnings
            .filterIsInstance<MarkdownDecodeWarning.UnsupportedSyntax>()
            .single { it.construct == "inlineMath" }
        assertEquals(MarkdownFidelityImpact.DataLoss, warning.impact)
    }

    // Inline images

    @Test
    fun inlineImageEscalatesUnderPreserveAndKeepsAltUnderDegrade() {
        val preserved = decode("see ![alt](u) here")
        assertEquals(
            "see ![alt](u) here",
            preserved.requireBlocks().single().preservedPayload("inlineImage"),
        )
        preserved.assertNoDataLoss()

        val degraded = decode("see ![alt](u) here", degradeProfile)
        val block = degraded.requireBlocks().single()
        assertEquals("see alt here", (block.content as BlockContent.Text).text)
        assertTrue(
            degraded.warnings.filterIsInstance<MarkdownDecodeWarning.UnsupportedSyntax>()
                .any { it.construct == "inlineImage" },
        )
    }

    // Footnote references

    @Test
    fun footnoteRefEscalatesUnderPreserveAndStaysLiteralUnderDegrade() {
        val preserved = decode("a [^1] b")
        assertEquals("a [^1] b", preserved.requireBlocks().single().preservedPayload("footnoteRef"))
        preserved.assertNoDataLoss()

        val degraded = decode("a [^1] b", degradeProfile)
        val block = degraded.requireBlocks().single()
        assertEquals("a [^1] b", (block.content as BlockContent.Text).text)
        assertTrue(
            degraded.warnings.filterIsInstance<MarkdownDecodeWarning.UnsupportedSyntax>()
                .any { it.construct == "footnoteRef" },
        )
    }

    // Link titles

    @Test
    fun inlineLinkTitleEscalatesTheLeafUnderPreserve() {
        val result = decode("x [a](u \"title\") y")
        val block = result.requireBlocks().single()
        assertEquals("x [a](u \"title\") y", block.preservedPayload("linkTitle"))
        result.assertNoDataLoss()
    }

    @Test
    fun titledDefinitionPreservesDefinitionAndEveryReferringLeaf() {
        val input = "[a][r]\n\nplain\n\n[r]: /u \"title\""
        val result = decode(input)
        val blocks = result.requireBlocks()
        assertEquals(3, blocks.size)
        assertEquals("[a][r]", blocks[0].preservedPayload("linkReferenceTitle"))
        assertEquals(BlockType.Paragraph, blocks[1].type)
        assertEquals("[r]: /u \"title\"", blocks[2].preservedPayload("linkReferenceDefinition"))
        result.assertNoDataLoss()
    }

    @Test
    fun untitledDefinitionStillLowersReferencesNatively() {
        val result = decode("[a][r]\n\n[r]: /u")
        val block = result.requireBlocks().single()
        assertEquals(BlockType.Paragraph, block.type)
        result.assertNoDataLoss()
    }

    // Ordered-list start values

    @Test
    fun nonOneOrderedStartPreservesTheWholeListOpaquely() {
        val result = decode("3. a\n4. b")
        val block = result.requireBlocks().single()
        assertEquals("3. a\n4. b", block.preservedPayload("orderedList"))
        result.assertNoDataLoss()
    }

    // Complex containers

    @Test
    fun multiParagraphListItemPreservesTheSmallestSafeContainer() {
        val input = "- a\n\n  b"
        val result = decode(input)
        val block = result.requireBlocks().single()
        assertEquals(input, block.preservedPayload("listItem"))
        result.assertNoDataLoss()
    }

    @Test
    fun nestedQuotePreservesTheWholeQuote() {
        val result = decode(">> deep")
        val block = result.requireBlocks().single()
        assertEquals(">> deep", block.preservedPayload("blockquote"))
        result.assertNoDataLoss()
    }

    @Test
    fun headingInsideQuotePreservesTheWholeQuote() {
        val input = "> # h\n> text"
        val result = decode(input)
        val block = result.requireBlocks().single()
        assertEquals(input, block.preservedPayload("blockquote"))
        result.assertNoDataLoss()
    }

    @Test
    fun listNestingBeyondModuleDepthPreservesTheList() {
        val input = "- a\n" +
            "  - b\n" +
            "    - c\n" +
            "      - d\n" +
            "        - e\n" +
            "          - f\n" +
            "            - g"
        val result = decode(input)
        val block = result.requireBlocks().single()
        assertEquals(input, block.preservedPayload("list"))
        result.assertNoDataLoss()
    }

    @Test
    fun fenceInsideListPreservesTheWholeList() {
        // A fence inside an item is multi-block item content; the item's slice
        // inside a container is not independently re-emittable (interior lines
        // carry the container indent), so the owner is the enclosing
        // root-level list.
        val input = "- a\n  ```kotlin\n  x\n  ```"
        val result = decode(input)
        val block = result.requireBlocks().single()
        assertEquals(input, block.preservedPayload("listItem"))
        result.assertNoDataLoss()
    }

    // Escalation happens before span commit

    @Test
    fun escalatedLeafLeaksNoPartialSpans() {
        val result = decode("**bold** \$m\$")
        val blocks = result.requireBlocks()
        val block = blocks.single()
        assertEquals("**bold** \$m\$", block.preservedPayload("inlineMath"))
        // No text block with committed spans exists anywhere in the result.
        assertTrue(blocks.none { it.content is BlockContent.Text })
        result.assertNoDataLoss()
    }

    @Test
    fun onlyTheEscalatingRootNodeIsPreserved() {
        val result = decode("plain **b**\n\na \$m\$ b\n\ntail")
        val blocks = result.requireBlocks()
        assertEquals(3, blocks.size)
        assertEquals(BlockType.Paragraph, blocks[0].type)
        assertEquals("plain b", (blocks[0].content as BlockContent.Text).text)
        blocks[1].preservedPayload("inlineMath")
        assertEquals(BlockType.Paragraph, blocks[2].type)
        result.assertNoDataLoss()
    }

    // The quote leaf owner is the root-level quote, not the bare leaf

    @Test
    fun inlineEscalationInsideQuotePreservesTheWholeQuote() {
        val input = "> a \$m\$ b"
        val result = decode(input)
        val block = result.requireBlocks().single()
        assertEquals(input, block.preservedPayload("inlineMath"))
        result.assertNoDataLoss()
    }
}
