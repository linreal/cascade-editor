package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.htmlserialization.HtmlProfile
import io.github.linreal.cascade.editor.htmlserialization.HtmlSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HtmlPerDecoderWhitespaceTest {

    @Test
    fun `paragraph heading and quote trim edges and collapse spaces and tabs`() {
        val blocks = HtmlSchema.decode(
            "<p>  Alpha   Beta\tGamma  </p>" +
                "<h2>\n  Heading   Text \n</h2>" +
                "<blockquote>\tQuoted   Text </blockquote>",
            HtmlProfile.Default,
        )

        assertEquals("Alpha Beta Gamma", assertTextContent(blocks[0]).text)
        assertEquals("Heading Text", assertTextContent(blocks[1]).text)
        assertEquals("Quoted Text", assertTextContent(blocks[2]).text)
    }

    @Test
    fun `list item trims edges and one trailing newline`() {
        val block = HtmlSchema.decode("<ul><li>  Item\n</li></ul>", HtmlProfile.Default).single()

        assertEquals(BlockType.BulletList, block.type)
        assertEquals("Item", assertTextContent(block).text)
    }

    @Test
    fun `pre preserves internal whitespace and drops one trailing newline`() {
        val block = HtmlSchema.decode("<pre>  a   b\tc\n</pre>", HtmlProfile.Default).single()

        assertEquals(BlockType.Code, block.type)
        assertEquals("  a   b\tc", assertTextContent(block).text)
    }

    private fun assertTextContent(block: io.github.linreal.cascade.editor.core.Block): BlockContent.Text =
        assertIs<BlockContent.Text>(block.content)
}
