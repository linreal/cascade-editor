package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.renumberNumberedLists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ListUtilsTest {

    private fun Block.atDepth(level: Int): Block = copy(
        attributes = BlockAttributes(indentationLevel = level),
    )

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
    fun `parent numbered sequence continues across nested numbered descendants`() {
        val blocks = listOf(
            Block.numberedList("parent 1", number = 1).atDepth(0),
            Block.numberedList("child 1", number = 1).atDepth(1),
            Block.numberedList("child 2", number = 1).atDepth(1),
            Block.numberedList("parent 2", number = 1).atDepth(0),
        )

        val result = renumberNumberedLists(blocks)

        assertEquals(1, (result[0].type as BlockType.NumberedList).number)
        assertEquals(1, (result[1].type as BlockType.NumberedList).number)
        assertEquals(2, (result[2].type as BlockType.NumberedList).number)
        assertEquals(2, (result[3].type as BlockType.NumberedList).number)
    }

    @Test
    fun `same depth numbered children under different parents start separate sequences`() {
        val blocks = listOf(
            Block.numberedList("parent 1", number = 1).atDepth(0),
            Block.numberedList("child of parent 1", number = 1).atDepth(1),
            Block.numberedList("parent 2", number = 2).atDepth(0),
            Block.numberedList("child of parent 2", number = 2).atDepth(1),
        )

        val result = renumberNumberedLists(blocks)

        assertEquals(1, (result[0].type as BlockType.NumberedList).number)
        assertEquals(1, (result[1].type as BlockType.NumberedList).number)
        assertEquals(2, (result[2].type as BlockType.NumberedList).number)
        assertEquals(1, (result[3].type as BlockType.NumberedList).number)
    }

    @Test
    fun `same depth paragraph breaks numbered sequence at that depth`() {
        val blocks = listOf(
            Block.numberedList("parent 1", number = 1).atDepth(0),
            Block.numberedList("child 1", number = 1).atDepth(1),
            Block.paragraph("child separator").atDepth(1),
            Block.numberedList("child restart", number = 2).atDepth(1),
            Block.numberedList("parent 2", number = 1).atDepth(0),
        )

        val result = renumberNumberedLists(blocks)

        assertEquals(1, (result[0].type as BlockType.NumberedList).number)
        assertEquals(1, (result[1].type as BlockType.NumberedList).number)
        assertSame(blocks[2], result[2])
        assertEquals(1, (result[3].type as BlockType.NumberedList).number)
        assertEquals(2, (result[4].type as BlockType.NumberedList).number)
    }

    @Test
    fun `deeper paragraph does not break parent depth numbered sequence`() {
        val blocks = listOf(
            Block.numberedList("parent 1", number = 1).atDepth(0),
            Block.paragraph("nested paragraph").atDepth(1),
            Block.numberedList("parent 2", number = 1).atDepth(0),
        )

        val result = renumberNumberedLists(blocks)

        assertEquals(1, (result[0].type as BlockType.NumberedList).number)
        assertSame(blocks[1], result[1])
        assertEquals(2, (result[2].type as BlockType.NumberedList).number)
    }

    @Test
    fun `unsupported block resets outline numbering scope`() {
        val blocks = listOf(
            Block.numberedList("parent 1", number = 1),
            Block.numberedList("child 1", number = 1).atDepth(1),
            Block.heading(level = 2, text = "Boundary"),
            Block.numberedList("parent restart", number = 2),
            Block.numberedList("child restart", number = 2).atDepth(1),
        )

        val result = renumberNumberedLists(blocks)

        assertEquals(1, (result[0].type as BlockType.NumberedList).number)
        assertEquals(1, (result[1].type as BlockType.NumberedList).number)
        assertSame(blocks[2], result[2])
        assertEquals(1, (result[3].type as BlockType.NumberedList).number)
        assertEquals(1, (result[4].type as BlockType.NumberedList).number)
    }

    @Test
    fun `already correct outline numbering preserves referential equality`() {
        val blocks = listOf(
            Block.numberedList("parent 1", number = 1).atDepth(0),
            Block.numberedList("child 1", number = 1).atDepth(1),
            Block.numberedList("child 2", number = 2).atDepth(1),
            Block.numberedList("parent 2", number = 2).atDepth(0),
        )

        val result = renumberNumberedLists(blocks)

        for (i in blocks.indices) {
            assertSame(blocks[i], result[i])
        }
    }

    @Test
    fun `run starting at non-1 number is renumbered from 1`() {
        val blocks = listOf(
            Block.numberedList("a", number = 3),
            Block.numberedList("b", number = 1),
            Block.numberedList("c", number = 1),
        )
        val result = renumberNumberedLists(blocks)
        assertEquals(1, (result[0].type as BlockType.NumberedList).number)
        assertEquals(2, (result[1].type as BlockType.NumberedList).number)
        assertEquals(3, (result[2].type as BlockType.NumberedList).number)
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
        // Second run: always starts from 1
        assertEquals(1, (result[3].type as BlockType.NumberedList).number)
        assertEquals(2, (result[4].type as BlockType.NumberedList).number)
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
    fun `single-item run with non-1 number is renumbered to 1`() {
        val block = Block.numberedList("solo", number = 7)
        val result = renumberNumberedLists(listOf(block))
        assertEquals(1, result.size)
        assertEquals(1, (result[0].type as BlockType.NumberedList).number)
    }

    @Test
    fun `single-item run with number 1 is left unchanged`() {
        val block = Block.numberedList("solo", number = 1)
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
        // Second run: always starts from 1
        assertEquals(1, (result[4].type as BlockType.NumberedList).number)
        assertEquals(2, (result[5].type as BlockType.NumberedList).number)
        assertEquals(3, (result[6].type as BlockType.NumberedList).number)
        // Paragraph unchanged
        assertSame(blocks[7], result[7])
    }

    @Test
    fun `renumbering after indenting numbered sibling into child sequence restarts child number`() {
        val blocksAfterIndent = listOf(
            Block.numberedList("parent 1", number = 1).atDepth(0),
            Block.numberedList("nested", number = 2).atDepth(1),
            Block.numberedList("parent 2", number = 3).atDepth(0),
        )

        val result = renumberNumberedLists(blocksAfterIndent)

        assertEquals(1, (result[0].type as BlockType.NumberedList).number)
        assertEquals(1, (result[1].type as BlockType.NumberedList).number)
        assertEquals(2, (result[2].type as BlockType.NumberedList).number)
    }

    @Test
    fun `renumbering after outdenting numbered child joins parent sequence`() {
        val blocksAfterOutdent = listOf(
            Block.numberedList("parent 1", number = 1).atDepth(0),
            Block.numberedList("promoted child", number = 1).atDepth(0),
            Block.numberedList("parent 2", number = 2).atDepth(0),
        )

        val result = renumberNumberedLists(blocksAfterOutdent)

        assertEquals(1, (result[0].type as BlockType.NumberedList).number)
        assertEquals(2, (result[1].type as BlockType.NumberedList).number)
        assertEquals(3, (result[2].type as BlockType.NumberedList).number)
    }
}
