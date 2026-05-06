package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import io.github.linreal.cascade.editor.htmlserialization.TagDecodeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HtmlSchemaDecodeTest {

    @Test
    fun `default profile decodes built-in block tags`() {
        val result = HtmlSchema.decodeWithReport(
            "<p>Paragraph</p><h1>Heading 1</h1><h6>Heading 6</h6>" +
                "<blockquote>Quote</blockquote><pre>Code</pre><hr>",
            HtmlProfile.Default,
        )

        assertEquals(6, result.blocks.size)
        assertTextBlock(result.blocks[0], BlockType.Paragraph, "Paragraph")
        assertTextBlock(result.blocks[1], BlockType.Heading(1), "Heading 1")
        assertTextBlock(result.blocks[2], BlockType.Heading(6), "Heading 6")
        assertTextBlock(result.blocks[3], BlockType.Quote, "Quote")
        assertTextBlock(result.blocks[4], BlockType.Code, "Code")
        assertEquals(BlockType.Divider, result.blocks[5].type)
        assertIs<BlockContent.Empty>(result.blocks[5].content)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `default profile decodes inline formatting tags and synonyms`() {
        val cases = listOf(
            "strong" to SpanStyle.Bold,
            "b" to SpanStyle.Bold,
            "em" to SpanStyle.Italic,
            "i" to SpanStyle.Italic,
            "u" to SpanStyle.Underline,
            "s" to SpanStyle.StrikeThrough,
            "strike" to SpanStyle.StrikeThrough,
            "del" to SpanStyle.StrikeThrough,
        )

        for ((tag, style) in cases) {
            val block = HtmlSchema.decode("<p>a<$tag>b</$tag>c</p>", HtmlProfile.Default).single()
            val content = assertTextContent(block)

            assertEquals("abc", content.text, "text for <$tag>")
            assertEquals(listOf(TextSpan(1, 2, style)), content.spans, "spans for <$tag>")
        }
    }

    @Test
    fun `default profile decodes nested typed lists with ul containing ol into flat normalized blocks`() {
        val blocks = HtmlSchema.decode(
            "<ul><li>One<ol><li>Nested one</li><li>Nested two</li></ol></li><li>Two</li></ul>",
            HtmlProfile.Default,
        )

        assertEquals(4, blocks.size)
        assertTextBlock(blocks[0], BlockType.BulletList, "One", indentation = 0)
        assertTextBlock(blocks[1], BlockType.NumberedList(1), "Nested one", indentation = 1)
        assertTextBlock(blocks[2], BlockType.NumberedList(2), "Nested two", indentation = 1)
        assertTextBlock(blocks[3], BlockType.BulletList, "Two", indentation = 0)
    }

    @Test
    fun `cascade indent zero on nested li overrides nesting depth`() {
        val blocks = HtmlSchema.decode(
            """<ul><li>Root<ul><li class="cascade-indent-0">Flat</li></ul></li></ul>""",
            HtmlProfile.Default,
        )

        assertEquals(2, blocks.size)
        assertTextBlock(blocks[0], BlockType.BulletList, "Root", indentation = 0)
        assertTextBlock(blocks[1], BlockType.BulletList, "Flat", indentation = 0)
    }

    @Test
    fun `default profile does not infer todo blocks from checkbox-like html`() {
        val result = HtmlSchema.decodeWithReport(
            """<ul><li><input type="checkbox" checked>Task</li></ul>""",
            HtmlProfile.Default,
        )

        val block = result.blocks.single()
        assertFalse(block.type is BlockType.Todo)
        assertEquals(BlockType.BulletList, block.type)
        assertEquals("Task", assertTextContent(block).text)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `list container decoders delegate to a profile li override`() {
        val profile = HtmlProfile.Default.withTagDecoder("li") { ctx, attrs, children ->
            val depth = attrs["class"]
                ?.let { Regex("ql-indent-(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
                ?: 0
            val inline = ctx.collectInlineText(children = children, trimEdges = true)
            TagDecodeResult.AsBlock(
                Block.bulletList(inline.text)
                    .withContent(BlockContent.Text(inline.text, inline.spans))
                    .withAttributes(
                        BlockAttributes(
                            indentationLevel = depth.coerceIn(
                                BlockAttributes.MIN_INDENTATION_LEVEL,
                                BlockAttributes.MAX_INDENTATION_LEVEL,
                            ),
                        )
                    ),
            )
        }

        val block = HtmlSchema.decode(
            """<ul><li class="ql-indent-2">Google</li></ul>""",
            profile,
        ).single()

        assertTextBlock(block, BlockType.BulletList, "Google", indentation = 2)
    }

    private fun assertTextBlock(
        block: Block,
        type: BlockType,
        text: String,
        indentation: Int = BlockAttributes.MIN_INDENTATION_LEVEL,
    ) {
        assertEquals(type, block.type)
        assertEquals(text, assertTextContent(block).text)
        assertEquals(indentation, block.attributes.indentationLevel)
    }

    private fun assertTextContent(block: Block): BlockContent.Text = assertIs<BlockContent.Text>(block.content)
}
