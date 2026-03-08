package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.ui.collectTextBlockIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpanLifecycleIntegrationTest {

    @Test
    fun `collectTextBlockIds returns only text-capable blocks with text content`() {
        val textId = BlockId("text")
        val dividerId = BlockId("divider")
        val inconsistentId = BlockId("inconsistent")

        val blocks = listOf(
            Block(
                id = textId,
                type = BlockType.Paragraph,
                content = BlockContent.Text("Hello")
            ),
            Block(
                id = dividerId,
                type = BlockType.Divider,
                content = BlockContent.Empty
            ),
            // Guards against accidental inclusion when model data is inconsistent.
            Block(
                id = inconsistentId,
                type = BlockType.Divider,
                content = BlockContent.Text("Should not be treated as text")
            )
        )

        assertEquals(setOf(textId), collectTextBlockIds(blocks))
    }

    @Test
    fun `span lifecycle cleanup removes runtime spans when block transitions to non-text`() {
        val blockId = BlockId("block-1")
        val holder = BlockSpanStates()
        holder.getOrCreate(
            blockId = blockId,
            initialSpans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
            textLength = 5,
        )
        holder.setPendingStyles(blockId, setOf(SpanStyle.Italic))

        val blocksAfterTransition = listOf(
            Block(
                id = blockId,
                type = BlockType.Divider,
                content = BlockContent.Empty
            )
        )

        holder.cleanup(collectTextBlockIds(blocksAfterTransition))

        assertEquals(emptyList(), holder.getSpans(blockId))
        assertNull(holder.getPendingStyles(blockId))
    }

    @Test
    fun `text block re-created with same id is initialized from snapshot spans after cleanup`() {
        val blockId = BlockId("block-1")
        val holder = BlockSpanStates()

        holder.getOrCreate(
            blockId = blockId,
            initialSpans = listOf(TextSpan(0, 5, SpanStyle.Bold)),
            textLength = 5,
        )
        holder.cleanup(emptySet())

        val restoredState = holder.getOrCreate(
            blockId = blockId,
            initialSpans = listOf(TextSpan(1, 4, SpanStyle.Italic)),
            textLength = 5,
        )

        assertEquals(listOf(TextSpan(1, 4, SpanStyle.Italic)), restoredState.value)
    }
}
