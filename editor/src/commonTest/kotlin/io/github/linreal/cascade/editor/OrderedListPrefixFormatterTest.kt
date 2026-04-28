package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.ui.renderers.OrderedListPrefixStyle
import io.github.linreal.cascade.editor.ui.renderers.formatOrderedListPrefix
import io.github.linreal.cascade.editor.ui.renderers.resolveOrderedListPrefixStyles
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderedListPrefixFormatterTest {

    private fun numbered(id: String, depth: Int, number: Int = 1): Block {
        return Block(
            id = BlockId(id),
            type = BlockType.NumberedList(number),
            content = BlockContent.Text(id),
            attributes = BlockAttributes(indentationLevel = depth),
        )
    }

    private fun resolvedStyle(blocks: List<Block>, id: String): OrderedListPrefixStyle {
        return resolveOrderedListPrefixStyles(blocks).styleFor(BlockId(id))
    }

    private fun paragraph(id: String, depth: Int): Block {
        return Block(
            id = BlockId(id),
            type = BlockType.Paragraph,
            content = BlockContent.Text(id),
            attributes = BlockAttributes(indentationLevel = depth),
        )
    }

    @Test
    fun `formats decimal prefixes`() {
        assertEquals("3.", formatOrderedListPrefix(number = 3, style = OrderedListPrefixStyle.Decimal))
    }

    @Test
    fun `formats lower alpha prefixes`() {
        assertEquals("b.", formatOrderedListPrefix(number = 2, style = OrderedListPrefixStyle.LowerAlpha))
    }

    @Test
    fun `formats lower roman prefixes`() {
        assertEquals("iv.", formatOrderedListPrefix(number = 4, style = OrderedListPrefixStyle.LowerRoman))
    }

    @Test
    fun `resolves decimal style when numbered list has no numbered ancestor`() {
        val blocks = listOf(
            paragraph("intro", depth = 4),
            numbered("list", depth = 5, number = 1),
        )

        assertEquals(
            OrderedListPrefixStyle.Decimal,
            resolvedStyle(blocks, "list"),
        )
    }

    @Test
    fun `resolves alpha style under decimal numbered ancestor`() {
        val blocks = listOf(
            numbered("parent", depth = 0),
            paragraph("between", depth = 1),
            numbered("child", depth = 4),
        )

        assertEquals(
            OrderedListPrefixStyle.LowerAlpha,
            resolvedStyle(blocks, "child"),
        )
    }

    @Test
    fun `resolves roman style under alpha numbered ancestor`() {
        val blocks = listOf(
            numbered("parent", depth = 0),
            numbered("child", depth = 2),
            numbered("grandchild", depth = 5),
        )

        assertEquals(
            OrderedListPrefixStyle.LowerRoman,
            resolvedStyle(blocks, "grandchild"),
        )
    }

    @Test
    fun `cycles back to decimal under roman numbered ancestor`() {
        val blocks = listOf(
            numbered("parent", depth = 0),
            numbered("child", depth = 1),
            numbered("grandchild", depth = 3),
            numbered("leaf", depth = 5),
        )

        assertEquals(
            OrderedListPrefixStyle.Decimal,
            resolvedStyle(blocks, "leaf"),
        )
    }

    @Test
    fun `formats lower alpha overflow as spreadsheet sequence`() {
        assertEquals("aa.", formatOrderedListPrefix(number = 27, style = OrderedListPrefixStyle.LowerAlpha))
    }

    @Test
    fun `falls back to decimal outside supported roman range`() {
        assertEquals("4000.", formatOrderedListPrefix(number = 4000, style = OrderedListPrefixStyle.LowerRoman))
    }
}
