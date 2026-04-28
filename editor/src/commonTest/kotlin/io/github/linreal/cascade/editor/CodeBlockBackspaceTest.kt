package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies the Code-block contract for `onBackspaceAtStart`:
 *  - Empty Code converts to Paragraph in place (same `BlockId`, `BlockContent.Text("", emptyList())`).
 *  - Non-empty Code converts to Paragraph in place, preserving multi-line text verbatim.
 *  - Each conversion forms exactly one structural undo entry.
 *  - Block selection mode is a hard guard — no mutation runs.
 */
class CodeBlockBackspaceTest {

    private val codeBlockId = BlockId("code-1")

    @Test
    fun `backspace at start of empty Code converts to Paragraph preserving id`() {
        val harness = Harness.code(text = "")

        harness.callbacks.onBackspaceAtStart(codeBlockId)

        val blocks = harness.stateHolder.state.blocks
        assertEquals(1, blocks.size)
        assertEquals(codeBlockId, blocks[0].id)
        assertEquals(BlockType.Paragraph, blocks[0].type)
        val content = blocks[0].content as BlockContent.Text
        assertEquals("", content.text)
        assertEquals(emptyList(), content.spans)
    }

    @Test
    fun `backspace at start of non-empty Code converts to Paragraph preserving multi-line text`() {
        val harness = Harness.code(text = "line1\nline2")

        harness.callbacks.onBackspaceAtStart(codeBlockId)

        val blocks = harness.stateHolder.state.blocks
        assertEquals(1, blocks.size)
        assertEquals(codeBlockId, blocks[0].id)
        assertEquals(BlockType.Paragraph, blocks[0].type)
        val content = blocks[0].content as BlockContent.Text
        assertEquals("line1\nline2", content.text)
        assertEquals(emptyList(), content.spans)
    }

    @Test
    fun `backspace at start preserves trailing newline verbatim`() {
        val harness = Harness.code(text = "line1\n")

        harness.callbacks.onBackspaceAtStart(codeBlockId)

        val blocks = harness.stateHolder.state.blocks
        assertEquals(1, blocks.size)
        assertEquals(BlockType.Paragraph, blocks[0].type)
        val content = blocks[0].content as BlockContent.Text
        assertEquals("line1\n", content.text)
        assertEquals(emptyList(), content.spans)
    }

    @Test
    fun `backspace conversion forms one structural history entry`() {
        val harness = Harness.code(text = "line1\nline2")

        harness.callbacks.onBackspaceAtStart(codeBlockId)

        assertEquals(BlockType.Paragraph, harness.stateHolder.state.blocks[0].type)
        assertTrue(harness.stateHolder.canUndo)

        harness.stateHolder.undo()
        assertEquals(BlockType.Code, harness.stateHolder.state.blocks[0].type)
        val content = harness.stateHolder.state.blocks[0].content as BlockContent.Text
        assertEquals("line1\nline2", content.text)
        assertFalse(harness.stateHolder.canUndo)

        harness.stateHolder.redo()
        assertEquals(BlockType.Paragraph, harness.stateHolder.state.blocks[0].type)
    }

    @Test
    fun `backspace is a no-op when block selection is active`() {
        val harness = Harness.codeWithSelection(text = "line1\nline2")

        harness.callbacks.onBackspaceAtStart(codeBlockId)

        // Block stays a Code block with original text. No history entry was pushed.
        val blocks = harness.stateHolder.state.blocks
        assertEquals(1, blocks.size)
        assertEquals(BlockType.Code, blocks[0].type)
        val content = blocks[0].content as BlockContent.Text
        assertEquals("line1\nline2", content.text)
        assertFalse(harness.stateHolder.canUndo)
    }

    private class Harness private constructor(initialState: EditorState) {
        val stateHolder = EditorStateHolder(initialState)
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()
        val callbacks = DefaultBlockCallbacks(
            dispatchFn = { action -> stateHolder.dispatch(action) },
            stateProvider = { stateHolder.state },
            textStates = textStates,
            spanStates = spanStates,
            stateHolder = stateHolder,
        )

        init {
            stateHolder.bindHistoryRuntime(textStates, spanStates)
            stateHolder.state.blocks.forEach { block ->
                val textContent = block.content as? BlockContent.Text ?: return@forEach
                textStates.getOrCreate(block.id, textContent.text)
                spanStates.getOrCreate(block.id, textContent.spans, textContent.text.length)
            }
        }

        companion object {
            fun code(text: String): Harness {
                val id = BlockId("code-1")
                val block = Block(
                    id = id,
                    type = BlockType.Code,
                    content = BlockContent.Text(text),
                )
                val state = EditorState.withBlocks(listOf(block)).copy(focusedBlockId = id)
                return Harness(state)
            }

            fun codeWithSelection(text: String): Harness {
                val id = BlockId("code-1")
                val block = Block(
                    id = id,
                    type = BlockType.Code,
                    content = BlockContent.Text(text),
                )
                val state = EditorState.withBlocks(listOf(block)).copy(
                    focusedBlockId = null,
                    selectedBlockIds = setOf(id),
                )
                return Harness(state)
            }
        }
    }
}
