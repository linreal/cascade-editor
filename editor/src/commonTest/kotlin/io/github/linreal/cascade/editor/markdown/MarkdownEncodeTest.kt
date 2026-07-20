package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.CustomBlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Canonical block encoder and emission engine. */
class MarkdownEncodeTest {

    private fun encode(
        blocks: List<Block>,
        profile: MarkdownProfile = MarkdownProfile.Default,
        limits: MarkdownCodecLimits = MarkdownCodecLimits.Default,
        lineEnding: MarkdownLineEnding = MarkdownLineEnding.Lf,
    ): MarkdownEncodeResult = MarkdownEncodeEngine.encodeWithReport(blocks, profile, limits, lineEnding)

    private fun MarkdownEncodeResult.requireMarkdown(): String =
        markdown ?: fail("expected a successful encode, got aborted with warnings=$warnings")

    private fun textBlock(type: BlockType, text: String): Block =
        Block(id = BlockId.generate(), type = type, content = BlockContent.Text(text))

    // Engine-owned separation and canonical forms

    @Test
    fun headingAndParagraphJoinWithBlankLineAndEndWithOneNewline() {
        val markdown = encode(
            listOf(
                textBlock(BlockType.Heading(2), "a"),
                textBlock(BlockType.Paragraph, "b"),
            ),
        ).requireMarkdown()
        assertEquals("## a\n\nb\n", markdown)
    }

    @Test
    fun emptyDocumentEncodesToEmptySuccessfulPayload() {
        val result = encode(emptyList())
        assertTrue(result.isSuccess)
        assertEquals("", result.markdown)
    }

    @Test
    fun dividerEncodesCanonically() {
        assertEquals("---\n", encode(listOf(Block.divider())).requireMarkdown())
    }

    @Test
    fun crLfOptionConvertsEveryLineEnding() {
        val markdown = encode(
            listOf(
                textBlock(BlockType.Heading(1), "a"),
                textBlock(BlockType.Paragraph, "b"),
            ),
            lineEnding = MarkdownLineEnding.CrLf,
        ).requireMarkdown()
        assertEquals("# a\r\n\r\nb\r\n", markdown)
    }

    // Quote prefixes every physical line

    @Test
    fun quoteWithHardBreakPrefixesBothPhysicalLines() {
        val markdown = encode(listOf(textBlock(BlockType.Quote, "a\nb"))).requireMarkdown()
        assertEquals("> a\\\n> b\n", markdown)
    }

    @Test
    fun quoteHardBreakUsesTwoSpacesUnderThatPolicy() {
        val profile = MarkdownProfile.Default.withHardBreakEncode(HardBreakEncode.TwoSpaces)
        val markdown = encode(listOf(textBlock(BlockType.Quote, "a\nb")), profile).requireMarkdown()
        assertEquals("> a  \n> b\n", markdown)
    }

    // Fences

    @Test
    fun codeFenceWidensPastInternalBacktickRuns() {
        val markdown = encode(listOf(textBlock(BlockType.Code, "a\n```\nb"))).requireMarkdown()
        assertEquals("````\na\n```\nb\n````\n", markdown)
    }

    @Test
    fun codeFenceWidensPastContentEndingInAFenceLikeLine() {
        val markdown = encode(listOf(textBlock(BlockType.Code, "a\n```"))).requireMarkdown()
        assertEquals("````\na\n```\n````\n", markdown)
    }

    @Test
    fun emptyCodeBlockEncodesAsEmptyFence() {
        val markdown = encode(listOf(textBlock(BlockType.Code, ""))).requireMarkdown()
        assertEquals("```\n```\n", markdown)
    }

    @Test
    fun codeContentIsNeverEscaped() {
        val markdown = encode(listOf(textBlock(BlockType.Code, "# not a heading *x*"))).requireMarkdown()
        assertEquals("```\n# not a heading *x*\n```\n", markdown)
    }

    // Warnings and fallbacks

    @Test
    fun paragraphDepthDropsToZeroWithWarning() {
        val block = Block.paragraph("b").withAttributes(BlockAttributes(indentationLevel = 2))
        val result = encode(listOf(block))
        assertEquals("b\n", result.requireMarkdown())
        val warning = result.warnings
            .filterIsInstance<MarkdownEncodeWarning.DroppedAttribute>()
            .single()
        assertEquals("indentationLevel", warning.attr)
        assertEquals(block.id, warning.blockId)
    }

