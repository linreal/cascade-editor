package io.github.linreal.cascade.editor.ui

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CascadeDocumentPreviewLogicTest {

    private val blocks = listOf(
        Block.paragraph("one").copy(id = BlockId("one")),
        Block.paragraph("two").copy(id = BlockId("two")),
        Block.paragraph("three").copy(id = BlockId("three")),
    )

    @Test
    fun `bounded preview returns exactly the ordered source prefix`() {
        assertEquals(blocks.take(2), blocks.previewPrefix(maxBlocks = 2))
    }

    @Test
    fun `unbounded preview keeps the original immutable list`() {
        assertSame(blocks, blocks.previewPrefix(maxBlocks = null))
    }

    @Test
    fun `already bounded preview avoids allocating another list`() {
        assertSame(blocks, blocks.previewPrefix(maxBlocks = blocks.size))
        assertSame(blocks, blocks.previewPrefix(maxBlocks = blocks.size + 1))
    }
}
