package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.serialization.DocumentSchema
import io.github.linreal.cascade.editor.serialization.loadFromJson
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextEntry
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorCheckpoint
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.EditingUiState
import io.github.linreal.cascade.editor.state.HistoryDirection
import io.github.linreal.cascade.editor.state.HistoryManager
import io.github.linreal.cascade.editor.state.StructuralEntry
import io.github.linreal.cascade.editor.state.resolveReplayTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EditorHistoryTest {

    @Test
    fun `holder undo redo traverses structural history in order`() {
        val blockId = BlockId("b1")
        val holder = EditorStateHolder(
            EditorState.withBlocks(listOf(textBlock(blockId, "three")))
        )

        holder.pushHistoryEntry(structuralEntry(blockId, beforeText = "one", afterText = "two"))
        holder.pushHistoryEntry(structuralEntry(blockId, beforeText = "two", afterText = "three"))

        assertTrue(holder.canUndo)
        assertFalse(holder.canRedo)

        holder.undo()
        assertEquals("two", holder.currentText())
        assertTrue(holder.canUndo)
        assertTrue(holder.canRedo)

        holder.undo()
        assertEquals("one", holder.currentText())
        assertFalse(holder.canUndo)
        assertTrue(holder.canRedo)

        holder.redo()
        assertEquals("two", holder.currentText())
        assertTrue(holder.canUndo)
        assertTrue(holder.canRedo)

        holder.redo()
        assertEquals("three", holder.currentText())
        assertTrue(holder.canUndo)
        assertFalse(holder.canRedo)
    }

    @Test
    fun `fresh pushed edit invalidates redo`() {
        val blockId = BlockId("b1")
        val holder = EditorStateHolder(
            EditorState.withBlocks(listOf(textBlock(blockId, "three")))
        )

        holder.pushHistoryEntry(structuralEntry(blockId, beforeText = "one", afterText = "two"))
        holder.pushHistoryEntry(structuralEntry(blockId, beforeText = "two", afterText = "three"))

        holder.undo()
        assertTrue(holder.canRedo)

        holder.pushHistoryEntry(structuralEntry(blockId, beforeText = "two", afterText = "branch"))

        assertFalse(holder.canRedo)
        assertTrue(holder.canUndo)
    }

    @Test
    fun `history manager trims oldest undo entries to max depth`() {
        val manager = HistoryManager(maxDepth = 2)
        val blockId = BlockId("b1")
        val replayedBeforeTexts = mutableListOf<String>()

        manager.push(structuralEntry(blockId, beforeText = "one", afterText = "two"))
        manager.push(structuralEntry(blockId, beforeText = "two", afterText = "three"))
        manager.push(structuralEntry(blockId, beforeText = "three", afterText = "four"))

        assertEquals(2, manager.undoDepth)
        assertFalse(manager.canRedo)

        manager.undo { entry ->
            replayedBeforeTexts += (entry as StructuralEntry).before.singleText()
        }
        manager.undo { entry ->
            replayedBeforeTexts += (entry as StructuralEntry).before.singleText()
        }

        assertEquals(listOf("three", "two"), replayedBeforeTexts)
        assertFalse(manager.canUndo)
        assertEquals(2, manager.redoDepth)
    }

    @Test
    fun `setState clears undo redo history`() {
        val blockId = BlockId("b1")
        val holder = EditorStateHolder(
            EditorState.withBlocks(listOf(textBlock(blockId, "two")))
        )

        holder.pushHistoryEntry(structuralEntry(blockId, beforeText = "one", afterText = "two"))
        assertTrue(holder.canUndo)

        holder.setState(EditorState.withBlocks(listOf(textBlock(blockId, "replacement"))))

        assertFalse(holder.canUndo)
        assertFalse(holder.canRedo)
        assertEquals("replacement", holder.currentText())
    }

    @Test
    fun `undo and redo are ignored during active replay`() {
        val blockId = BlockId("b1")
        val holder = EditorStateHolder(
            EditorState.withBlocks(listOf(textBlock(blockId, "two")))
        )

        holder.pushHistoryEntry(structuralEntry(blockId, beforeText = "one", afterText = "two"))

        holder.withHistoryReplay {
            holder.undo()
        }

        assertEquals("two", holder.currentText())
        assertTrue(holder.canUndo)
        assertFalse(holder.canRedo)

        holder.undo()
        assertEquals("one", holder.currentText())
        assertFalse(holder.canUndo)
        assertTrue(holder.canRedo)

        holder.withHistoryReplay {
            holder.redo()
        }

        assertEquals("one", holder.currentText())
        assertFalse(holder.canUndo)
        assertTrue(holder.canRedo)
    }

    @Test
    fun `loadFromJson clears undo redo history`() {
        val blockId = BlockId("b1")
        val holder = EditorStateHolder(
            EditorState.withBlocks(listOf(textBlock(blockId, "two")))
        )
        val textStates = BlockTextStates()
        val spanStates = BlockSpanStates()

        holder.pushHistoryEntry(structuralEntry(blockId, beforeText = "one", afterText = "two"))
        assertTrue(holder.canUndo)

        val json = DocumentSchema.encodeToString(
            listOf(textBlock(blockId, "loaded"))
        )

        holder.loadFromJson(json, textStates, spanStates)

        assertFalse(holder.canUndo)
        assertFalse(holder.canRedo)
        assertEquals("loaded", holder.currentText())
    }

    @Test
    fun `history manager ignores no op entries`() {
        val manager = HistoryManager()
        val blockId = BlockId("b1")

        manager.push(structuralEntry(blockId, beforeText = "same", afterText = "same"))

        assertFalse(manager.canUndo)
        assertFalse(manager.canRedo)
        assertEquals(0, manager.undoDepth)
    }

    @Test
    fun `history manager restores stack positions when undo replay fails`() {
        val manager = HistoryManager()
        val blockId = BlockId("b1")
        val failure = IllegalStateException("boom")

        manager.push(structuralEntry(blockId, beforeText = "one", afterText = "two"))

        val thrown = assertFailsWith<IllegalStateException> {
            manager.undo { throw failure }
        }

        assertSame(failure, thrown)
        assertTrue(manager.canUndo)
        assertFalse(manager.canRedo)
        assertEquals(1, manager.undoDepth)
        assertEquals(0, manager.redoDepth)
    }

    @Test
    fun `block text entry replay patches target block only and preserves untouched identity`() {
        val targetId = BlockId("target")
        val untouchedId = BlockId("untouched")
        val currentTarget = textBlock(targetId, "after")
        val untouchedBlock = textBlock(untouchedId, "same")
        val entry = BlockTextEntry(
            blockId = targetId,
            before = BlockContent.Text("before"),
            after = BlockContent.Text("after"),
            uiBefore = editingUiState(targetId),
            uiAfter = editingUiState(targetId),
        )

        val (resolvedBlocks, resolvedUi) = entry.resolveReplayTarget(
            currentBlocks = listOf(currentTarget, untouchedBlock),
            direction = HistoryDirection.Undo,
        )

        assertEquals("before", (resolvedBlocks[0].content as BlockContent.Text).text)
        assertSame(untouchedBlock, resolvedBlocks[1])
        assertEquals(targetId, resolvedUi.focusedBlockId)
    }

    @Test
    fun `block text entry replay fails when target block is missing`() {
        val targetId = BlockId("missing")
        val entry = BlockTextEntry(
            blockId = targetId,
            before = BlockContent.Text("before"),
            after = BlockContent.Text("after"),
            uiBefore = editingUiState(targetId),
            uiAfter = editingUiState(targetId),
        )

        val thrown = assertFailsWith<IllegalArgumentException> {
            entry.resolveReplayTarget(
                currentBlocks = listOf(textBlock(BlockId("other"), "other")),
                direction = HistoryDirection.Undo,
            )
        }

        assertTrue(thrown.message?.contains("missing") == true)
    }

    private fun structuralEntry(
        blockId: BlockId,
        beforeText: String,
        afterText: String,
    ): StructuralEntry {
        return StructuralEntry(
            before = checkpoint(blockId, beforeText),
            after = checkpoint(blockId, afterText),
        )
    }

    private fun checkpoint(
        blockId: BlockId,
        text: String,
    ): EditorCheckpoint {
        return EditorCheckpoint(
            blocks = listOf(textBlock(blockId, text)),
            ui = editingUiState(blockId),
        )
    }

    private fun editingUiState(blockId: BlockId): EditingUiState {
        return EditingUiState(
            focusedBlockId = blockId,
            focusedTextSelection = null,
            focusedPendingStyles = emptySet(),
        )
    }

    private fun textBlock(
        id: BlockId,
        text: String,
    ): Block {
        return Block(
            id = id,
            type = BlockType.Paragraph,
            content = BlockContent.Text(text),
        )
    }

    private fun EditorStateHolder.currentText(): String {
        return state.blocks.singleText()
    }

    private fun EditorCheckpoint.singleText(): String {
        return blocks.singleText()
    }

    private fun List<Block>.singleText(): String {
        return (single().content as BlockContent.Text).text
    }
}
