package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.indentation.IndentationStateCalculator
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndentationStateCalculatorTest {

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

    private fun state(
        blocks: List<Block>,
        focusedBlockId: BlockId? = null,
        selectedBlockIds: Set<BlockId> = emptySet(),
    ): EditorState {
        return EditorState.withBlocks(blocks).copy(
            focusedBlockId = focusedBlockId,
            selectedBlockIds = selectedBlockIds,
        )
    }

    @Test
    fun `no focus and no selection disables indentation with empty targets`() {
        val result = IndentationStateCalculator.compute(
            state(blocks = listOf(block("a"), block("b"))),
        )

        assertFalse(result.canIndentForward)
        assertFalse(result.canIndentBackward)
        assertEquals(emptyList(), result.targetBlockIds)
    }

    @Test
    fun `focused supported block that can move deeper enables indent forward`() {
        val first = block("first")
        val second = block("second")

        val result = IndentationStateCalculator.compute(
            state(
                blocks = listOf(first, second),
                focusedBlockId = second.id,
            ),
        )

        assertTrue(result.canIndentForward)
        assertFalse(result.canIndentBackward)
        assertEquals(listOf(second.id), result.targetBlockIds)
    }

    @Test
    fun `selected supported blocks deeper than root enable both indentation directions`() {
        val root = block("root")
        val firstChild = block("first-child", depth = 1)
        val secondChild = block("second-child", depth = 1)

        val result = IndentationStateCalculator.compute(
            state(
                blocks = listOf(root, firstChild, secondChild),
                selectedBlockIds = setOf(secondChild.id, firstChild.id),
            ),
        )

        assertTrue(result.canIndentForward)
        assertTrue(result.canIndentBackward)
        assertEquals(listOf(firstChild.id, secondChild.id), result.targetBlockIds)
    }

    @Test
    fun `selected unsupported blocks only disable indentation with empty targets`() {
        val heading = block("heading", type = BlockType.Heading(1))
        val divider = block("divider", type = BlockType.Divider)

        val result = IndentationStateCalculator.compute(
            state(
                blocks = listOf(heading, divider),
                selectedBlockIds = setOf(heading.id, divider.id),
            ),
        )

        assertFalse(result.canIndentForward)
        assertFalse(result.canIndentBackward)
        assertEquals(emptyList(), result.targetBlockIds)
    }

    @Test
    fun `selected supported child under selected supported parent is excluded from targets`() {
        val root = block("root")
        val parent = block("parent")
        val child = block("child", depth = 1)

        val result = IndentationStateCalculator.compute(
            state(
                blocks = listOf(root, parent, child),
                selectedBlockIds = setOf(parent.id, child.id),
            ),
        )

        assertTrue(result.canIndentForward)
        assertFalse(result.canIndentBackward)
        assertEquals(listOf(parent.id), result.targetBlockIds)
    }

    @Test
    fun `indent forward is enabled after unsupported boundary`() {
        val heading = block("heading", type = BlockType.Heading(1))
        val paragraph = block("paragraph")

        val result = IndentationStateCalculator.compute(
            state(
                blocks = listOf(heading, paragraph),
                focusedBlockId = paragraph.id,
            ),
        )

        assertTrue(result.canIndentForward)
        assertFalse(result.canIndentBackward)
        assertEquals(listOf(paragraph.id), result.targetBlockIds)
    }

    @Test
    fun `read-only policy disables indentation booleans for otherwise indentable target`() {
        val first = block("first")
        val second = block("second")

        val result = IndentationStateCalculator.compute(
            state = state(
                blocks = listOf(first, second),
                focusedBlockId = second.id,
            ),
            policy = EditorInteractionPolicy.ReadOnly,
        )

        assertFalse(result.canIndentForward)
        assertFalse(result.canIndentBackward)
        assertEquals(listOf(second.id), result.targetBlockIds)
    }
}
