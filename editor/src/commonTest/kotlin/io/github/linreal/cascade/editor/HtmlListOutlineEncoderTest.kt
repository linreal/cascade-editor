package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlListOutlineEncoderTest {

    @Test
    fun `default list outline encoder wraps consecutive bullet items in one ul`() {
        val html = HtmlSchema.encode(
            listOf(
                Block.bulletList("one"),
                Block.bulletList("two"),
            ),
            HtmlProfile.Default,
        )

        assertEquals("<ul><li>one</li><li>two</li></ul>", html)
    }

    @Test
    fun `default list outline encoder nests mixed list types by indentation depth`() {
        val blocks = listOf(
            Block.bulletList("Bullet"),
            Block.numberedList("Numbered").withIndentation(1),
            Block.bulletList("Bullet 2"),
        )

        val html = HtmlSchema.encode(blocks, HtmlProfile.Default)

        assertEquals("<ul><li>Bullet<ol><li>Numbered</li></ol></li><li>Bullet 2</li></ul>", html)
    }

    @Test
    fun `default list outline encoder unwinds nested mixed list branches`() {
        val blocks = listOf(
            Block.bulletList("root"),
            Block.bulletList("child").withIndentation(1),
            Block.numberedList("grandchild").withIndentation(2),
            Block.numberedList("numbered child").withIndentation(1),
            Block.bulletList("next root"),
        )

        val html = HtmlSchema.encode(blocks, HtmlProfile.Default)

        assertEquals(
            "<ul><li>root<ul><li>child<ol><li>grandchild</li></ol></li></ul>" +
                "<ol><li>numbered child</li></ol></li><li>next root</li></ul>",
            html,
        )
    }

    @Test
    fun `default list outline encoder preserves spans inside list items`() {
        val block = Block.bulletList("Bold").withContent(
            BlockContent.Text(
                text = "Bold",
                spans = listOf(TextSpan(0, 4, SpanStyle.Bold)),
            )
        )

        val html = HtmlSchema.encode(listOf(block), HtmlProfile.Default)

        assertEquals("<ul><li><strong>Bold</strong></li></ul>", html)
    }

    @Test
    fun `default list outline encoder uses cascade indentation class for free list depths`() {
        val blocks = listOf(
            Block.bulletList("free").withIndentation(4),
            Block.bulletList("child").withIndentation(5),
            Block.numberedList("next").withIndentation(2),
        )

        val html = HtmlSchema.encode(blocks, HtmlProfile.Default)
        val decoded = HtmlSchema.decode(html, HtmlProfile.Default)

        assertEquals(
            """<ul><li class="cascade-indent-4">free<ul><li>child</li></ul></li>""" +
                """</ul><ol><li class="cascade-indent-2">next</li></ol>""",
            html,
        )
        assertEquals(listOf(4, 5, 2), decoded.map { it.attributes.indentationLevel })
        assertEquals(listOf(BlockType.BulletList, BlockType.BulletList, BlockType.NumberedList(1)), decoded.map { it.type })
    }

    private fun Block.withIndentation(level: Int): Block =
        withAttributes(BlockAttributes(indentationLevel = level))
}
