package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Inline phase basics — code spans, escapes, hard/soft breaks,
 * entities, and inline resource limits. Emphasis has its own directed fixture
 * file ([MarkdownEmphasisTest]).
 */
class MarkdownInlineBasicsTest {

    private fun decode(
        input: String,
        profile: MarkdownProfile = MarkdownProfile.Default,
        limits: MarkdownCodecLimits = MarkdownCodecLimits.Default,
    ): MarkdownDecodeResult = MarkdownDecodeEngine.decode(input, profile, limits)

    private fun MarkdownDecodeResult.singleText(): BlockContent.Text {
        val block = blocks?.singleOrNull()
            ?: fail("expected one block, got blocks=$blocks warnings=$warnings")
        return assertIs<BlockContent.Text>(block.content)
    }

    // Code spans

    @Test
    fun codeSpanBindsBeforeEmphasis() {
        val content = decode("`a**b`").singleText()
        assertEquals("a**b", content.text)
        assertEquals(listOf(TextSpan(0, 4, SpanStyle.InlineCode)), content.spans)
    }

    @Test
    fun codeSpanCloserMustMatchOpenerRunLength() {
        val content = decode("``a`b``").singleText()
        assertEquals("a`b", content.text)
        assertEquals(listOf(TextSpan(0, 3, SpanStyle.InlineCode)), content.spans)
    }

    @Test
    fun unmatchedBacktickRunStaysLiteralAndRestParses() {
        val content = decode("`a *b*").singleText()
        assertEquals("`a b", content.text)
        assertEquals(listOf(TextSpan(3, 4, SpanStyle.Italic)), content.spans)
    }

    @Test
    fun codeSpanStripsOneSpacePerCommonMark() {
        val content = decode("` `` `").singleText()
        assertEquals("``", content.text)
        assertEquals(listOf(TextSpan(0, 2, SpanStyle.InlineCode)), content.spans)
    }

    @Test
    fun allSpaceCodeSpanContentIsNotStripped() {
        val content = decode("` `").singleText()
        assertEquals(" ", content.text)
    }

    @Test
    fun codeSpanNormalizesInternalLineEndingToSpace() {
        val content = decode("`a\nb`").singleText()
        assertEquals("a b", content.text)
        assertEquals(listOf(TextSpan(0, 3, SpanStyle.InlineCode)), content.spans)
    }

    // Escapes

    @Test
    fun backslashEscapesAsciiPunctuation() {
        val content = decode("""\*not em\*""").singleText()
        assertEquals("*not em*", content.text)
        assertTrue(content.spans.isEmpty())
    }

    @Test
    fun backslashBeforeNonPunctuationStaysLiteral() {
        val content = decode("""a\b""").singleText()
        assertEquals("""a\b""", content.text)
    }

    // Hard and soft breaks

    @Test
    fun trailingBackslashIsAHardBreak() {
        val content = decode("a\\\nb").singleText()
        assertEquals("a\nb", content.text)
    }

    @Test
    fun twoTrailingSpacesAreAHardBreak() {
        val content = decode("a  \nb").singleText()
        assertEquals("a\nb", content.text)
    }

    @Test
    fun oneTrailingSpaceIsASoftBreak() {
        val content = decode("a \nb").singleText()
        assertEquals("a b", content.text)
    }

    @Test
    fun softBreakLowersPerPolicy() {
        assertEquals("a b", decode("a\nb").singleText().text)
        val lineBreak = MarkdownProfile.Default.withSoftBreak(SoftBreak.LineBreak)
        assertEquals("a\nb", decode("a\nb", lineBreak).singleText().text)
    }

    @Test
    fun trailingBackslashAtEndOfParagraphStaysLiteral() {
        val content = decode("foo\\").singleText()
        assertEquals("foo\\", content.text)
    }

    @Test
    fun hardBreakModeKeepsEveryNewlineLiteral() {
        val profile = MarkdownProfile.Default.withNewlineSemantics(NewlineSemantics.HardBreak)
        assertEquals("a\nb", decode("a\nb", profile).singleText().text)
    }

    // Entities

    @Test
    fun entitiesDecodeOutsideCodeSpans() {
        val content = decode("&amp; &#x41; &nbsp;").singleText()
        assertEquals("& A \u00A0", content.text)
    }

    @Test
    fun entitiesDoNotDecodeInsideCodeSpans() {
        val content = decode("`&amp;`").singleText()
        assertEquals("&amp;", content.text)
    }

