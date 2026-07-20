package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** Source tree → editor blocks through the internal decode engine. */
class MarkdownLoweringTest {

    // The degrade paths asserted below are WarnAndDegrade behavior; under the
    // default Preserve policy the same shapes escalate to md.preserved blocks
    // Covered by MarkdownEscalationTest.
    private val degradeProfile =
        MarkdownProfile.Default.withUnsupportedSyntax(UnsupportedSyntax.WarnAndDegrade)

    private fun decode(
        input: String,
        profile: MarkdownProfile = MarkdownProfile.Default,
        limits: MarkdownCodecLimits = MarkdownCodecLimits.Default,
    ): MarkdownDecodeResult = MarkdownDecodeEngine.decode(input, profile, limits)

    private fun MarkdownDecodeResult.requireBlocks(): List<Block> =
        blocks ?: fail("expected a successful decode, got aborted with warnings=$warnings")

    private val Block.text: String
        get() = (content as? BlockContent.Text)?.text ?: fail("expected text content, got $content")

    // Headings, paragraphs, dividers

    @Test
    fun headingsParagraphsAndDividersLower() {
        val blocks = decode("## title\n\nbody\n\n---").requireBlocks()
        assertEquals(3, blocks.size)
        assertEquals(BlockType.Heading(2), blocks[0].type)
        assertEquals("title", blocks[0].text)
        assertEquals(BlockType.Paragraph, blocks[1].type)
        assertEquals("body", blocks[1].text)
        assertEquals(BlockType.Divider, blocks[2].type)
        assertEquals(BlockContent.Empty, blocks[2].content)
    }

    @Test
    fun softBreakLowersPerPolicy() {
        val spaceJoined = decode("a\nb").requireBlocks()
        assertEquals("a b", spaceJoined.single().text)

        val lineBreakProfile = MarkdownProfile.Default.withSoftBreak(SoftBreak.LineBreak)
        val newlineJoined = decode("a\nb", lineBreakProfile).requireBlocks()
        assertEquals("a\nb", newlineJoined.single().text)
    }

    @Test
    fun emptyInputLowersToSuccessfulEmptyPayload() {
        val result = decode("")
        assertTrue(result.isSuccess)
        assertEquals(emptyList(), result.blocks)
    }

    // Quotes

