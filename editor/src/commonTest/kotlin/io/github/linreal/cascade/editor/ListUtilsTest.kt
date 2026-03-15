package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.renumberNumberedLists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ListUtilsTest {

    @Test
    fun `empty list returns empty`() {
        val result = renumberNumberedLists(emptyList())
        assertEquals(0, result.size)
    }

    @Test
    fun `no numbered blocks returns same blocks`() {
        val blocks = listOf(
            Block.paragraph("a"),
            Block.bulletList("b"),
            Block.paragraph("c"),
        )
        val result = renumberNumberedLists(blocks)
        assertEquals(3, result.size)
        for (i in blocks.indices) {
            assertSame(blocks[i], result[i])
        }
    }

    @Test
    fun `single run is numbered sequentially from base`() {
        val blocks = listOf(
            Block.numberedList("a", number = 1),
            Block.numberedList("b", number = 1),
            Block.numberedList("c", number = 1),
        )
        val result = renumberNumberedLists(blocks)
        assertEquals(1, (result[0].type as BlockType.NumberedList).number)
        assertEquals(2, (result[1].type as BlockType.NumberedList).number)
        assertEquals(3, (result[2].type as BlockType.NumberedList).number)
    }

    @Test
    fun `run starting at non-1 number uses that as base`() {
        val blocks = listOf(
            Block.numberedList("a", number = 3),
            Block.numberedList("b", number = 1),
            Block.numberedList("c", number = 1),
        )
        val result = renumberNumberedLists(blocks)
        assertEquals(3, (result[0].type as BlockType.NumberedList).number)
        assertEquals(4, (result[1].type as BlockType.NumberedList).number)
        assertEquals(5, (result[2].type as BlockType.NumberedList).number)
    }

    @Test
    fun `multiple runs separated by other block types`() {
        val blocks = listOf(
            Block.numberedList("a", number = 1),
            Block.numberedList("b", number = 1),
            Block.paragraph("---"),
            Block.numberedList("c", number = 5),
            Block.numberedList("d", number = 1),
        )
        val result = renumberNumberedLists(blocks)
        // First run: 1, 2
        assertEquals(1, (result[0].type as BlockType.NumberedList).number)
        assertEquals(2, (result[1].type as BlockType.NumberedList).number)
        // Paragraph unchanged
        assertSame(blocks[2], result[2])
        // Second run: 5, 6
        assertEquals(5, (result[3].type as BlockType.NumberedList).number)
        assertEquals(6, (result[4].type as BlockType.NumberedList).number)
    }

    @Test
    fun `bullet list breaks numbered run`() {
        val blocks = listOf(
            Block.numberedList("a", number = 1),
            Block.numberedList("b", number = 1),
            Block.bulletList("bullet"),
            Block.numberedList("c", number = 1),
        )
        val result = renumberNumberedLists(blocks)
        assertEquals(1, (result[0].type as BlockType.NumberedList).number)
        assertEquals(2, (result[1].type as BlockType.NumberedList).number)
        assertSame(blocks[2], result[2]) // bullet unchanged
        assertEquals(1, (result[3].type as BlockType.NumberedList).number) // new run starts at 1
    }

    @Test
    fun `single-item run is left unchanged`() {
        val block = Block.numberedList("solo", number = 7)
        val result = renumberNumberedLists(listOf(block))
        assertEquals(1, result.size)
        assertSame(block, result[0])
    }

    @Test
    fun `already-correct numbers preserve referential equality`() {
        val blocks = listOf(
            Block.numberedList("a", number = 1),
            Block.numberedList("b", number = 2),
            Block.numberedList("c", number = 3),
        )
        val result = renumberNumberedLists(blocks)
        for (i in blocks.indices) {
            assertSame(blocks[i], result[i])
        }
    }

    @Test
    fun `mixed content with multiple runs and non-list blocks`() {
        val blocks = listOf(
            Block.paragraph("intro"),
            Block.numberedList("a", number = 1),
            Block.numberedList("b", number = 1),
            Block.heading(level = 2, text = "section"),
            Block.numberedList("c", number = 2),
            Block.numberedList("d", number = 2),
            Block.numberedList("e", number = 2),
            Block.paragraph("end"),
        )
        val result = renumberNumberedLists(blocks)
        // Paragraph unchanged
        assertSame(blocks[0], result[0])
        // First run: 1, 2
        assertEquals(1, (result[1].type as BlockType.NumberedList).number)
        assertEquals(2, (result[2].type as BlockType.NumberedList).number)
        // Heading unchanged
        assertSame(blocks[3], result[3])
        // Second run: base=2, so 2, 3, 4
        assertEquals(2, (result[4].type as BlockType.NumberedList).number)
        assertEquals(3, (result[5].type as BlockType.NumberedList).number)
        assertEquals(4, (result[6].type as BlockType.NumberedList).number)
        // Paragraph unchanged
        assertSame(blocks[7], result[7])
    }
}