    @Test
    fun headingWithEmbeddedNewlineEncodesSpacesWithDataLossWarning() {
        val result = encode(listOf(textBlock(BlockType.Heading(1), "a\nb")))
        assertEquals("# a b\n", result.requireMarkdown())
        val warning = result.warnings
            .filterIsInstance<MarkdownEncodeWarning.DroppedAttribute>()
            .single()
        assertEquals(MarkdownFidelityImpact.DataLoss, warning.impact)
    }

    @Test
    fun unregisteredCustomBlockFallsBackToPlainParagraphWithWarning() {
        val customType = object : CustomBlockType {
            override val typeId: String = "sample.widget"
            override val displayName: String = "Widget"
            override val supportsText: Boolean = true
        }
        val block = Block(BlockId.generate(), customType, BlockContent.Text("visible"))
        val result = encode(listOf(block))
        assertEquals("visible\n", result.requireMarkdown())
        val warning = result.warnings
            .filterIsInstance<MarkdownEncodeWarning.UnsupportedBlock>()
            .single()
        assertEquals("sample.widget", warning.typeId)
        assertEquals(MarkdownFidelityImpact.DataLoss, warning.impact)
    }

    @Test
    fun emptyParagraphIsNotEncodableInCommonMarkMode() {
        val result = encode(listOf(Block.paragraph("")))
        assertEquals("", result.requireMarkdown())
        assertTrue(result.warnings.filterIsInstance<MarkdownEncodeWarning.UnsupportedBlock>().isNotEmpty())
    }

    @Test
    fun emptyQuoteIsNotEncodableInCommonMarkMode() {
        // "> " would re-decode to zero blocks; the empty quote must go through
        // the warning fallback, never vanish silently.
        val result = encode(listOf(textBlock(BlockType.Quote, "")))
        assertEquals("", result.requireMarkdown())
        assertTrue(result.warnings.filterIsInstance<MarkdownEncodeWarning.UnsupportedBlock>().isNotEmpty())
    }

    @Test
    fun throwingConsumerEncoderDegradesThroughFallbackWithoutThrowing() {
        val profile = MarkdownProfile.Default.withMarkdownBlockEncoder<BlockType.Paragraph> { _, _, _ ->
            error("boom")
        }
        val result = encode(listOf(Block.paragraph("still here")), profile)
        assertEquals("still here\n", result.requireMarkdown())
        assertIs<MarkdownEncodeWarning.EncoderException>(
            result.warnings.first { it is MarkdownEncodeWarning.EncoderException },
        )
    }

    // Limits

    @Test
    fun outputLimitAbortsWithNullPayload() {
        val result = encode(
            listOf(Block.paragraph("a".repeat(64))),
            limits = MarkdownCodecLimits(maxOutputChars = 16),
        )
        assertTrue(result.isAborted)
        assertNull(result.markdown)
        val fatal = result.warnings.single { it.impact == MarkdownFidelityImpact.Fatal }
        assertIs<MarkdownEncodeWarning.OutputLimitExceeded>(fatal)
    }

    // Preserved verbatim emission

    @Test
    fun preservedBlockEmitsExactRawMarkdownViaVerbatim() {
        val table = "| a | b |\n| --- | --- |"
        val decoded = MarkdownDecodeEngine.decode(table, MarkdownProfile.Default).blocks
            ?: fail("decode failed")
        val markdown = encode(decoded).requireMarkdown()
        assertEquals("$table\n", markdown)
    }

    // decode(encode(...)) smoke test for block shapes

    @Test
    fun blockShapesSurviveEncodeDecodeRoundTrip() {
        val original = listOf(
            textBlock(BlockType.Heading(1), "Title"),
            textBlock(BlockType.Paragraph, "Some plain text."),
            textBlock(BlockType.BulletList, "one"),
            textBlock(BlockType.Todo(checked = true), "task"),
            textBlock(BlockType.NumberedList(1), "first"),
            textBlock(BlockType.Quote, "quoted"),
            textBlock(BlockType.Code, "code()\n  indented"),
            Block.divider(),
        )
        val markdown = encode(original).requireMarkdown()
        val decoded = MarkdownDecodeEngine.decode(markdown, MarkdownProfile.Default).blocks
            ?: fail("re-decode failed")
        assertEquals(original.map { it.type }, decoded.map { it.type })
        assertEquals(
            original.map { (it.content as? BlockContent.Text)?.text },
            decoded.map { (it.content as? BlockContent.Text)?.text },
        )
        assertEquals(
            original.map { it.attributes.indentationLevel },
            decoded.map { it.attributes.indentationLevel },
        )
    }
}
