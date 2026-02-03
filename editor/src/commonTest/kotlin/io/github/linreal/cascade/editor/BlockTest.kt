package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
