package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.UpdateBlockText
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.registry.PolicyAwareBlockCallbacks
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadOnlyStateBoundaryTest {

    @Test
    fun `read only facade does not proxy text mutation to state holder`() {
        val blockId = BlockId("block")
        val holder = EditorStateHolder(EditorState.withBlocks(listOf(textBlock(blockId, "initial"))))
        val delegate = DefaultBlockCallbacks(
            dispatchFn = { action -> holder.dispatch(action) },
            stateProvider = { holder.state },
            stateHolder = holder,
        )
        val callbacks = PolicyAwareBlockCallbacks(delegate, EditorInteractionPolicy.ReadOnly)

        callbacks.dispatch(UpdateBlockText(blockId, "blocked"))

        assertEquals("initial", holder.textOf(blockId))
    }

    @Test
    fun `app owned direct dispatch remains mutable outside read only facade`() {
        val blockId = BlockId("block")
        val holder = EditorStateHolder(EditorState.withBlocks(listOf(textBlock(blockId, "initial"))))

        holder.dispatch(UpdateBlockText(blockId, "mutated"))

        assertEquals("mutated", holder.textOf(blockId))
    }

    @Test
    fun `app owned setState remains mutable outside read only facade`() {
        val blockId = BlockId("block")
        val holder = EditorStateHolder(EditorState.withBlocks(listOf(textBlock(blockId, "initial"))))

        holder.setState(EditorState.withBlocks(listOf(textBlock(blockId, "replacement"))))

        assertEquals("replacement", holder.textOf(blockId))
    }

    private fun textBlock(id: BlockId, text: String): Block {
        return Block(
            id = id,
            type = BlockType.Paragraph,
            content = BlockContent.Text(text),
        )
    }

    private fun EditorStateHolder.textOf(blockId: BlockId): String {
        return (state.getBlock(blockId)?.content as BlockContent.Text).text
    }
}
