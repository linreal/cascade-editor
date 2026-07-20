package io.github.linreal.cascade.editor.markdown

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownStrictProfileTest {

    private val strict = MarkdownProfile.Default.withoutHtmlBridge()

    @Test
    fun `strict profile strips inline html to text with data loss`() {
        val result = MarkdownSchema.decodeWithReport("<span foo>x</span>", strict)
        val content = result.blocks!!.single().content as BlockContent.Text
        assertEquals("x", content.text)
        assertTrue(result.warnings.any { it is MarkdownDecodeWarning.HtmlStripped })
        assertTrue(result.warnings.any { it.impact == MarkdownFidelityImpact.DataLoss })
    }

    @Test
    fun `strict profile analyze recommends raw fallback for html`() {
        val report = MarkdownSchema.analyze("<span foo>x</span>", strict)
        assertEquals(MarkdownEditModeRecommendation.RawFallback, report.recommendedMode)
        assertTrue(report.dataLossWarnings.isNotEmpty())
    }

    private val preserveHtml = MarkdownProfile.Default.withHtmlInMarkdown(
        io.github.linreal.cascade.editor.markdown.HtmlInMarkdown.Preserve,
    )

    @Test
    fun `strict profile never emits raw html through any emission path`() {
        // A document assembled from every raw-HTML-bearing shape: HTML span
        // encoders, md.preservedHtml verbatim, HTML-kind md.preserved verbatim.
        val preservedHtml = MarkdownSchema.decode("<div><b>hi</b></div>", preserveHtml)!!
        val doc = listOf(
            Block.paragraph(
                "abc",
                spans = listOf(
                    TextSpan(0, 3, SpanStyle.Underline),
                ),
            ),
            Block.paragraph(
                "def",
                spans = listOf(TextSpan(0, 3, SpanStyle.Highlight(0xFF00FF00L))),
            ),
        ) + preservedHtml

        val encoded = MarkdownSchema.encode(doc, strict) ?: error("encode aborted")
        assertFalse(encoded.contains("<u>"), "leaked <u>: $encoded")
        assertFalse(encoded.contains("<mark"), "leaked <mark>: $encoded")
        assertFalse(encoded.contains("<div"), "leaked <div>: $encoded")
        assertFalse(encoded.contains("<b>"), "leaked <b>: $encoded")
        assertFalse(encoded.contains("<script"), "leaked <script>: $encoded")
    }

    @Test
    fun `strict support set does not claim underline or highlight`() {
        assertFalse(strict.supportSet.supportsSpan(SpanStyle.Underline))
        assertFalse(strict.supportSet.supportsSpan(SpanStyle.Highlight(0xFF00FF00L)))
        assertTrue(strict.supportSet.supportsSpan(SpanStyle.Bold))
    }

    @Test
    fun `strict profile emits no raw script through the preserved-html path`() {
        // Raw <script> preserved under a Preserve profile becomes an opaque
        // md.preservedHtml block; strict encode drops it entirely.
        val withScript = MarkdownSchema.decode("<script>alert(1)</script>", preserveHtml)!!
        val content = withScript.single().content
        assertTrue(
            content is BlockContent.Custom && content.typeId == MARKDOWN_PRESERVED_HTML_TYPE_ID,
            "expected preserved html, got $content",
        )
        val encoded = MarkdownSchema.encode(withScript, strict) ?: ""
        assertFalse(encoded.contains("<script"), "leaked <script>: $encoded")
        assertFalse(encoded.contains("alert(1)"), "leaked script body: $encoded")
    }
}
