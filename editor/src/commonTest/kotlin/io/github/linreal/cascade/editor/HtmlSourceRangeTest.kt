package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.htmlserialization.HtmlNode
import io.github.linreal.cascade.editor.htmlserialization.HtmlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HtmlSourceRangeTest {

    @Test
    fun `element offsets are UTF-16 half-open offsets into the original source`() {
        val html = "😀<P data-x=\"1\">x</P>"
        val nodes = HtmlParser.parse(html).nodes
        val leadingText = assertIs<HtmlNode.Text>(nodes[0])
        val element = assertIs<HtmlNode.Element>(nodes[1])

        assertEquals("😀", leadingText.text)
        assertEquals(2, element.sourceStart)
        assertEquals(html.length, element.sourceEndExclusive)
        assertEquals("""<P data-x="1">x</P>""", html.substring(element.sourceStart, element.sourceEndExclusive))
    }

    @Test
    fun `decoded text keeps source range of original entity reference`() {
        val html = "<p>&amp;</p>"
        val p = assertIs<HtmlNode.Element>(HtmlParser.parse(html).nodes.single())
        val text = assertIs<HtmlNode.Text>(p.children.single())

        assertEquals("&", text.text)
        assertEquals(3, text.sourceStart)
        assertEquals(8, text.sourceEndExclusive)
        assertEquals("&amp;", html.substring(text.sourceStart, text.sourceEndExclusive))
    }
}
