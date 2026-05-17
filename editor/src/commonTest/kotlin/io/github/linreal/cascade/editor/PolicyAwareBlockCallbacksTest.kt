package io.github.linreal.cascade.editor

import io.github.linreal.cascade.editor.action.AddBlockRangeToSelection
import io.github.linreal.cascade.editor.action.ApplySpanStyle
import io.github.linreal.cascade.editor.action.CancelDrag
import io.github.linreal.cascade.editor.action.ClearFocus
import io.github.linreal.cascade.editor.action.ClearSelection
import io.github.linreal.cascade.editor.action.CloseSlashCommand
import io.github.linreal.cascade.editor.action.CompleteDrag
import io.github.linreal.cascade.editor.action.ConvertBlockType
import io.github.linreal.cascade.editor.action.DeleteBlock
import io.github.linreal.cascade.editor.action.DeleteBlocks
import io.github.linreal.cascade.editor.action.DeleteSelectedOrFocused
import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.FocusBlock
import io.github.linreal.cascade.editor.action.FocusNextBlock
import io.github.linreal.cascade.editor.action.FocusPreviousBlock
import io.github.linreal.cascade.editor.action.HighlightSlashCommand
import io.github.linreal.cascade.editor.action.IndentBackward
import io.github.linreal.cascade.editor.action.IndentForward
import io.github.linreal.cascade.editor.action.InsertBlock
import io.github.linreal.cascade.editor.action.InsertBlockAfter
import io.github.linreal.cascade.editor.action.InsertBlockBefore
import io.github.linreal.cascade.editor.action.MergeBlocks
import io.github.linreal.cascade.editor.action.MoveBlocks
import io.github.linreal.cascade.editor.action.NavigateSlashBack
import io.github.linreal.cascade.editor.action.NavigateSlashSubmenu
import io.github.linreal.cascade.editor.action.OpenSlashCommand
import io.github.linreal.cascade.editor.action.RemoveSpanStyle
import io.github.linreal.cascade.editor.action.ReplaceBlock
import io.github.linreal.cascade.editor.action.SelectAll
import io.github.linreal.cascade.editor.action.SelectBlock
import io.github.linreal.cascade.editor.action.SelectBlockRange
import io.github.linreal.cascade.editor.action.SplitBlock
import io.github.linreal.cascade.editor.action.StartDrag
import io.github.linreal.cascade.editor.action.ToggleBlockSelection
import io.github.linreal.cascade.editor.action.ToggleTodo
import io.github.linreal.cascade.editor.action.UpdateBlockContent
import io.github.linreal.cascade.editor.action.UpdateBlockText
import io.github.linreal.cascade.editor.action.UpdateDragTarget
import io.github.linreal.cascade.editor.action.UpdateSlashCommandSession
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.PolicyAwareBlockCallbacks
import io.github.linreal.cascade.editor.slash.SlashCommandId
import io.github.linreal.cascade.editor.state.SlashQueryRange
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolicyAwareBlockCallbacksTest {

    @Test
    fun `editable policy forwards callback methods and dispatch`() {
        val delegate = RecordingBlockCallbacks()
        val callbacks = PolicyAwareBlockCallbacks(delegate, EditorInteractionPolicy.Editable)
        val blockId = BlockId("block")
        val action = UpdateBlockText(blockId, "updated")

        callbacks.dispatch(action)
        callbacks.onFocus(blockId)
        callbacks.onEnter(blockId, cursorPosition = 3)
        callbacks.onBackspaceAtStart(blockId)
        callbacks.onDeleteAtEnd(blockId)
        callbacks.onDragStart(blockId, touchOffsetY = 12.5f)
        callbacks.onSlashCommand(blockId, SlashQueryRange(0, 1), initialQuery = "h")
        callbacks.onClick(blockId)
        callbacks.onLongClick(blockId)

        assertEquals(
            listOf(
                RecordedCall.Dispatch(action),
                RecordedCall.Focus(blockId),
                RecordedCall.Enter(blockId, 3),
                RecordedCall.BackspaceAtStart(blockId),
                RecordedCall.DeleteAtEnd(blockId),
                RecordedCall.DragStart(blockId, 12.5f),
                RecordedCall.SlashCommand(blockId, SlashQueryRange(0, 1), "h"),
                RecordedCall.Click(blockId),
                RecordedCall.LongClick(blockId),
            ),
            delegate.calls,
        )
    }

    @Test
    fun `read only policy blocks mutating direct callbacks`() {
        val delegate = RecordingBlockCallbacks()
        val callbacks = PolicyAwareBlockCallbacks(delegate, EditorInteractionPolicy.ReadOnly)
        val blockId = BlockId("block")

        callbacks.onEnter(blockId, cursorPosition = 3)
        callbacks.onBackspaceAtStart(blockId)
        callbacks.onDeleteAtEnd(blockId)
        callbacks.onDragStart(blockId, touchOffsetY = 12.5f)
        callbacks.onSlashCommand(blockId, SlashQueryRange(0, 1), initialQuery = "h")

        assertTrue(delegate.calls.isEmpty())
    }

    @Test
    fun `read only policy forwards focus click and long click callbacks`() {
        val delegate = RecordingBlockCallbacks()
        val callbacks = PolicyAwareBlockCallbacks(delegate, EditorInteractionPolicy.ReadOnly)
        val blockId = BlockId("block")

        callbacks.onFocus(blockId)
        callbacks.onClick(blockId)
        callbacks.onLongClick(blockId)

        assertEquals(
            listOf(
                RecordedCall.Focus(blockId),
                RecordedCall.Click(blockId),
                RecordedCall.LongClick(blockId),
            ),
            delegate.calls,
        )
    }

    @Test
    fun `read only policy forwards focus and cleanup actions`() {
        val delegate = RecordingBlockCallbacks()
        val callbacks = PolicyAwareBlockCallbacks(delegate, EditorInteractionPolicy.ReadOnly)
        val allowedActions = listOf(
            FocusBlock(BlockId("block")),
            ClearFocus,
            CloseSlashCommand,
            CancelDrag,
            ClearSelection,
        )

        allowedActions.forEach(callbacks::dispatch)

        assertEquals(
            allowedActions.map { action -> RecordedCall.Dispatch(action) }.toList(),
            delegate.calls.toList(),
        )
    }

    @Test
    fun `read only policy blocks representative mutating actions`() {
        val delegate = RecordingBlockCallbacks()
        val callbacks = PolicyAwareBlockCallbacks(delegate, EditorInteractionPolicy.ReadOnly)

        representativeMutatingActions().forEach(callbacks::dispatch)

        assertTrue(delegate.calls.isEmpty())
    }

    @Test
    fun `partial policy blocks only block selection actions when selection is disabled`() {
        val delegate = RecordingBlockCallbacks()
        val callbacks = PolicyAwareBlockCallbacks(
            delegate,
            EditorInteractionPolicy.Editable.copy(canSelectBlocks = false),
        )
        val blockId = BlockId("block")
        val update = UpdateBlockText(blockId, "updated")
        val selection = ToggleBlockSelection(blockId)

        callbacks.dispatch(update)
        callbacks.dispatch(selection)

        assertEquals<List<RecordedCall>>(listOf(RecordedCall.Dispatch(update)), delegate.calls)
    }

    @Test
    fun `partial policy blocks only drag actions and direct drag start when dragging is disabled`() {
        val delegate = RecordingBlockCallbacks()
        val callbacks = PolicyAwareBlockCallbacks(
            delegate,
            EditorInteractionPolicy.Editable.copy(canDragBlocks = false),
        )
        val blockId = BlockId("block")
        val update = UpdateBlockText(blockId, "updated")
        val dragActions = listOf(
            StartDrag(blockId, touchOffsetY = 0f),
            UpdateDragTarget(targetIndex = 1),
            CompleteDrag,
        )

        callbacks.dispatch(update)
        dragActions.forEach(callbacks::dispatch)
        callbacks.onDragStart(blockId, touchOffsetY = 12.5f)

        assertEquals<List<RecordedCall>>(listOf(RecordedCall.Dispatch(update)), delegate.calls)
    }

    private fun representativeMutatingActions(): List<EditorAction> {
        val blockId = BlockId("block")
        val otherId = BlockId("other")
        val block = textBlock(otherId, "new")
        val slashRange = SlashQueryRange(0, 1)
        val slashId = SlashCommandId("slash")

        return listOf(
            InsertBlock(block),
            InsertBlockAfter(block, afterBlockId = blockId),
            InsertBlockBefore(block, beforeBlockId = blockId),
            DeleteBlocks(setOf(blockId)),
            DeleteBlock(blockId),
            DeleteSelectedOrFocused,
            UpdateBlockContent(blockId, BlockContent.Text("updated")),
            UpdateBlockText(blockId, "updated"),
            ConvertBlockType(blockId, BlockType.Heading(1)),
            MoveBlocks(setOf(blockId), toIndex = 1),
            MergeBlocks(sourceId = otherId, targetId = blockId),
            ReplaceBlock(blockId, block),
            SelectBlock(blockId),
            ToggleBlockSelection(blockId),
            SelectBlockRange(blockId, otherId),
            AddBlockRangeToSelection(blockId, otherId),
            SelectAll,
            FocusNextBlock,
            FocusPreviousBlock,
            StartDrag(blockId, touchOffsetY = 0f),
            UpdateDragTarget(targetIndex = 1),
            CompleteDrag,
            OpenSlashCommand(anchorBlockId = blockId, queryRange = slashRange),
            UpdateSlashCommandSession(query = "h", queryRange = SlashQueryRange(0, 2)),
            NavigateSlashSubmenu(slashId),
            NavigateSlashBack,
            HighlightSlashCommand(slashId),
            SplitBlock(blockId, atPosition = 1, newBlockText = "tail"),
            ToggleTodo(blockId),
            IndentForward,
            IndentBackward,
            ApplySpanStyle(blockId, rangeStart = 0, rangeEnd = 1, style = SpanStyle.Bold),
            RemoveSpanStyle(blockId, rangeStart = 0, rangeEnd = 1, style = SpanStyle.Bold),
        )
    }
}