    @Test
    fun unknownEntityNameStaysLiteralWithInformationalWarningAndExactRange() {
        val input = "ab &nosuch; cd"
        val result = decode(input)
        assertEquals("ab &nosuch; cd", result.singleText().text)
        val warning = result.warnings
            .filterIsInstance<MarkdownDecodeWarning.UnsupportedEntity>()
            .single()
        assertEquals("nosuch", warning.name)
        assertEquals(MarkdownFidelityImpact.Informational, warning.impact)
        assertEquals(MarkdownSourceRange(3, 11), warning.range)
    }

    @Test
    fun entityDecodeNonePassesReferencesThrough() {
        val profile = MarkdownProfile.Default.withEntityDecode(EntityDecode.None)
        val result = decode("&amp;", profile)
        assertEquals("&amp;", result.singleText().text)
        assertTrue(result.warnings.none { it is MarkdownDecodeWarning.UnsupportedEntity })
    }

    // Limits

    @Test
    fun spansPerBlockLimitAborts() {
        val result = decode(
            "*a* *b* *c*",
            limits = MarkdownCodecLimits.Default.copy(maxSpansPerBlock = 2),
        )
        assertTrue(result.isAborted)
        val fatal = result.warnings.filterIsInstance<MarkdownDecodeWarning.LimitExceeded>().single()
        assertEquals(MarkdownCodecLimitKind.SpansPerBlock, fatal.kind)
    }

    @Test
    fun totalSpansLimitAbortsAcrossBlocks() {
        val result = decode(
            "*a* *b*\n\n*c* *d*",
            limits = MarkdownCodecLimits.Default.copy(maxTotalSpans = 3),
        )
        assertTrue(result.isAborted)
        val fatal = result.warnings.filterIsInstance<MarkdownDecodeWarning.LimitExceeded>().single()
        assertEquals(MarkdownCodecLimitKind.TotalSpans, fatal.kind)
    }

    @Test
    fun delimiterRunLimitAbortsInsteadOfSlowResolution() {
        val input = "*a".repeat(300)
        val result = decode(
            input,
            limits = MarkdownCodecLimits.Default.copy(maxDelimiterRuns = 100),
        )
        assertTrue(result.isAborted)
        val fatal = result.warnings.filterIsInstance<MarkdownDecodeWarning.LimitExceeded>().single()
        assertEquals(MarkdownCodecLimitKind.DelimiterRuns, fatal.kind)
    }

    @Test
    fun adversarialDelimiterInputIsBoundedByDefaultLimits() {
        // A single 10k-character run plus thousands of small runs: both must
        // complete or abort within the limits — never hang.
        val singleRun = "*".repeat(10_000)
        val manyRuns = "*a".repeat(4_000)
        for (input in listOf(singleRun, manyRuns)) {
            val result = decode(input)
            assertTrue(result.isSuccess || result.isAborted)
        }
    }

    @Test
    fun adversarialBacktickAndBracketFloodsAreBounded() {
        // Neither construct is covered by maxDelimiterRuns; the per-text
        // backtick index and bracket-match table keep these linear (an
        // unmatched opener must never rescan to end-of-text per character).
        val floods = listOf(
            // "x " keeps the backtick floods inside a paragraph leaf — a line
            // starting with three-plus backticks is a code fence at block level.
            "x " + "`".repeat(10_000),
            "`".repeat(10_000),
            "[".repeat(10_000),
            "![".repeat(5_000),
            // Strictly increasing run lengths: every opener is unmatched.
            buildString {
                append("x ")
                for (n in 1..120) {
                    append("`".repeat(n)).append('x')
                }
            },
        )
        for (input in floods) {
            val result = decode(input)
            assertTrue(result.isSuccess || result.isAborted, "flood must complete bounded")
        }
        // An unmatched backtick flood is one maximal run — literal, no code span.
        val literal = decode("x " + "`".repeat(64)).singleText()
        assertEquals("x " + "`".repeat(64), literal.text)
        assertTrue(literal.spans.isEmpty())
    }

    // Spans land on the block

    @Test
    fun leafSpansAreNormalizedVisibleCoordinates() {
        val result = decode("x **bold** y")
        val content = result.singleText()
        assertEquals("x bold y", content.text)
        assertEquals(listOf(TextSpan(2, 6, SpanStyle.Bold)), content.spans)
    }

    @Test
    fun headingAndQuoteLeavesCarrySpans() {
        val result = decode("# a *i*\n\n> q **b**")
        val blocks = result.blocks ?: fail("aborted: ${result.warnings}")
        val heading = assertIs<BlockContent.Text>(blocks[0].content)
        assertEquals("a i", heading.text)
        assertEquals(listOf(TextSpan(2, 3, SpanStyle.Italic)), heading.spans)
        val quote = assertIs<BlockContent.Text>(blocks[1].content)
        assertEquals("q b", quote.text)
        assertEquals(listOf(TextSpan(2, 3, SpanStyle.Bold)), quote.spans)
    }

}
