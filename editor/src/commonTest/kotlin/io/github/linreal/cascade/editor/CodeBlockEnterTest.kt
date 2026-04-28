package io.github.linreal.cascade.editor

import androidx.compose.ui.text.TextRange
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies the Code-block-specific Enter contract:
 *  - Mid-text Enter inserts `\n` instead of splitting.
 *  - Ranged Enter replaces the selection with `\n` and never exits.
 *  - Empty Code converts to Paragraph (mirrors empty root-list exit).
 *  - Trailing-blank-line Enter (cursor at end + visible text ends with `\n`)
 *    drops the trailing newline and inserts a fresh Paragraph below.
 *  - Cursor-at-end without a trailing `\n` still inserts `\n` and stays in code.
 *  - Each mutation forms exactly one structural undo entry.
 */
class CodeBlockEnterTest {

    private val codeBlockId = BlockId("code-1")

    @Test
    fun `mid-text Enter inserts newline at cursor without splitting`() {
        val harness = Harness.code(text = "line1line2", cursorPosition = 5)

        harness.callbacks.onEnter(codeBlockId, cursorPosition = 5)

        assertEquals(listOf("line1\nline2"), harness.visibleTexts())
        assertEquals(listOf(BlockType.Code), harness.blockTypes())
        assertEquals(6, harness.cursorOf(codeBlockId))
    }

    @Test
    fun `ranged Enter replaces selection with newline and does not exit at end`() {
        val harness = Harness.code(text = "line1XYZline2", selection = TextRange(5, 8))

        harness.callbacks.onEnter(codeBlockId, cursorPosition = 8)

        assertEquals(listOf("line1\nline2"), harness.visibleTexts())
        assertEquals(listOf(BlockType.Code), harness.blockTypes())
        assertEquals(6, harness.cursorOf(codeBlockId))
    }

    @Test
    fun `ranged Enter touching end does not trigger trailing-newline exit`() {
        val harness = Harness.code(text = "line1\n", selection = TextRange(3, 6))

        harness.callbacks.onEnter(codeBlockId, cursorPosition = 6)

        assertEquals(listOf("lin\n"), harness.visibleTexts())
        assertEquals(listOf(BlockType.Code), harness.blockTypes())
        assertEquals(4, harness.cursorOf(codeBlockId))
    }

    @Test
    fun `empty Code converts to Paragraph preserving block id`() {
        val harness = Harness.code(text = "", cursorPosition = 0)

        harness.callbacks.onEnter(codeBlockId, cursorPosition = 0)

        assertEquals(listOf(BlockType.Paragraph), harness.blockTypes())
        assertEquals(listOf(codeBlockId), harness.blockIds())
        assertEquals(listOf(""), harness.visibleTexts())
    }

    @Test
    fun `trailing newline Enter exits code block and inserts paragraph below`() {
        val harness = Harness.code(text = "line1\n", cursorPosition = 6)

        harness.callbacks.onEnter(codeBlockId, cursorPosition = 6)

        val ids = harness.blockIds()
        assertEquals(2, ids.size)
        assertEquals(codeBlockId, ids[0])
        val newId = ids[1]
        assertTrue(newId != codeBlockId, "new paragraph must have a fresh id")

        assertEquals(BlockType.Code, harness.blockTypes()[0])
        assertEquals(BlockType.Paragraph, harness.blockTypes()[1])
        assertEquals("line1", harness.visibleTexts()[0])
        assertEquals("", harness.visibleTexts()[1])
        assertEquals(newId, harness.stateHolder.state.focusedBlockId)
    }

    @Test
    fun `cursor at end without trailing newline still inserts newline and stays in code`() {
        val harness = Harness.code(text = "line1", cursorPosition = 5)

        harness.callbacks.onEnter(codeBlockId, cursorPosition = 5)

        assertEquals(listOf("line1\n"), harness.visibleTexts())
        assertEquals(listOf(BlockType.Code), harness.blockTypes())
        assertEquals(6, harness.cursorOf(codeBlockId))
    }

    @Test
    fun `mid-text Enter does not exit even when text contains trailing newline`() {
        val harness = Harness.code(text = "line1\nline2\n", cursorPosition = 5)

        harness.callbacks.onEnter(codeBlockId, cursorPosition = 5)

        assertEquals(listOf("line1\n\nline2\n"), harness.visibleTexts())
        assertEquals(listOf(BlockType.Code), harness.blockTypes())
        assertEquals(6, harness.cursorOf(codeBlockId))
    }