private sealed interface RecordedCall {
    data class Dispatch(val action: EditorAction) : RecordedCall
    data class Focus(val blockId: BlockId) : RecordedCall
    data class Enter(val blockId: BlockId, val cursorPosition: Int) : RecordedCall
    data class BackspaceAtStart(val blockId: BlockId) : RecordedCall
    data class DeleteAtEnd(val blockId: BlockId) : RecordedCall
    data class Click(val blockId: BlockId) : RecordedCall
    data class LongClick(val blockId: BlockId) : RecordedCall
    data class DragStart(val blockId: BlockId, val touchOffsetY: Float) : RecordedCall
    data class SlashCommand(
        val blockId: BlockId,
        val queryRange: SlashQueryRange,
        val initialQuery: String,
    ) : RecordedCall
}

private class RecordingBlockCallbacks : BlockCallbacks {
    val calls = mutableListOf<RecordedCall>()

    override fun dispatch(action: EditorAction) {
        calls += RecordedCall.Dispatch(action)
    }

    override fun onFocus(blockId: BlockId) {
        calls += RecordedCall.Focus(blockId)
    }

    override fun onEnter(blockId: BlockId, cursorPosition: Int) {
        calls += RecordedCall.Enter(blockId, cursorPosition)
    }

    override fun onBackspaceAtStart(blockId: BlockId) {
        calls += RecordedCall.BackspaceAtStart(blockId)
    }

    override fun onDeleteAtEnd(blockId: BlockId) {
        calls += RecordedCall.DeleteAtEnd(blockId)
    }

    override fun onClick(blockId: BlockId) {
        calls += RecordedCall.Click(blockId)
    }

    override fun onLongClick(blockId: BlockId) {
        calls += RecordedCall.LongClick(blockId)
    }

    override fun onDragStart(blockId: BlockId, touchOffsetY: Float) {
        calls += RecordedCall.DragStart(blockId, touchOffsetY)
    }

    override fun onSlashCommand(
        blockId: BlockId,
        queryRange: SlashQueryRange,
        initialQuery: String,
    ) {
        calls += RecordedCall.SlashCommand(blockId, queryRange, initialQuery)
    }
}

private fun textBlock(id: BlockId, text: String): Block {
    return Block(
        id = id,
        type = BlockType.Paragraph,
        content = BlockContent.Text(text),
    )
}
