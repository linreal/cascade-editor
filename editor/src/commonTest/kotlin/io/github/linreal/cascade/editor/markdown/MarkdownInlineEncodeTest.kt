package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** Canonical marker emission with encode-side verification. */
class MarkdownInlineEncodeTest {

    private fun paragraph(text: String, vararg spans: TextSpan): Block = Block(
        id = BlockId.generate(),
        type = BlockType.Paragraph,
        content = BlockContent.Text(text, spans.toList()),
    )

    private fun encode(
        vararg blocks: Block,
        profile: MarkdownProfile = MarkdownProfile.Default,
    ): MarkdownEncodeResult = MarkdownEncodeEngine.encodeWithReport(blocks.toList(), profile)

    private fun MarkdownEncodeResult.text(): String =
        markdown ?: fail("encode aborted: $warnings")

    private fun decodeSpans(
        markdown: String,
        profile: MarkdownProfile = MarkdownProfile.Default,
    ): BlockContent.Text {
        val result = MarkdownDecodeEngine.decode(markdown, profile)
        val block = result.blocks?.singleOrNull()
            ?: fail("expected one decoded block, warnings=${result.warnings}")
        return assertIs<BlockContent.Text>(block.content)
    }

    // Canonical markers

    @Test
    fun boldAndItalicUseCanonicalMarkers() {
        assertEquals(
            "**bold**\n",
            encode(paragraph("bold", TextSpan(0, 4, SpanStyle.Bold))).text(),
        )
        assertEquals(
            "*italic*\n",
            encode(paragraph("italic", TextSpan(0, 6, SpanStyle.Italic))).text(),
        )
        assertEquals(
            "~~struck~~\n",
            encode(paragraph("struck", TextSpan(0, 6, SpanStyle.StrikeThrough))).text(),
        )
    }

