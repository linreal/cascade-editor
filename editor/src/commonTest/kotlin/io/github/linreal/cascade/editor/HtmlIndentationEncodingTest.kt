package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import io.github.linreal.cascade.editor.htmlserialization.TagDecodeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HtmlIndentationEncodingTest {

    @Test
    fun `cascade indentation class decodes on paragraph`() {
        val maxIndentation = BlockAttributes.MAX_INDENTATION_LEVEL
        val block = HtmlSchema.decode(
            """<p class="note cascade-indent-$maxIndentation other">Indented</p>""",
            HtmlProfile.Default,
        ).single()

        assertEquals(BlockType.Paragraph, block.type)
        assertEquals("Indented", assertTextContent(block).text)
        assertEquals(maxIndentation, block.attributes.indentationLevel)
    }

    @Test
    fun `post decode normalization clears indentation from unsupported block types`() {
        val profile = HtmlProfile.Default.withTagDecoder("x") { _, _, _ ->
            TagDecodeResult.AsBlock(
                Block(
                    id = BlockId.generate(),
                    type = BlockType.Quote,
                    content = BlockContent.Text("quote"),
                    attributes = BlockAttributes(indentationLevel = 2),
                )
            )
        }

        val block = HtmlSchema.decode("<x></x>", profile).single()

        assertEquals(BlockType.Quote, block.type)
        assertEquals(BlockAttributes.MIN_INDENTATION_LEVEL, block.attributes.indentationLevel)
    }

    @Test
    fun `default profile encodes and decodes paragraph cascade indentation class`() {
        val maxIndentation = BlockAttributes.MAX_INDENTATION_LEVEL
        val block = Block.paragraph("Indented")
            .withAttributes(BlockAttributes(indentationLevel = maxIndentation))

        val html = HtmlSchema.encode(listOf(block), HtmlProfile.Default)
        val decoded = HtmlSchema.decode(html, HtmlProfile.Default).single()

        assertEquals("""<p class="cascade-indent-$maxIndentation">Indented</p>""", html)
        assertEquals(BlockType.Paragraph, decoded.type)
        assertEquals("Indented", assertTextContent(decoded).text)
        assertEquals(maxIndentation, decoded.attributes.indentationLevel)
    }

    @Test
    fun `default profile round trips list indentation depths`() {
        val blocks = (BlockAttributes.MIN_INDENTATION_LEVEL..BlockAttributes.MAX_INDENTATION_LEVEL)
            .map { depth ->
                Block.bulletList("depth $depth")
                    .withAttributes(BlockAttributes(indentationLevel = depth))
            }

        val decoded = HtmlSchema.decode(
            HtmlSchema.encode(blocks, HtmlProfile.Default),
            HtmlProfile.Default,
        )

        assertEquals(blocks.size, decoded.size)
        decoded.forEachIndexed { index, block ->
            assertEquals(BlockType.BulletList, block.type)
            assertEquals("depth $index", assertTextContent(block).text)
            assertEquals(index, block.attributes.indentationLevel)
        }
    }

    @Test
    fun `default profile does not emit indentation class for unsupported block types`() {
        val block = Block(
            id = BlockId.generate(),
            type = BlockType.Quote,
            content = BlockContent.Text("Quote"),
            attributes = BlockAttributes(indentationLevel = 2),
        )

        val html = HtmlSchema.encode(listOf(block), HtmlProfile.Default)

        assertEquals("<blockquote>Quote</blockquote>", html)
    }

    private fun assertTextContent(block: Block): BlockContent.Text = assertIs<BlockContent.Text>(block.content)
}
