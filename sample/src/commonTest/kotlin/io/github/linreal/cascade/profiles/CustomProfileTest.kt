package io.github.linreal.cascade.profiles

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.htmlserialization.HtmlEncodeWarning
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CustomProfileTest {

    @Test
    fun `decodes the documented Custom sample layout`() {
        val blocks = HtmlSchema.decode(CustomHtmlSamples.DialectHtml, CustomHtmlProfile.Profile)

        assertEquals(15, blocks.size)
        assertTextBlock(blocks[0], BlockType.Paragraph, "Hello world!")
        assertTextBlock(blocks[1], BlockType.BulletList, "List")
        assertTextBlock(blocks[2], BlockType.BulletList, "")
        assertTextBlock(blocks[3], BlockType.NumberedList(1), "Numeric")
        assertTextBlock(blocks[4], BlockType.NumberedList(2), "")
        assertTextBlock(blocks[5], BlockType.NumberedList(3), "")
        assertTextBlock(blocks[6], BlockType.Paragraph, "bold", listOf(TextSpan(0, 4, SpanStyle.Bold)))
        assertTextBlock(blocks[7], BlockType.Paragraph, "italic", listOf(TextSpan(0, 6, SpanStyle.Italic)))
        assertTextBlock(blocks[8], BlockType.Paragraph, "strike", listOf(TextSpan(0, 6, SpanStyle.StrikeThrough)))
        assertTextBlock(
            blocks[9],
            BlockType.Paragraph,
            "all",
            listOf(
                TextSpan(0, 3, SpanStyle.Bold),
                TextSpan(0, 3, SpanStyle.Italic),
                TextSpan(0, 3, SpanStyle.StrikeThrough),
            ),
        )
        assertTextBlock(
            blocks[10],
            BlockType.Paragraph,
            "Link",
            listOf(TextSpan(0, 4, SpanStyle.Link("https://www.google.com"))),
        )
        assertTextBlock(blocks[11], BlockType.Paragraph, "Code", listOf(TextSpan(0, 4, SpanStyle.InlineCode)))
        assertTextBlock(blocks[12], BlockType.Paragraph, "Normal text")
        assertTextBlock(blocks[13], BlockType.Code, "Code block\nCode Block")
        assertTextBlock(blocks[14], BlockType.BulletList, "content", indentation = 2)
    }

    @Test
    fun `exports supplied dialect sample as Custom canonical html`() {
        val html = HtmlSchema.encode(
            HtmlSchema.decode(CustomHtmlSamples.DialectHtml, CustomHtmlProfile.Profile),
            CustomHtmlProfile.Profile,
        )

        assertEquals(CustomHtmlSamples.CanonicalHtml, html)
    }

    @Test
    fun `canonical Custom html is byte-stable through decode encode`() {
        val html = HtmlSchema.encode(
            HtmlSchema.decode(CustomHtmlSamples.CanonicalHtml, CustomHtmlProfile.Profile),
            CustomHtmlProfile.Profile,
        )

        assertEquals(CustomHtmlSamples.CanonicalHtml, html)
    }

    @Test
    fun `decodes concrete ql indent classes on list items`() {
        val block = HtmlSchema.decode(
            """<ul><li class="ql-indent-2">Nested</li></ul>""",
            CustomHtmlProfile.Profile,
        ).single()

        assertTextBlock(block, BlockType.BulletList, "Nested", indentation = 2)
    }

    @Test
    fun `non numeric ql indent falls back to root indentation`() {
        val block = HtmlSchema.decode(
            """<li class="ql-indent-N">Placeholder</li>""",
            CustomHtmlProfile.Profile,
        ).single()

        assertTextBlock(block, BlockType.BulletList, "Placeholder", indentation = 0)
    }

    @Test
    fun `encodes Custom canonical span tags and link attributes`() {
        val block = Block.paragraph(
            text = "bold italic strike code link",
            spans = listOf(
                TextSpan(0, 4, SpanStyle.Bold),
                TextSpan(5, 11, SpanStyle.Italic),
                TextSpan(12, 18, SpanStyle.StrikeThrough),
                TextSpan(19, 23, SpanStyle.InlineCode),
                TextSpan(24, 28, SpanStyle.Link("https://example.com?a=1&b=<x>")),
            ),
        )

        val html = HtmlSchema.encode(listOf(block), CustomHtmlProfile.Profile)

        assertEquals(
            "<strong>bold</strong> <em>italic</em> <s>strike</s> <code>code</code> " +
                """<a rel="nofollow noreferrer noopener" target="_blank" href="https://example.com?a=1&amp;b=&lt;x&gt;">link</a>""" +
                "\n",
            html,
        )
    }

    @Test
    fun `encodes consecutive bullet lists as one flat Custom list`() {
        val blocks = listOf(
            Block.bulletList("Root"),
            Block.bulletList("Nested").withIndentation(2),
        )

        val html = HtmlSchema.encode(blocks, CustomHtmlProfile.Profile)

        assertEquals(
            """
            <ul><li>Root
            </li><li class="ql-indent-2">Nested
            </li></ul>
            """.trimIndent(),
            html,
        )
    }

    @Test
    fun `encodes consecutive numbered lists as one flat Custom list`() {
        val blocks = listOf(
            numberedList("One", number = 1),
            numberedList("Nested", number = 1, indentation = 1),
            numberedList("Nested sibling", number = 2, indentation = 1),
            numberedList("Two", number = 2),
        )

        val html = HtmlSchema.encode(blocks, CustomHtmlProfile.Profile)

        assertEquals(
            """
            <ol><li>One
            </li><li class="ql-indent-1">Nested
            </li><li class="ql-indent-1">Nested sibling
            </li><li>Two
            </li></ol>
            """.trimIndent(),
            html,
        )
    }

    @Test
    fun `encodes Custom paragraphs as root inline runs`() {
        val html = HtmlSchema.encode(
            listOf(
                Block.paragraph("plain"),
                Block.paragraph("bold", spans = listOf(TextSpan(0, 4, SpanStyle.Bold))),
            ),
            CustomHtmlProfile.Profile,
        )

        assertEquals("plain\n<strong>bold</strong>\n", html)
    }

    @Test
    fun `encodes Custom code blocks as pre without nested code tag`() {
        val html = HtmlSchema.encode(listOf(codeBlock("Code block\nCode Block")), CustomHtmlProfile.Profile)

        assertEquals("<pre>Code block\nCode Block\n</pre>\n", html)
    }

    @Test
    fun `drops paragraph indentation with encode warning`() {
        val block = Block.paragraph("Indented").withIndentation(1)

        val result = HtmlSchema.encodeWithReport(listOf(block), CustomHtmlProfile.Profile)
        val decoded = HtmlSchema.decode(result.html, CustomHtmlProfile.Profile).single()

        assertEquals("Indented\n", result.html)
        assertEquals(
            HtmlEncodeWarning.DroppedAttribute(
                typeId = "paragraph",
                attr = "indentationLevel",
                reason = "Custom HTML only supports indentation on list items",
            ),
            result.warnings.single(),
        )
        assertEquals(0, decoded.attributes.indentationLevel)
    }

    @Test
    fun `mixed-type nested outlines encode as flat sibling list containers`() {
        val blocks = listOf(
            Block.bulletList("Root"),
            numberedList("Child", number = 1, indentation = 1),
            Block.bulletList("Grandchild").withIndentation(2),
        )

        val html = HtmlSchema.encode(blocks, CustomHtmlProfile.Profile)
        val roundTripped = HtmlSchema.decode(html, CustomHtmlProfile.Profile)

        assertEquals(
            """
            <ul><li>Root
            </li></ul><ol><li class="ql-indent-1">Child
            </li></ol><ul><li class="ql-indent-2">Grandchild
            </li></ul>
            """.trimIndent(),
            html,
        )
        assertFalse(html.contains("<li>Root<ol>"))
        assertTextBlock(roundTripped[0], BlockType.BulletList, "Root")
        assertTextBlock(roundTripped[1], BlockType.NumberedList(1), "Child", indentation = 1)
        assertTextBlock(roundTripped[2], BlockType.BulletList, "Grandchild", indentation = 2)
    }

    @Test
    fun `support set matches Custom round-trip claims`() {
        val supportSet = CustomHtmlProfile.Profile.supportSet

        assertTrue(supportSet.supportsBlock(Block.paragraph("plain")))
        assertFalse(supportSet.supportsBlock(Block.paragraph("indented").withIndentation(1)))
        assertTrue(supportSet.supportsBlock(Block.bulletList("nested").withIndentation(3)))
        assertTrue(supportSet.supportsBlock(numberedList("nested", number = 1, indentation = 3)))
        assertTrue(supportSet.supportsBlock(codeBlock("code")))
        assertFalse(supportSet.supportsBlock(Block.heading(level = 1, text = "Heading")))
        assertFalse(supportSet.supportsBlock(Block.divider()))
        assertTrue(supportSet.supportsSpan(SpanStyle.Bold))
        assertTrue(supportSet.supportsSpan(SpanStyle.Link("https://example.com")))
        assertFalse(supportSet.supportsSpan(SpanStyle.Underline))
        assertFalse(supportSet.supportsSpan(SpanStyle.Highlight(0xFFFF_FF00L)))
    }

    private fun assertTextBlock(
        block: Block,
        type: BlockType,
        text: String,
        spans: List<TextSpan> = emptyList(),
        indentation: Int = 0,
    ) {
        assertEquals(type, block.type)
        val content = assertIs<BlockContent.Text>(block.content)
        assertEquals(text, content.text)
        assertEquals(spans.sortedBy { it.style.toString() }, content.spans.sortedBy { it.style.toString() })
        assertEquals(indentation, block.attributes.indentationLevel)
    }

    private fun numberedList(
        text: String,
        number: Int,
        indentation: Int = 0,
    ): Block = Block(
        id = BlockId.generate(),
        type = BlockType.NumberedList(number),
        content = BlockContent.Text(text),
        attributes = BlockAttributes(indentationLevel = indentation),
    )

    private fun codeBlock(text: String): Block = Block(
        id = BlockId.generate(),
        type = BlockType.Code,
        content = BlockContent.Text(text),
    )

}

private fun Block.withIndentation(indentation: Int): Block =
    withAttributes(BlockAttributes(indentationLevel = indentation))
