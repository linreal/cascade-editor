package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.htmlserialization.BlockSeparator
import io.github.linreal.cascade.editor.htmlserialization.EntityDecode
import io.github.linreal.cascade.editor.htmlserialization.HtmlDecodeWarning
import io.github.linreal.cascade.editor.htmlserialization.HtmlNode
import io.github.linreal.cascade.editor.htmlserialization.HtmlParser
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.InlineRoot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HtmlParserPolicyTest {

    @Test
    fun `InlineRoot Drop drops root inline text with warning`() {
        val result = HtmlParser.parse("hello", HtmlProfile.Default.withParserPolicy(InlineRoot.Drop))

        assertTrue(result.nodes.isEmpty())
        val warning = assertIs<HtmlDecodeWarning.DroppedContent>(result.warnings.single())
        assertEquals("Dropped root inline content", warning.reason)
        assertEquals(0, warning.charOffset)
    }

    @Test
    fun `InlineRoot Drop leaves unknown root elements for unknown-tag policy`() {
        val result = HtmlParser.parse(
            "<x-widget>hello</x-widget>",
            HtmlProfile.Default.withParserPolicy(InlineRoot.Drop),
        )

        val element = assertElement("x-widget", result.nodes.single())
        assertEquals("hello", assertText(element.children.single()).text)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `InlineRoot WrapInParagraph wraps root inline content in synthetic paragraph`() {
        val profile = HtmlProfile.Default.withParserPolicy(InlineRoot.WrapInParagraph)
        val result = HtmlParser.parse("hello <strong>world</strong>", profile)

        val paragraph = assertElement("p", result.nodes.single())
        assertEquals("hello ", assertText(paragraph.children[0]).text)
        assertEquals("strong", assertElement("strong", paragraph.children[1]).tag)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `BlockSeparator BlockTags keeps root newlines inside one wrapped inline run`() {
        val profile = HtmlProfile.Default
            .withParserPolicy(BlockSeparator.BlockTags)
            .withParserPolicy(InlineRoot.WrapInParagraph)
        val result = HtmlParser.parse("a\nb", profile)

        val paragraph = assertElement("p", result.nodes.single())
        assertEquals("a\nb", assertText(paragraph.children.single()).text)
    }

    @Test
    fun `BlockSeparator BlockTags ignores whitespace between root block tags`() {
        val profile = HtmlProfile.Default
            .withParserPolicy(BlockSeparator.BlockTags)
            .withParserPolicy(InlineRoot.WrapInParagraph)
        val result = HtmlParser.parse("<p>a</p>\n<p>b</p>", profile)

        assertEquals(
            listOf("a", "b"),
            result.nodes.map { assertText(assertElement("p", it).children.single()).text },
        )
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `BlockSeparator Newline splits root inline text on a single newline without empty paragraph`() {
        val profile = HtmlProfile.Default
            .withParserPolicy(BlockSeparator.Newline)
            .withParserPolicy(InlineRoot.WrapInParagraph)
        val result = HtmlParser.parse("a\nb", profile)

        assertEquals(
            listOf("a", "b"),
            result.nodes.map { assertText(assertElement("p", it).children.single()).text },
        )
    }

    @Test
    fun `BlockSeparator Newline splits root inline text on a decoded newline entity`() {
        val profile = HtmlProfile.Default
            .withParserPolicy(BlockSeparator.Newline)
            .withParserPolicy(InlineRoot.WrapInParagraph)
        val result = HtmlParser.parse("a&#10;b", profile)

        assertEquals(
            listOf("a", "b"),
            result.nodes.map { assertText(assertElement("p", it).children.single()).text },
        )
    }

    @Test
    fun `BlockSeparator Newline leaves newline entities literal when entity decoding is disabled`() {
        val profile = HtmlProfile.Default
            .withParserPolicy(BlockSeparator.Newline)
            .withParserPolicy(InlineRoot.WrapInParagraph)
            .withParserPolicy(EntityDecode.None)
        val result = HtmlParser.parse("a&#10;b", profile)

        val paragraph = assertElement("p", result.nodes.single())
        assertEquals("a&#10;b", assertText(paragraph.children.single()).text)
    }

    @Test
    fun `BlockSeparator Newline turns three consecutive root newlines into two empty paragraphs`() {
        val profile = HtmlProfile.Default
            .withParserPolicy(BlockSeparator.Newline)
            .withParserPolicy(InlineRoot.WrapInParagraph)
        val result = HtmlParser.parse("a\n\n\nb", profile)

        assertEquals(4, result.nodes.size)
        assertEquals("a", assertText(assertElement("p", result.nodes[0]).children.single()).text)
        assertTrue(assertElement("p", result.nodes[1]).children.isEmpty())
        assertTrue(assertElement("p", result.nodes[2]).children.isEmpty())
        assertEquals("b", assertText(assertElement("p", result.nodes[3]).children.single()).text)
    }

    @Test
    fun `BlockSeparator Newline creates empty paragraphs from consecutive decoded newline entities`() {
        val profile = HtmlProfile.Default
            .withParserPolicy(BlockSeparator.Newline)
            .withParserPolicy(InlineRoot.WrapInParagraph)
        val result = HtmlParser.parse("a&#10;&#10;b", profile)

        assertEquals(3, result.nodes.size)
        assertEquals("a", assertText(assertElement("p", result.nodes[0]).children.single()).text)
        val empty = assertElement("p", result.nodes[1])
        assertTrue(empty.children.isEmpty())
        assertEquals(6, empty.sourceStart)
        assertEquals(11, empty.sourceEndExclusive)
        assertEquals("b", assertText(assertElement("p", result.nodes[2]).children.single()).text)
    }

    @Test
    fun `BlockSeparator Newline ignores spaces and tabs around root separators`() {
        val profile = HtmlProfile.Default
            .withParserPolicy(BlockSeparator.Newline)
            .withParserPolicy(InlineRoot.WrapInParagraph)
        val result = HtmlParser.parse("a \t\n\t b", profile)

        assertEquals(
            listOf("a", "b"),
            result.nodes.map { assertText(assertElement("p", it).children.single()).text },
        )
    }

    @Test
    fun `BlockSeparator Newline ignores decoded spaces and tabs around root separators`() {
        val profile = HtmlProfile.Default
            .withParserPolicy(BlockSeparator.Newline)
            .withParserPolicy(InlineRoot.WrapInParagraph)
        val result = HtmlParser.parse("a&#32;&#9;&#10;&#9;&#32;b", profile)

        assertEquals(
            listOf("a", "b"),
            result.nodes.map { assertText(assertElement("p", it).children.single()).text },
        )
    }

    @Test
    fun `BlockSeparator Newline with InlineRoot Drop emits warnings without synthetic paragraphs`() {
        val profile = HtmlProfile.Default
            .withParserPolicy(BlockSeparator.Newline)
            .withParserPolicy(InlineRoot.Drop)
        val result = HtmlParser.parse("a\n\nb", profile)

        assertTrue(result.nodes.isEmpty())
        assertEquals(2, result.warnings.size)
        assertTrue(result.warnings.all { it is HtmlDecodeWarning.DroppedContent })
    }

    @Test
    fun `BlockSeparator Newline leaves newlines inside element children untouched`() {
        val profile = HtmlProfile.Default
            .withParserPolicy(BlockSeparator.Newline)
            .withParserPolicy(InlineRoot.WrapInParagraph)
        val result = HtmlParser.parse("<li>a\nb</li><pre>x\ny</pre>", profile)

        val li = assertElement("li", result.nodes[0])
        val pre = assertElement("pre", result.nodes[1])
        assertEquals("a\nb", assertText(li.children.single()).text)
        assertEquals("x\ny", assertText(pre.children.single()).text)
    }

    @Test
    fun `EntityDecode None passes entity references through policy pipeline`() {
        val profile = HtmlProfile.Default.withParserPolicy(EntityDecode.None)
        val result = HtmlParser.parse("<p>&amp;</p>", profile)

        val paragraph = assertElement("p", result.nodes.single())
        assertEquals("&amp;", assertText(paragraph.children.single()).text)
    }

    @Test
    fun `EntityDecode Standard decodes entity references through policy pipeline`() {
        val profile = HtmlProfile.Default.withParserPolicy(EntityDecode.Standard)
        val result = HtmlParser.parse("<p>&amp;</p>", profile)

        val paragraph = assertElement("p", result.nodes.single())
        assertEquals("&", assertText(paragraph.children.single()).text)
    }

    private fun assertElement(expectedTag: String, node: HtmlNode): HtmlNode.Element {
        val element = assertIs<HtmlNode.Element>(node)
        assertEquals(expectedTag, element.tag)
        return element
    }

    private fun assertText(node: HtmlNode): HtmlNode.Text = assertIs<HtmlNode.Text>(node)
}
