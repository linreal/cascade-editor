package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.htmlserialization.Html
import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlEscapingTest {

    // escapeText — only & < > are escaped; quotes and apostrophes are preserved.

    @Test
    fun `escapeText escapes ampersand less-than greater-than only`() {
        assertEquals("Tom &amp; Jerry &lt; &gt;", Html.escapeText("Tom & Jerry < >"))
    }

    @Test
    fun `escapeText leaves quotes and apostrophes alone`() {
        val input = """She said "hi" and 'bye'"""
        assertEquals(input, Html.escapeText(input))
    }

    @Test
    fun `escapeText returns the same string when no escapable chars are present`() {
        val input = "plain ASCII text 123"
        assertEquals(input, Html.escapeText(input))
    }

    @Test
    fun `escapeText handles empty input`() {
        assertEquals("", Html.escapeText(""))
    }

    @Test
    fun `escapeText escapes ampersand before any later sigil`() {
        // & must be escaped first so already-escaped sigils don't become "&amp;lt;".
        assertEquals("&amp;&lt;", Html.escapeText("&<"))
    }

    @Test
    fun `escapeText preserves non-escapable Unicode`() {
        val input = "héllo 😀 wörld"
        assertEquals(input, Html.escapeText(input))
    }

    // escapeAttr — & < > " ' are all escaped.

    @Test
    fun `escapeAttr escapes all attribute-sensitive characters`() {
        val input = """a & b < c > d " e ' f"""
        val expected = "a &amp; b &lt; c &gt; d &quot; e &#39; f"
        assertEquals(expected, Html.escapeAttr(input))
    }

    @Test
    fun `escapeAttr returns the same string when no escapable chars are present`() {
        val input = "plain-attr_value 123"
        assertEquals(input, Html.escapeAttr(input))
    }

    @Test
    fun `escapeAttr handles empty input`() {
        assertEquals("", Html.escapeAttr(""))
    }

    @Test
    fun `escapeAttr escapes ampersand before any later sigil`() {
        assertEquals("""&amp;&quot;&#39;""", Html.escapeAttr("""&"'"""))
    }
}
