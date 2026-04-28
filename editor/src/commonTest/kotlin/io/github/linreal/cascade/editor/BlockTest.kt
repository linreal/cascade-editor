package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.CustomBlockType
import io.github.linreal.cascade.editor.core.UnknownBlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
    fun `paragraph factory defaults to zero indentation`() {
        val block = Block.paragraph(text = "Paragraph")

        assertEquals(BlockAttributes.Default, block.attributes)
        assertEquals(0, block.attributes.indentationLevel)
    }

    @Test
    fun `copying numbered list with attributes preserves type and content`() {
        val block = Block.numberedList(text = "Item", number = 7)
        val attributes = BlockAttributes(indentationLevel = 2)

        val copy = block.copy(attributes = attributes)

        assertEquals(block.id, copy.id)
        assertEquals(block.type, copy.type)
        assertEquals(block.content, copy.content)
        assertEquals(attributes, copy.attributes)
    }

    @Test
    fun `built in block types expose indentation support`() {
        val customType = object : CustomBlockType {
            override val typeId: String = "callout"
            override val displayName: String = "Callout"
        }
        val supportedTypes = listOf(
            BlockType.Paragraph,
            BlockType.Todo(),
            BlockType.BulletList,
            BlockType.NumberedList(),
        )
        val unsupportedTypes: List<BlockType> = listOf(
            BlockType.Heading(1),
            BlockType.Quote,
            BlockType.Divider,
            UnknownBlockType(typeId = "future_widget", rawTypeJson = """{"typeId":"future_widget"}"""),
            customType,
        )

        supportedTypes.forEach { type ->
            assertTrue(type.supportsIndentation, "${type.displayName} should support indentation")
        }
        unsupportedTypes.forEach { type ->
            assertFalse(type.supportsIndentation, "${type.displayName} should not support indentation")
        }
    }

    @Test
    fun `unsupported block defaults to zero indentation`() {
        val block = Block(
            id = BlockId("unknown"),
            type = UnknownBlockType(typeId = "future_widget", rawTypeJson = """{"typeId":"future_widget"}"""),
            content = BlockContent.Empty,
        )

        assertEquals(0, block.attributes.indentationLevel)
    }

    @Test
    fun `BlockAttributes accepts indentation through level five`() {
        val attributes = BlockAttributes(indentationLevel = 5)

        assertEquals(5, attributes.indentationLevel)
    }

    @Test
    fun `BlockAttributes rejects indentation outside supported range`() {
        assertFailsWith<IllegalArgumentException> {
            BlockAttributes(indentationLevel = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            BlockAttributes(indentationLevel = 6)
        }
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