    @Test
    fun multiParagraphQuoteLowersToOneQuoteBlockPerParagraph() {
        val blocks = decode("> a\n>\n> b").requireBlocks()
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it.type == BlockType.Quote })
        assertEquals(listOf("a", "b"), blocks.map { it.text })
    }

    @Test
    fun nestedQuoteFlattensToOneLevelWithWarning() {
        val result = decode(">> deep", degradeProfile)
        val blocks = result.requireBlocks()
        assertEquals(1, blocks.size)
        assertEquals(BlockType.Quote, blocks[0].type)
        assertEquals("deep", blocks[0].text)
        val warning = result.warnings
            .filterIsInstance<MarkdownDecodeWarning.DroppedAttribute>()
            .single { it.construct == "blockquote" }
        assertEquals(MarkdownFidelityImpact.DataLoss, warning.impact)
    }

    @Test
    fun headingInsideQuoteDegradesWithDataLossWarning() {
        val result = decode("> # h", degradeProfile)
        val blocks = result.requireBlocks()
        assertEquals(1, blocks.size)
        assertEquals(BlockType.Heading(1), blocks[0].type)
        assertEquals("h", blocks[0].text)
        val warning = result.warnings
            .filterIsInstance<MarkdownDecodeWarning.UnsupportedSyntax>()
            .single { it.construct == "blockquote" }
        assertEquals(MarkdownFidelityImpact.DataLoss, warning.impact)
    }

    // Lists

    @Test
    fun threeLevelBulletListLowersToNestedDepths() {
        val blocks = decode("- a\n  - b\n    - c").requireBlocks()
        assertEquals(3, blocks.size)
        assertTrue(blocks.all { it.type == BlockType.BulletList })
        assertEquals(listOf("a", "b", "c"), blocks.map { it.text })
        assertEquals(listOf(0, 1, 2), blocks.map { it.attributes.indentationLevel })
    }

    @Test
    fun nonOneOrderedStartRenumbersWithDataLossWarning() {
        val result = decode("3. a", degradeProfile)
        val blocks = result.requireBlocks()
        assertEquals(BlockType.NumberedList(1), blocks.single().type)
        assertEquals("a", blocks.single().text)
        val warning = result.warnings
            .filterIsInstance<MarkdownDecodeWarning.UnsupportedSyntax>()
            .single { it.construct == "orderedList" }
        assertEquals(MarkdownFidelityImpact.DataLoss, warning.impact)
        assertEquals(0, warning.range.start)
        assertTrue(warning.range.endExclusive > 0)
    }

    @Test
    fun orderedRunKeepsSequentialNumbering() {
        val blocks = decode("1. a\n2. b\n3. c").requireBlocks()
        assertEquals(
            listOf(BlockType.NumberedList(1), BlockType.NumberedList(2), BlockType.NumberedList(3)),
            blocks.map { it.type },
        )
    }

    @Test
    fun taskItemsLowerToTodosWithCheckedFlags() {
        val blocks = decode("- [x] done\n- [ ] open").requireBlocks()
        assertEquals(2, blocks.size)
        assertEquals(BlockType.Todo(checked = true), blocks[0].type)
        assertEquals("done", blocks[0].text)
        assertEquals(BlockType.Todo(checked = false), blocks[1].type)
        assertEquals("open", blocks[1].text)
    }

    @Test
    fun nestedTaskItemsKeepListDepths() {
        val blocks = decode("- a\n  - [x] b").requireBlocks()
        assertEquals(BlockType.BulletList, blocks[0].type)
        assertEquals(BlockType.Todo(checked = true), blocks[1].type)
        assertEquals(1, blocks[1].attributes.indentationLevel)
    }

    @Test
    fun depthBeyondModuleRangeClampsWithWarning() {
        val input = "- a\n" +
            "  - b\n" +
            "    - c\n" +
            "      - d\n" +
            "        - e\n" +
            "          - f\n" +
            "            - g"
        val result = decode(input, degradeProfile)
        val blocks = result.requireBlocks()
        assertEquals(7, blocks.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 5), blocks.map { it.attributes.indentationLevel })
        val warning = result.warnings
            .filterIsInstance<MarkdownDecodeWarning.DroppedAttribute>()
            .single { it.construct == "list" }
        assertEquals("indentationLevel", warning.attr)
    }

    @Test
    fun multiParagraphListItemDegradesWithWarning() {
        val result = decode("- a\n\n  b", degradeProfile)
        val blocks = result.requireBlocks()
        assertEquals(2, blocks.size)
        assertEquals(BlockType.BulletList, blocks[0].type)
        assertEquals("a", blocks[0].text)
        assertEquals(BlockType.Paragraph, blocks[1].type)
        assertEquals("b", blocks[1].text)
        val warning = result.warnings
            .filterIsInstance<MarkdownDecodeWarning.UnsupportedSyntax>()
            .single { it.construct == "listItem" }
        assertEquals(MarkdownFidelityImpact.DataLoss, warning.impact)
    }

    @Test
    fun fenceInsideListItemDegradesWithWarning() {
        val result = decode("- a\n  ```\n  x\n  ```", degradeProfile)
        val blocks = result.requireBlocks()
        assertEquals(2, blocks.size)
        assertEquals(BlockType.BulletList, blocks[0].type)
        assertEquals(BlockType.Code, blocks[1].type)
        assertEquals("x", blocks[1].text)
        assertTrue(
            result.warnings.filterIsInstance<MarkdownDecodeWarning.UnsupportedSyntax>()
                .any { it.construct == "listItem" },
        )
    }

    // Code

    @Test
    fun fenceContentPreservesExactStructuralNewlines() {
        val blocks = decode("```\n\ncode\n\n\n```").requireBlocks()
        val code = blocks.single()
        assertEquals(BlockType.Code, code.type)
        assertEquals("\ncode\n\n", code.text)
    }

    @Test
    fun indentedCodeLowersToCodeBlock() {
        val blocks = decode("    code").requireBlocks()
        assertEquals(BlockType.Code, blocks.single().type)
        assertEquals("code", blocks.single().text)
    }

    // Identity and pipeline invariants

    @Test
    fun loweredBlocksGetFreshIdsOnEveryDecode() {
        val first = decode("a\n\nb").requireBlocks()
        val second = decode("a\n\nb").requireBlocks()
        val ids = (first + second).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun abortedParsePropagatesAsAbortedDecode() {
        val result = decode("text", limits = MarkdownCodecLimits(maxInputChars = 1))
        assertTrue(result.isAborted)
        assertEquals(null, result.blocks)
        assertIs<MarkdownDecodeWarning.InputLimitExceeded>(result.warnings.single())
    }
}
