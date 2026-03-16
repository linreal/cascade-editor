package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BlockTest {

    @Test
    fun `heading level must be between 1 and 6`() {
        // Valid levels
        BlockType.Heading(1)
        BlockType.Heading(6)

        // Invalid levels
        assertFailsWith<IllegalArgumentException> {
            BlockType.Heading(0)
        }
        assertFailsWith<IllegalArgumentException> {
            BlockType.Heading(7)
        }
    }

    @Test
    fun `NumberedList default number is 1`() {
        val type = BlockType.NumberedList()
        assertEquals(1, type.number)
    }

    @Test
    fun `NumberedList preserves custom number`() {
        val type = BlockType.NumberedList(number = 5)
        assertEquals(5, type.number)
    }

    @Test
    fun `NumberedList rejects number less than 1`() {
        assertFailsWith<IllegalArgumentException> {
            BlockType.NumberedList(number = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            BlockType.NumberedList(number = -1)
        }
    }

    @Test
    fun `NumberedList is pattern-matchable`() {
        val type: BlockType = BlockType.NumberedList(number = 3)
        assertIs<BlockType.NumberedList>(type)
        assertTrue(type is BlockType.NumberedList)
    }

    @Test
    fun `NumberedList data class equality`() {
        assertEquals(BlockType.NumberedList(1), BlockType.NumberedList(1))
        assertTrue(BlockType.NumberedList(1) != BlockType.NumberedList(2))
    }

    @Test
    fun `numberedList factory creates correct block`() {
        val block = Block.numberedList(text = "Item", number = 3)
        assertIs<BlockType.NumberedList>(block.type)
        assertEquals(3, (block.type as BlockType.NumberedList).number)
        assertEquals("Item", (block.content as BlockContent.Text).text)
    }

    @Test
    fun `numberedList factory defaults`() {
        val block = Block.numberedList()
        assertIs<BlockType.NumberedList>(block.type)
        assertEquals(1, (block.type as BlockType.NumberedList).number)
        assertEquals("", (block.content as BlockContent.Text).text)
    }

    @Test
    fun `block creation with text content`() {
        val block = Block(
            id = BlockId("test-1"),
            type = BlockType.Paragraph,
            content = BlockContent.Text("Hello, World!")
        )

        assertEquals(BlockId("test-1"), block.id)
        assertEquals(BlockType.Paragraph, block.type)
        assertEquals("Hello, World!", (block.content as BlockContent.Text).text)
    }
}
