package io.github.linreal.cascade.editor.htmlserialization

import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HtmlFragmentDecoderTest {

    @Test
    fun `inline entry point decodes paired inline tags to spans`() {
        val result = HtmlFragmentDecoder.decodeInlineFragment("<u>x</u>", HtmlProfile.Default)

        assertEquals("x", result.fragment.text)
        assertEquals(listOf(TextSpan(0, 1, SpanStyle.Underline)), result.fragment.spans)
    }

    @Test
    fun `inline entry point keeps surrounding root text`() {
        val result = HtmlFragmentDecoder.decodeInlineFragment("a <em>b</em> c", HtmlProfile.Default)

        assertEquals("a b c", result.fragment.text)
        assertEquals(listOf(TextSpan(2, 3, SpanStyle.Italic)), result.fragment.spans)
    }

    @Test
    fun `block entry point decodes a blockquote fragment to a quote block`() {
        val result = HtmlFragmentDecoder.decodeBlockFragment(
            "<blockquote><p>a</p></blockquote>",
            HtmlProfile.Default,
        )

        val block = result.blocks.single()
        assertEquals(BlockType.Quote, block.type)
        val content = assertIs<BlockContent.Text>(block.content)
        assertEquals("a", content.text)
    }

    @Test
    fun `block entry point flushes root inline runs into paragraphs`() {
        val result = HtmlFragmentDecoder.decodeBlockFragment(
            "loose <b>text</b><p>para</p>",
            HtmlProfile.Default,
        )

        assertEquals(2, result.blocks.size)
        val first = assertIs<BlockContent.Text>(result.blocks[0].content)
        assertEquals("loose text", first.text)
        assertEquals(listOf(TextSpan(6, 10, SpanStyle.Bold)), first.spans)
        val second = assertIs<BlockContent.Text>(result.blocks[1].content)
        assertEquals("para", second.text)
    }

    @Test
    fun `block entry point ignores whitespace-only root text between blocks`() {
        val result = HtmlFragmentDecoder.decodeBlockFragment(
            "<p>a</p>\n  <p>b</p>\n",
            HtmlProfile.Default,
        )

        assertEquals(2, result.blocks.size)
        assertEquals("a", assertIs<BlockContent.Text>(result.blocks[0].content).text)
        assertEquals("b", assertIs<BlockContent.Text>(result.blocks[1].content).text)
    }

    @Test
    fun `block entry point keeps significant whitespace between root inline elements`() {
        val result = HtmlFragmentDecoder.decodeBlockFragment(
            "<b>a</b> <i>b</i>",
            HtmlProfile.Default,
        )

        val paragraph = assertIs<BlockContent.Text>(result.blocks.single().content)
        assertEquals("a b", paragraph.text)
        assertEquals(
            listOf(TextSpan(0, 1, SpanStyle.Bold), TextSpan(2, 3, SpanStyle.Italic)),
            paragraph.spans,
        )
    }

    @Test
    fun `text leaf reparser hook is invoked for inline text leaves`() {
        val seen = mutableListOf<String>()
        val result = HtmlFragmentDecoder.decodeInlineFragment(
            "<u>ab</u>",
            HtmlProfile.Default,
            textLeafReparser = { text ->
                seen += text
                InlineFragment(text.uppercase(), emptyList())
            },
        )

        assertEquals(listOf("ab"), seen)
        assertEquals("AB", result.fragment.text)
        assertEquals(listOf(TextSpan(0, 2, SpanStyle.Underline)), result.fragment.spans)
    }

    @Test
    fun `text leaf reparser can contribute spans under outer tags`() {
        // Simulates the bridge handing "**bold**" back to a Markdown inline
        // parser: the hook returns a shorter text plus its own span, and the
        // outer <u> decoder wraps the combined fragment.
        val result = HtmlFragmentDecoder.decodeInlineFragment(
            "<u>**bold**</u>",
            HtmlProfile.Default,
            textLeafReparser = { text ->
                if (text == "**bold**") {
                    InlineFragment("bold", listOf(TextSpan(0, 4, SpanStyle.Bold)))
                } else {
                    InlineFragment(text, emptyList())
                }
            },
        )

        assertEquals("bold", result.fragment.text)
        assertEquals(
            setOf(
                TextSpan(0, 4, SpanStyle.Bold),
                TextSpan(0, 4, SpanStyle.Underline),
            ),
            result.fragment.spans.toSet(),
        )
    }

    @Test
    fun `throwing reparser degrades to verbatim text with a decoder exception warning`() {
        val result = HtmlFragmentDecoder.decodeInlineFragment(
            "<u>ab</u>",
            HtmlProfile.Default,
            textLeafReparser = { error("hook boom") },
        )

        assertEquals("ab", result.fragment.text)
        assertEquals(listOf(TextSpan(0, 2, SpanStyle.Underline)), result.fragment.spans)
        val warning = assertIs<HtmlDecodeWarning.DecoderException>(result.warnings.single())
        assertTrue(warning.message.contains("hook boom"))
    }

    @Test
    fun `block entry point applies the reparser to paragraph text leaves`() {
        val seen = mutableListOf<String>()
        HtmlFragmentDecoder.decodeBlockFragment(
            "<p>inner</p>",
            HtmlProfile.Default,
            textLeafReparser = { text ->
                seen += text
                InlineFragment(text, emptyList())
            },
        )

        assertEquals(listOf("inner"), seen)
    }

    @Test
    fun `empty fragment decodes to empty results without warnings`() {
        val blockResult = HtmlFragmentDecoder.decodeBlockFragment("", HtmlProfile.Default)
        val inlineResult = HtmlFragmentDecoder.decodeInlineFragment("", HtmlProfile.Default)

        assertTrue(blockResult.blocks.isEmpty())
        assertTrue(blockResult.warnings.isEmpty())
        assertEquals("", inlineResult.fragment.text)
        assertTrue(inlineResult.fragment.spans.isEmpty())
        assertTrue(inlineResult.warnings.isEmpty())
    }
}
