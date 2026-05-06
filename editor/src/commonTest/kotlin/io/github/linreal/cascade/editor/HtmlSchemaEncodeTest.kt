package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlSchemaEncodeTest {

    @Test
    fun `default profile encodes canonical block tags`() {
        val blocks = listOf(
            Block.paragraph("Paragraph"),
            Block.heading(level = 2, text = "Heading"),
            Block(
                id = BlockId.generate(),
                type = BlockType.Quote,
                content = BlockContent.Text("Quote"),
            ),
            Block(
                id = BlockId.generate(),
                type = BlockType.Code,
                content = BlockContent.Text("a\nb"),
            ),
            Block.divider(),
        )

        val html = HtmlSchema.encode(blocks, HtmlProfile.Default)

        assertEquals(
            "<p>Paragraph</p><h2>Heading</h2><blockquote>Quote</blockquote><pre><code>a\nb</code></pre><hr>",
            html,
        )
    }

    @Test
    fun `default profile encodes heading levels one through six`() {
        val blocks = (1..6).map { level -> Block.heading(level = level, text = "H$level") }

        val html = HtmlSchema.encode(blocks, HtmlProfile.Default)

        assertEquals("<h1>H1</h1><h2>H2</h2><h3>H3</h3><h4>H4</h4><h5>H5</h5><h6>H6</h6>", html)
    }

    @Test
    fun `default profile encodes canonical span tags`() {
        val cases = listOf(
            SpanStyle.Bold to "<p><strong>x</strong>y</p>",
            SpanStyle.Italic to "<p><em>x</em>y</p>",
            SpanStyle.Underline to "<p><u>x</u>y</p>",
            SpanStyle.StrikeThrough to "<p><s>x</s>y</p>",
            SpanStyle.InlineCode to "<p><code>x</code>y</p>",
            SpanStyle.Highlight(0xFF00FF00) to
                """<p><mark data-cascade-highlight="FF00FF00">x</mark>y</p>""",
        )

        for ((style, expectedHtml) in cases) {
            val block = Block.paragraph("xy", spans = listOf(TextSpan(0, 1, style)))

            assertEquals(expectedHtml, HtmlSchema.encode(listOf(block), HtmlProfile.Default))
        }
    }

    @Test
    fun `default profile escapes text inside encoded spans`() {
        val block = Block.paragraph(
            text = "a < b & c",
            spans = listOf(TextSpan(2, 7, SpanStyle.Bold)),
        )

        val html = HtmlSchema.encode(listOf(block), HtmlProfile.Default)

        assertEquals("<p>a <strong>&lt; b &amp;</strong> c</p>", html)
    }

    @Test
    fun `default profile reopens overlapping spans in stable order`() {
        val block = Block.paragraph(
            text = "abcd",
            spans = listOf(
                TextSpan(0, 3, SpanStyle.Bold),
                TextSpan(1, 4, SpanStyle.Italic),
            ),
        )

        val html = HtmlSchema.encode(listOf(block), HtmlProfile.Default)

        assertEquals("<p><strong>a<em>bc</em></strong><em>d</em></p>", html)
    }

    @Test
    fun `default profile reopens three mutually overlapping spans in stable order`() {
        val block = Block.paragraph(
            text = "abcdefghijklmnop",
            spans = listOf(
                TextSpan(0, 10, SpanStyle.Bold),
                TextSpan(5, 15, SpanStyle.Italic),
                TextSpan(8, 12, SpanStyle.Underline),
            ),
        )

        val html = HtmlSchema.encode(listOf(block), HtmlProfile.Default)

        assertEquals(
            "<p><strong>abcde<em>fgh<u>ij</u></em></strong><em><u>kl</u>mno</em>p</p>",
            html,
        )
    }

    @Test
    fun `default profile encodes non-code newlines as br`() {
        val html = HtmlSchema.encode(listOf(Block.paragraph("a\nb")), HtmlProfile.Default)

        assertEquals("<p>a<br>b</p>", html)
    }
}
