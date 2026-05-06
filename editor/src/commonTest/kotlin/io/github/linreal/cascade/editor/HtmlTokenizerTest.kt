package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.htmlserialization.EntityDecode
import io.github.linreal.cascade.editor.htmlserialization.HtmlToken
import io.github.linreal.cascade.editor.htmlserialization.HtmlTokenizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HtmlTokenizerTest {

    @Test
    fun `tokenize parses quoted unquoted and missing-value attributes`() {
        val html = """<A HREF="https://example.com" data-id='42' checked title=Hello>"""
        val token = HtmlTokenizer.tokenize(html, EntityDecode.None).single()

        val openTag = assertIs<HtmlToken.OpenTag>(token)
        assertEquals("a", openTag.name)
        assertEquals("href", openTag.attributes[0].name)
        assertEquals("https://example.com", openTag.attributes[0].value)
        assertEquals("data-id", openTag.attributes[1].name)
        assertEquals("42", openTag.attributes[1].value)
        assertEquals("checked", openTag.attributes[2].name)
        assertNull(openTag.attributes[2].value)
        assertEquals("title", openTag.attributes[3].name)
        assertEquals("Hello", openTag.attributes[3].value)
    }

    @Test
    fun `tokenize skips comments without dropping surrounding text`() {
        val tokens = HtmlTokenizer.tokenize("a<!-- hidden -->b")

        assertEquals(2, tokens.size)
        assertIs<HtmlToken.Text>(tokens[0])
        assertIs<HtmlToken.Text>(tokens[1])
        assertEquals("a", (tokens[0] as HtmlToken.Text).text)
        assertEquals("b", (tokens[1] as HtmlToken.Text).text)
    }

    @Test
    fun `tokenize skips markup declarations`() {
        val tokens = HtmlTokenizer.tokenize("<!DOCTYPE html><p>x</p>")

        assertEquals(3, tokens.size)
        val openTag = assertIs<HtmlToken.OpenTag>(tokens[0])
        assertEquals("p", openTag.name)
        assertEquals(15, openTag.sourceStart)
    }

    @Test
    fun `tokenize emits lowercased closing tags with source ranges`() {
        val tokens = HtmlTokenizer.tokenize("<p>x</P>")

        val closeTag = assertIs<HtmlToken.CloseTag>(tokens[2])
        assertEquals("p", closeTag.name)
        assertEquals(4, closeTag.sourceStart)
        assertEquals(8, closeTag.sourceEndExclusive)
    }

    @Test
    fun `tokenize decodes supported text entities while preserving source range`() {
        val html = "&amp;&lt;&#65;&#x41;&nbsp;&quot;&apos;"
        val token = HtmlTokenizer.tokenize(html, EntityDecode.Standard).single()

        val text = assertIs<HtmlToken.Text>(token)
        assertEquals("&<AA\u00A0\"'", text.text)
        assertEquals(0, text.sourceStart)
        assertEquals(html.length, text.sourceEndExclusive)
        assertEquals(html, html.substring(text.sourceStart, text.sourceEndExclusive))
    }

    @Test
    fun `tokenize leaves entities unchanged when decoding is disabled`() {
        val html = "&amp;&lt;&#65;"
        val token = HtmlTokenizer.tokenize(html, EntityDecode.None).single()

        val text = assertIs<HtmlToken.Text>(token)
        assertEquals(html, text.text)
    }

    @Test
    fun `tokenize identifies explicit self-closing tags and void elements`() {
        val tokens = HtmlTokenizer.tokenize("<br><hr /><custom />")

        val br = assertIs<HtmlToken.OpenTag>(tokens[0])
        val hr = assertIs<HtmlToken.OpenTag>(tokens[1])
        val custom = assertIs<HtmlToken.OpenTag>(tokens[2])
        assertEquals("br", br.name)
        assertTrue(br.closesImmediately)
        assertEquals("hr", hr.name)
        assertTrue(hr.closesImmediately)
        assertEquals("custom", custom.name)
        assertTrue(custom.closesImmediately)
    }

    @Test
    fun `tokenize truncated quoted attribute without throwing`() {
        val html = "<p class=\""
        val token = HtmlTokenizer.tokenize(html).single()

        val openTag = assertIs<HtmlToken.OpenTag>(token)
        assertEquals("p", openTag.name)
        assertEquals("class", openTag.attributes.single().name)
        assertEquals("", openTag.attributes.single().value)
        assertEquals(0, openTag.sourceStart)
        assertEquals(html.length, openTag.sourceEndExclusive)
    }
}
