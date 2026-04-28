package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.IndentationDirection
import io.github.linreal.cascade.editor.core.canShiftIndentation
import io.github.linreal.cascade.editor.core.isValidIndentationOutline
import io.github.linreal.cascade.editor.core.normalizeIndentationOutline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutlineUtilsTest {

    private fun block(
        id: String,
        type: BlockType = BlockType.Paragraph,
        depth: Int = 0,
    ): Block {
        return Block(
            id = BlockId(id),
            type = type,
            content = if (type.supportsText) BlockContent.Text(id) else BlockContent.Empty,
            attributes = BlockAttributes(indentationLevel = depth),
        )
    }

    @Test
    fun `canShiftIndentation allows second supported root to move under previous supported block`() {
        val blocks = listOf(
            block("first"),
            block("second"),
        )

        assertTrue(
            canShiftIndentation(
                blocks = blocks,
                targetRootIndices = listOf(1),
                direction = IndentationDirection.Forward,
            )
        )
    }

    @Test
    fun `canShiftIndentation allows first supported block moving deeper`() {
        val blocks = listOf(
            block("first"),
            block("second"),
        )

        assertTrue(
            canShiftIndentation(
                blocks = blocks,
                targetRootIndices = listOf(0),
                direction = IndentationDirection.Forward,
            )
        )
    }

    @Test
    fun `canShiftIndentation rejects backward no-op at depth zero`() {
        val blocks = listOf(block("first"))

        assertFalse(
            canShiftIndentation(
                blocks = blocks,
                targetRootIndices = listOf(0),
                direction = IndentationDirection.Backward,
            )
        )
    }

    @Test
    fun `canShiftIndentation allows nested root subtree to move backward`() {
        val blocks = listOf(
            block("root"),
            block("child", depth = 1),
            block("grandchild", depth = 2),
        )

        assertTrue(
            canShiftIndentation(
                blocks = blocks,
                targetRootIndices = listOf(1),
                direction = IndentationDirection.Backward,
            )
        )
    }

    @Test
    fun `canShiftIndentation rejects outline with unsupported hidden indentation`() {
        val blocks = listOf(
            block("root"),
            block("heading", type = BlockType.Heading(1), depth = 1),
            block("paragraph", depth = 1),
        )

        assertFalse(
            canShiftIndentation(
                blocks = blocks,
                targetRootIndices = listOf(2),
                direction = IndentationDirection.Backward,
            )
        )
    }

    @Test
    fun `unsupported blocks are hard outline boundaries for validation`() {
        val blocks = listOf(
            block("root"),
            block("heading", type = BlockType.Heading(1)),
            block("orphan", depth = 1),
        )

        assertTrue(blocks.isValidIndentationOutline())
    }

    @Test
    fun `normalizeIndentationOutline preserves supported indentation after unsupported boundary`() {
        val blocks = listOf(
            block("root"),
            block("heading", type = BlockType.Heading(1)),
            block("orphan", depth = 1),
        )

        val normalized = normalizeIndentationOutline(blocks)

        assertEquals(listOf(0, 0, 1), normalized.map { it.attributes.indentationLevel })
        assertTrue(normalized.isValidIndentationOutline())
    }

    @Test
    fun `free indentation outline allows skipped levels and indented first block`() {
        val blocks = listOf(
            block("first", depth = 4),
            block("child", depth = 5),
            block("sibling", depth = 2),
            block("deep-sibling", depth = 4),
        )

        assertTrue(blocks.isValidIndentationOutline())
        assertEquals(blocks, normalizeIndentationOutline(blocks))
    }
}
