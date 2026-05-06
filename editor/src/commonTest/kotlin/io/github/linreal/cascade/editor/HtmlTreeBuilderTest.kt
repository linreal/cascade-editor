package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.htmlserialization.HtmlDecodeWarning
import io.github.linreal.cascade.editor.htmlserialization.HtmlNode
import io.github.linreal.cascade.editor.htmlserialization.HtmlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HtmlTreeBuilderTest {

    @Test
    fun `parse nested html preserves order and lowercases tag and attribute names`() {
        val result = HtmlParser.parse("""<DIV Data-ID="42">Hello <SPAN>World</SPAN></DIV>""")

        assertTrue(result.warnings.isEmpty())
        val div = assertIs<HtmlNode.Element>(result.nodes.single())
        assertEquals("div", div.tag)
        assertEquals(mapOf("data-id" to "42"), div.attrs)
        assertEquals("Hello ", assertIs<HtmlNode.Text>(div.children[0]).text)
        val span = assertIs<HtmlNode.Element>(div.children[1])
        assertEquals("span", span.tag)
        assertEquals("World", assertIs<HtmlNode.Text>(span.children.single()).text)
    }

    @Test
    fun `parse drops comments from the node tree`() {
        val result = HtmlParser.parse("<p>a<!-- hidden -->b</p>")

        val p = assertIs<HtmlNode.Element>(result.nodes.single())
        assertEquals(listOf("a", "b"), p.children.map { assertIs<HtmlNode.Text>(it).text })
    }

    @Test
    fun `parse drops markup declarations from the node tree`() {
        val result = HtmlParser.parse("<!DOCTYPE html><p>x</p>")

        val p = assertIs<HtmlNode.Element>(result.nodes.single())
        assertEquals("p", p.tag)
        assertEquals("x", assertIs<HtmlNode.Text>(p.children.single()).text)
    }

    @Test
    fun `parse represents void elements without requiring closing tags`() {
        val result = HtmlParser.parse("<p>a<br>b<hr></p>")

        assertTrue(result.warnings.isEmpty())
        val p = assertIs<HtmlNode.Element>(result.nodes.single())
        assertEquals("a", assertIs<HtmlNode.Text>(p.children[0]).text)
        assertEquals("br", assertIs<HtmlNode.Element>(p.children[1]).tag)
        assertEquals("b", assertIs<HtmlNode.Text>(p.children[2]).text)
        assertEquals("hr", assertIs<HtmlNode.Element>(p.children[3]).tag)
    }

    @Test
    fun `parse truncated input succeeds with an unclosed tag warning`() {
        val html = "<p class=\""
        val result = HtmlParser.parse(html)

        val p = assertIs<HtmlNode.Element>(result.nodes.single())
        assertEquals("p", p.tag)
        assertEquals(mapOf("class" to ""), p.attrs)
        assertEquals(0, p.sourceStart)
        assertEquals(html.length, p.sourceEndExclusive)
        assertEquals(
            HtmlDecodeWarning.UnclosedTag(tag = "p", charOffset = 0),
            result.warnings.single(),
        )
    }

    @Test
    fun `parse stray closing tags records a warning and keeps surrounding text`() {
        val result = HtmlParser.parse("a</span>b")

        assertEquals(listOf("a", "b"), result.nodes.map { assertIs<HtmlNode.Text>(it).text })
        assertEquals(
            HtmlDecodeWarning.StrayClosingTag(tag = "span", charOffset = 1),
            result.warnings.single(),
        )
    }

    @Test
    fun `parse mismatched nesting straightens the tree and records a warning`() {
        val result = HtmlParser.parse("<b><i></b></i>")

        val b = assertIs<HtmlNode.Element>(result.nodes.single())
        val i = assertIs<HtmlNode.Element>(b.children.single())
        assertEquals("b", b.tag)
        assertEquals("i", i.tag)
        assertTrue(
            result.warnings.any {
                it == HtmlDecodeWarning.MismatchedNesting(
                    expected = "i",
                    found = "b",
                    charOffset = 6,
                )
            }
        )
    }

    @Test
    fun `parse multi-level mismatched nesting suppresses redundant counterpart closes`() {
        val result = HtmlParser.parse("<a><b><c><a></c></a></a>")

        val outerA = assertIs<HtmlNode.Element>(result.nodes.single())
        val b = assertIs<HtmlNode.Element>(outerA.children.single())
        val c = assertIs<HtmlNode.Element>(b.children.single())
        val innerA = assertIs<HtmlNode.Element>(c.children.single())

        assertEquals("a", outerA.tag)
        assertEquals("b", b.tag)
        assertEquals("c", c.tag)
        assertEquals("a", innerA.tag)
        assertEquals(
            listOf(
                HtmlDecodeWarning.MismatchedNesting(expected = "a", found = "c", charOffset = 12),
                HtmlDecodeWarning.MismatchedNesting(expected = "b", found = "a", charOffset = 16),
            ),
            result.warnings,
        )
    }
}
