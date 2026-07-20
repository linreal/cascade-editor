package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownHtmlBridgeTest {

    private fun spansOf(markdown: String): Pair<String, List<TextSpan>> {
        val block = MarkdownSchema.decode(markdown, MarkdownProfile.Default)!!.single()
        val content = block.content as BlockContent.Text
        return content.text to content.spans
    }

    @Test
    fun `inline paired tag bridges and keeps nested markdown`() {
        val result = MarkdownSchema.decodeWithReport("<u>**bold** x</u>", MarkdownProfile.Default)
        val content = result.blocks!!.single().content as BlockContent.Text
        assertEquals("bold x", content.text)
        assertTrue(content.spans.any { it.style == SpanStyle.Underline && it.start == 0 && it.end == 6 })
        assertTrue(content.spans.any { it.style == SpanStyle.Bold && it.start == 0 && it.end == 4 })
        assertTrue(result.warnings.any { it is MarkdownDecodeWarning.HtmlBridged })
    }

    @Test
    fun `underline and highlight encode as html and round-trip`() {
        val block = Block.paragraph(
            "abcdef",
            spans = listOf(
                TextSpan(0, 3, SpanStyle.Underline),
                TextSpan(3, 6, SpanStyle.Highlight(0x80FF0000L)),
            ),
        )
        val encoded = MarkdownSchema.encode(listOf(block), MarkdownProfile.Default)!!
        assertTrue(encoded.contains("<u>abc</u>"), "expected <u> in: $encoded")
        assertTrue(
            encoded.contains("<mark data-cascade-highlight=\"80FF0000\">def</mark>"),
            "expected <mark> in: $encoded",
        )
        val (text, spans) = spansOf(encoded.trimEnd('\n'))
        assertEquals("abcdef", text)
        assertTrue(spans.any { it.style == SpanStyle.Underline && it.start == 0 && it.end == 3 })
        assertTrue(spans.any { it.style is SpanStyle.Highlight && it.start == 3 && it.end == 6 })
    }

    @Test
    fun `html comment under bridge plus preserve becomes preserved html block`() {
        val result = MarkdownSchema.decodeWithReport("<!-- separator -->", MarkdownProfile.Default)
        val block = result.blocks!!.single()
        val content = block.content
        assertTrue(
            content is BlockContent.Custom && content.typeId == MARKDOWN_PRESERVED_HTML_TYPE_ID,
            "expected md.preservedHtml, got $content",
        )
        assertTrue(result.warnings.any { it is MarkdownDecodeWarning.PreservedSyntax })
    }

    @Test
    fun `bridged island with an unknown inner tag records data loss and analyzes raw fallback`() {
        val input = "<u><blink>x</blink></u>"
        val result = MarkdownSchema.decodeWithReport(input, MarkdownProfile.Default)
        val content = result.blocks!!.single().content as BlockContent.Text
        // The inner text survives with Underline, but the dropped <blink> is data loss.
        assertEquals("x", content.text)
        assertTrue(content.spans.any { it.style == SpanStyle.Underline })
        val stripped = result.warnings.filterIsInstance<MarkdownDecodeWarning.HtmlStripped>()
        assertTrue(stripped.isNotEmpty(), "expected an HtmlStripped DataLoss warning")
        assertTrue(result.warnings.any { it.impact == MarkdownFidelityImpact.DataLoss })
        assertTrue(result.warnings.none { it is MarkdownDecodeWarning.HtmlBridged })
        // Re-based range points inside the Markdown source.
        assertTrue(stripped.all { it.range.start in 0..input.length })

        val report = MarkdownSchema.analyze(input, MarkdownProfile.Default)
        assertEquals(MarkdownEditModeRecommendation.RawFallback, report.recommendedMode)
    }

    @Test
    fun `empty inline island is not a successful bridge`() {
        // <u></u> has no content: under Default (Bridge + Preserve) the leaf
        // escalates to a preserved block rather than a clean bridge.
        val result = MarkdownSchema.decodeWithReport("<u></u>", MarkdownProfile.Default)
        val content = result.blocks!!.single().content
        assertTrue(content is BlockContent.Custom && content.typeId == MARKDOWN_PRESERVED_TYPE_ID)
        assertTrue(result.warnings.none { it is MarkdownDecodeWarning.HtmlBridged })
    }

    @Test
    fun `flood of unmatched inline html opens terminates`() {
        val flood = "<a foo>text ".repeat(5000)
        val result = MarkdownSchema.decodeWithReport(flood, MarkdownProfile.Default)
        // Bounded scan: completes (aborted or success) without hanging.
        assertTrue(result.isSuccess || result.isAborted)
    }

    @Test
    fun `bold partially overlapping underline round-trips`() {
        val block = Block.paragraph(
            "abcdef",
            spans = listOf(
                TextSpan(0, 4, SpanStyle.Bold),
                TextSpan(2, 6, SpanStyle.Underline),
            ),
        )
        val encoded = MarkdownSchema.encodeWithReport(listOf(block), MarkdownProfile.Default)
        assertTrue(encoded.isSuccess)
        assertTrue(encoded.warnings.none { it.impact == MarkdownFidelityImpact.DataLoss })
        val (text, spans) = spansOf(encoded.markdown!!.trimEnd('\n'))
        assertEquals("abcdef", text)
        assertTrue(spans.any { it.style == SpanStyle.Bold && it.start == 0 && it.end == 4 })
        assertTrue(spans.any { it.style == SpanStyle.Underline && it.start == 2 && it.end == 6 })
    }
}