    @Test
    fun juxtaposedStarRunsSwitchItalicToUnderscore() {
        val result = encode(
            paragraph(
                "(a)(b)",
                TextSpan(0, 3, SpanStyle.Bold),
                TextSpan(3, 6, SpanStyle.Italic),
            ),
        )
        assertEquals("**(a)**_(b)_\n", result.text())
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun intrawordJuxtapositionKeepsStarAndVerifies() {
        // The italic boundary is intraword, so `_` cannot flank; the emitted
        // `***` juxtaposition must still re-decode to exactly these spans.
        val spans = listOf(
            TextSpan(0, 1, SpanStyle.Bold),
            TextSpan(1, 2, SpanStyle.Italic),
        )
        val result = encode(paragraph("ab", *spans.toTypedArray()))
        val decoded = decodeSpans(result.text())
        assertEquals("ab", decoded.text)
        assertEquals(spans.toSet(), decoded.spans.toSet())
    }

    @Test
    fun intrawordItalicKeepsStarMarker() {
        val result = encode(paragraph("abc", TextSpan(1, 2, SpanStyle.Italic)))
        assertEquals("a*b*c\n", result.text())
        val decoded = decodeSpans(result.text())
        assertEquals(listOf(TextSpan(1, 2, SpanStyle.Italic)), decoded.spans)
    }

    @Test
    fun plainDelimiterCharactersAreEscaped() {
        val result = encode(paragraph("a*b"))
        val markdown = result.text()
        val decoded = decodeSpans(markdown)
        assertEquals("a*b", decoded.text)
        assertTrue(decoded.spans.isEmpty())
    }

    // Inline code

    @Test
    fun boldFullyContainingCodeRoundTrips() {
        val spans = listOf(
            TextSpan(0, 3, SpanStyle.Bold),
            TextSpan(1, 2, SpanStyle.InlineCode),
        )
        val result = encode(paragraph("abc", *spans.toTypedArray()))
        assertEquals("**a`b`c**\n", result.text())
        val decoded = decodeSpans(result.text())
        assertEquals(spans.toSet(), decoded.spans.toSet())
    }

    @Test
    fun codeSpanDelimitersWidenPastContentBackticks() {
        val result = encode(paragraph("a`b", TextSpan(0, 3, SpanStyle.InlineCode)))
        // The helper widens past the internal run and pads conservatively;
        // CommonMark's strip-one-space rule undoes the padding on decode.
        assertEquals("`` a`b ``\n", result.text())
        val decoded = decodeSpans(result.text())
        assertEquals("a`b", decoded.text)
        assertEquals(listOf(TextSpan(0, 3, SpanStyle.InlineCode)), decoded.spans)
    }

    @Test
    fun italicStrictlyInsideCodeIsDroppedWithoutBridge() {
        val result = encode(
            paragraph(
                "abcd",
                TextSpan(0, 4, SpanStyle.InlineCode),
                TextSpan(1, 3, SpanStyle.Italic),
            ),
        )
        assertEquals("`abcd`\n", result.text())
        val warning = result.warnings
            .filterIsInstance<MarkdownEncodeWarning.DroppedSpanOverlap>()
            .single()
        assertEquals(MarkdownTextRange(1, 3), warning.textRange)
        val decoded = decodeSpans(result.text())
        assertEquals(listOf(TextSpan(0, 4, SpanStyle.InlineCode)), decoded.spans)
    }

    @Test
    fun newlineInCodeSpanDropsTheCodeMarks() {
        val result = encode(paragraph("a\nb", TextSpan(0, 3, SpanStyle.InlineCode)))
        assertEquals("a\\\nb\n", result.text())
        assertTrue(result.warnings.filterIsInstance<MarkdownEncodeWarning.DroppedSpanOverlap>().isNotEmpty())
        assertEquals("a\nb", decodeSpans(result.text()).text)
    }

    // Links

    @Test
    fun linkEncodesInlineFormWithStyledText() {
        val spans = listOf(
            TextSpan(0, 1, SpanStyle.Bold),
            TextSpan(0, 1, SpanStyle.Link("../g.md")),
        )
        val result = encode(paragraph("x", *spans.toTypedArray()))
        assertEquals("[**x**](../g.md)\n", result.text())
        val decoded = decodeSpans(result.text())
        assertEquals(spans.toSet(), decoded.spans.toSet())
    }

    @Test
    fun destinationNeedingAngleFormGetsIt() {
        val result = encode(paragraph("x", TextSpan(0, 1, SpanStyle.Link("http://x/(1)"))))
        assertEquals("[x](<http://x/(1)>)\n", result.text())
        val decoded = decodeSpans(result.text())
        assertEquals(listOf(TextSpan(0, 1, SpanStyle.Link("http://x/(1)"))), decoded.spans)
    }

    // Hard breaks

    @Test
    fun hardBreaksEncodePerPolicyAndDecodeBothForms() {
        val block = paragraph("a\nb")
        assertEquals("a\\\nb\n", encode(block).text())
        val twoSpaces = MarkdownProfile.Default.withHardBreakEncode(HardBreakEncode.TwoSpaces)
        assertEquals("a  \nb\n", encode(block, profile = twoSpaces).text())
        assertEquals("a\nb", decodeSpans("a\\\nb").text)
        assertEquals("a\nb", decodeSpans("a  \nb").text)
    }

    @Test
    fun spanAcrossHardBreakRoundTrips() {
        val spans = listOf(TextSpan(0, 3, SpanStyle.Bold))
        val result = encode(paragraph("a\nb", *spans.toTypedArray()))
        assertEquals("**a\\\nb**\n", result.text())
        val decoded = decodeSpans(result.text())
        assertEquals("a\nb", decoded.text)
        assertEquals(spans, decoded.spans)
    }

    // Prefixed blocks keep complete physical lines

    @Test
    fun quoteWithSpansPrefixesEveryPhysicalLine() {
        val block = Block(
            id = BlockId.generate(),
            type = BlockType.Quote,
            content = BlockContent.Text("a\nb", listOf(TextSpan(0, 3, SpanStyle.Bold))),
        )
        assertEquals("> **a\\\n> b**\n", encode(block).text())
    }

    @Test
    fun listItemsCarrySpans() {
        val block = Block(
            id = BlockId.generate(),
            type = BlockType.BulletList,
            content = BlockContent.Text("hi", listOf(TextSpan(0, 2, SpanStyle.Bold))),
        )
        assertEquals("- **hi**\n", encode(block).text())
    }

    // Heading embedded newline decision

    @Test
    fun headingEmbeddedNewlineBecomesSpaceWithDataLoss() {
        val block = Block(
            id = BlockId.generate(),
            type = BlockType.Heading(2),
            content = BlockContent.Text("a\nb", listOf(TextSpan(0, 3, SpanStyle.Bold))),
        )
        val result = encode(block)
        assertEquals("## **a b**\n", result.text())
        assertTrue(
            result.warnings.filterIsInstance<MarkdownEncodeWarning.DroppedAttribute>()
                .any { it.attr == "lineBreak" },
        )
    }

    // Span-free value exclusions must warn, never silently change (review fix)

    @Test
    fun plainTextValueExclusionsWarnInsteadOfSilentlyChanging() {
        val cases = listOf("a  \nb", "a ", "  a", "    a", "a\t")
        for (text in cases) {
            val result = encode(paragraph(text))
            val markdown = result.text()
            val warned = result.warnings.any { it.impact == MarkdownFidelityImpact.DataLoss }
            val decoded = MarkdownDecodeEngine.decode(markdown, MarkdownProfile.Default)
            val roundTrips = (decoded.blocks?.singleOrNull()?.content as? BlockContent.Text)
                ?.text == text
            assertTrue(
                warned || roundTrips,
                "\"$text\" must round-trip exactly or carry a DataLoss warning; " +
                    "got \"$markdown\" with ${result.warnings}",
            )
        }
    }

    @Test
    fun ordinaryPlainTextStillEncodesWithoutWarnings() {
        val result = encode(paragraph("plain text, nothing special"))
        assertEquals("plain text, nothing special\n", result.text())
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun textMismatchEmitsOneGenericWarningNotAmbiguousEmphasisPerSpan() {
        // Trailing spaces before the hard break cannot round-trip; the bold
        // span is innocent and must not be blamed (review fix, finding 6).
        val result = encode(paragraph("a  \nb", TextSpan(0, 1, SpanStyle.Bold)))
        assertTrue(result.warnings.none { it is MarkdownEncodeWarning.AmbiguousEmphasis })
        assertTrue(
            result.warnings.filterIsInstance<MarkdownEncodeWarning.DroppedSpanOverlap>()
                .isNotEmpty(),
        )
    }

    // Code blocks bypass the inline renderer entirely

    @Test
    fun codeBlockContentIsNeverEscaped() {
        val block = Block(
            id = BlockId.generate(),
            type = BlockType.Code,
            content = BlockContent.Text("*not em* [x](u)"),
        )
        assertEquals("```\n*not em* [x](u)\n```\n", encode(block).text())
    }
}
