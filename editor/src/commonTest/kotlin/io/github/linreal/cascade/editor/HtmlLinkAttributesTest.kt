package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.htmlserialization.HtmlDecodeWarning
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HtmlLinkAttributesTest {

    @Test
    fun `a href preserves bare domain target exactly`() {
        // Stored-target validation: imported hrefs are never rewritten. Previously,
        // bare domains gained an https:// prefix.
        val block = HtmlSchema.decode("""<p><a href="example.com/path">Example</a></p>""", HtmlProfile.Default).single()
        val content = assertTextContent(block)

        assertEquals("Example", content.text)
        assertEquals(
            listOf(TextSpan(0, 7, SpanStyle.Link("example.com/path"))),
            content.spans,
        )
    }

    @Test
    fun `a href preserves fragment target without scheme prefix`() {
        val block = HtmlSchema.decode("""<p><a href="#section">x</a></p>""", HtmlProfile.Default).single()
        val content = assertTextContent(block)

        assertEquals("x", content.text)
        assertEquals(listOf(TextSpan(0, 1, SpanStyle.Link("#section"))), content.spans)
    }

    @Test
    fun `a href preserves relative mailto tel and custom-scheme targets exactly`() {
        val targets = listOf("../guide.md", "/docs", "mailto:user@example.com", "tel:+123", "note://custom")
        for (target in targets) {
            val block = HtmlSchema.decode("""<p><a href="$target">x</a></p>""", HtmlProfile.Default).single()
            val content = assertTextContent(block)

            assertEquals(
                listOf(TextSpan(0, 1, SpanStyle.Link(target))),
                content.spans,
                "href $target must be preserved exactly",
            )
        }
    }

    @Test
    fun `a with blank href keeps text drops link span and reports dropped attribute`() {
        val result = HtmlSchema.decodeWithReport("""<p><a href=" ">Example</a></p>""", HtmlProfile.Default)
        val content = assertTextContent(result.blocks.single())

        assertEquals("Example", content.text)
        assertTrue(content.spans.isEmpty())
        val warning = assertIs<HtmlDecodeWarning.DroppedAttribute>(result.warnings.single())
        assertEquals("a", warning.tag)
        assertEquals("href", warning.attr)
    }

    @Test
    fun `a without href keeps text drops link span and reports dropped attribute`() {
        val result = HtmlSchema.decodeWithReport("<p><a>Example</a></p>", HtmlProfile.Default)
        val content = assertTextContent(result.blocks.single())

        assertEquals("Example", content.text)
        assertTrue(content.spans.isEmpty())
        val warning = assertIs<HtmlDecodeWarning.DroppedAttribute>(result.warnings.single())
        assertEquals("a", warning.tag)
        assertEquals("href", warning.attr)
    }

    @Test
    fun `mark with data cascade highlight decodes color span`() {
        val block = HtmlSchema.decode(
            """<p><mark data-cascade-highlight="FF00FF00">green</mark></p>""",
            HtmlProfile.Default,
        ).single()
        val content = assertTextContent(block)

        assertEquals("green", content.text)
        assertEquals(listOf(TextSpan(0, 5, SpanStyle.Highlight(0xFF00FF00))), content.spans)
    }

    @Test
    fun `plain mark decodes default yellow highlight span`() {
        val block = HtmlSchema.decode("<p><mark>yellow</mark></p>", HtmlProfile.Default).single()
        val content = assertTextContent(block)

        assertEquals("yellow", content.text)
        assertEquals(
            listOf(TextSpan(0, 6, SpanStyle.Highlight(HtmlProfile.DEFAULT_HIGHLIGHT_COLOR_ARGB))),
            content.spans,
        )
    }

    @Test
    fun `mark with invalid highlight warns and decodes default yellow highlight span`() {
        val result = HtmlSchema.decodeWithReport(
            """<p><mark data-cascade-highlight="not-a-color">yellow</mark></p>""",
            HtmlProfile.Default,
        )
        val content = assertTextContent(result.blocks.single())

        assertEquals("yellow", content.text)
        assertEquals(
            listOf(TextSpan(0, 6, SpanStyle.Highlight(HtmlProfile.DEFAULT_HIGHLIGHT_COLOR_ARGB))),
            content.spans,
        )
        val warning = assertIs<HtmlDecodeWarning.InvalidAttribute>(result.warnings.single())
        assertEquals("mark", warning.tag)
        assertEquals("data-cascade-highlight", warning.attr)
        assertEquals("not-a-color", warning.value)
        assertEquals(
            """<p><mark data-cascade-highlight="FFFFFF00">yellow</mark></p>""",
            HtmlSchema.encode(result.blocks, HtmlProfile.Default),
        )
    }

    @Test
    fun `default profile encodes link attributes in canonical order`() {
        val block = io.github.linreal.cascade.editor.core.Block.paragraph(
            text = "Example",
            spans = listOf(TextSpan(0, 7, SpanStyle.Link("""https://example.com?a=1&b="x""""))),
        )

        val html = HtmlSchema.encode(listOf(block), HtmlProfile.Default)

        assertEquals(
            """<p><a href="https://example.com?a=1&amp;b=&quot;x&quot;" rel="noreferrer">Example</a></p>""",
            html,
        )
    }

    @Test
    fun `default profile encodes highlight attribute in canonical order`() {
        val block = io.github.linreal.cascade.editor.core.Block.paragraph(
            text = "green",
            spans = listOf(TextSpan(0, 5, SpanStyle.Highlight(0xFF00FF00))),
        )

        val html = HtmlSchema.encode(listOf(block), HtmlProfile.Default)

        assertEquals("""<p><mark data-cascade-highlight="FF00FF00">green</mark></p>""", html)
    }

    private fun assertTextContent(block: io.github.linreal.cascade.editor.core.Block): BlockContent.Text =
        assertIs<BlockContent.Text>(block.content)
}