    // History — each Enter mutation forms exactly one undo step

    @Test
    fun `mid-text Enter forms one structural history entry`() {
        val harness = Harness.code(text = "line1line2", cursorPosition = 5)

        harness.callbacks.onEnter(codeBlockId, cursorPosition = 5)

        assertEquals(listOf("line1\nline2"), harness.visibleTexts())
        assertTrue(harness.stateHolder.canUndo)

        harness.stateHolder.undo()
        assertEquals(listOf("line1line2"), harness.visibleTexts())
        assertFalse(harness.stateHolder.canUndo)

        harness.stateHolder.redo()
        assertEquals(listOf("line1\nline2"), harness.visibleTexts())
    }

    @Test
    fun `ranged Enter forms one structural history entry`() {
        val harness = Harness.code(text = "line1XYZline2", selection = TextRange(5, 8))

        harness.callbacks.onEnter(codeBlockId, cursorPosition = 8)

        assertEquals(listOf("line1\nline2"), harness.visibleTexts())

        harness.stateHolder.undo()
        assertEquals(listOf("line1XYZline2"), harness.visibleTexts())

        harness.stateHolder.redo()
        assertEquals(listOf("line1\nline2"), harness.visibleTexts())
    }

    @Test
    fun `empty Code Enter forms one structural history entry`() {
        val harness = Harness.code(text = "", cursorPosition = 0)

        harness.callbacks.onEnter(codeBlockId, cursorPosition = 0)

        assertEquals(listOf(BlockType.Paragraph), harness.blockTypes())

        harness.stateHolder.undo()
        assertEquals(listOf(BlockType.Code), harness.blockTypes())

        harness.stateHolder.redo()
        assertEquals(listOf(BlockType.Paragraph), harness.blockTypes())
    }

    @Test
    fun `trailing newline exit forms one structural history entry`() {
        val harness = Harness.code(text = "line1\n", cursorPosition = 6)

        harness.callbacks.onEnter(codeBlockId, cursorPosition = 6)

        assertEquals(2, harness.blockIds().size)
        assertEquals(listOf("line1", ""), harness.visibleTexts())

        harness.stateHolder.undo()
        assertEquals(1, harness.blockIds().size)
        assertEquals(listOf("line1\n"), harness.visibleTexts())
        assertEquals(BlockType.Code, harness.blockTypes()[0])

        harness.stateHolder.redo()
        assertEquals(2, harness.blockIds().size)
        assertEquals(listOf("line1", ""), harness.visibleTexts())
    }

    // Snapshot consistency — Code Enter never re-introduces spans

    @Test
    fun `mid-text Enter keeps snapshot spans empty on Code block`() {
        val harness = Harness.code(text = "line1line2", cursorPosition = 5)

        harness.callbacks.onEnter(codeBlockId, cursorPosition = 5)

        val codeBlock = harness.stateHolder.state.getBlock(codeBlockId)
        assertNotNull(codeBlock)
        val content = codeBlock.content as BlockContent.Text
        assertEquals(emptyList(), content.spans)
    }

    private class Harness private constructor(
        initialBlocks: List<Block>,
        focusedBlockId: BlockId,
        selection: TextRange,
    ) {
        val stateHolder = EditorStateHolder(
            EditorState.withBlocks(initialBlocks).copy(focusedBlockId = focusedBlockId),
        )
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
            textStates.setSelection(focusedBlockId, selection)
        }

        fun visibleTexts(): List<String> {
            return stateHolder.state.blocks.map { block ->
                val text = block.content as? BlockContent.Text
                textStates.getVisibleText(block.id) ?: text?.text.orEmpty()
            }
        }

        fun blockIds(): List<BlockId> = stateHolder.state.blocks.map { it.id }

        fun blockTypes(): List<BlockType> = stateHolder.state.blocks.map { it.type }

        fun cursorOf(blockId: BlockId): Int? = textStates.getSelection(blockId)?.start

        companion object {
            fun code(
                text: String,
                cursorPosition: Int = text.length,
                selection: TextRange = TextRange(cursorPosition),
            ): Harness {
                val id = BlockId("code-1")
                val block = Block(
                    id = id,
                    type = BlockType.Code,
                    content = BlockContent.Text(text),
                )
                return Harness(
                    initialBlocks = listOf(block),
                    focusedBlockId = id,
                    selection = selection,
                )
            }
        }
    }
}
