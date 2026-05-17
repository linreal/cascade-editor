package io.github.linreal.cascade.editor

import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.action.EditorAction
import io.github.linreal.cascade.editor.action.StartDrag
import io.github.linreal.cascade.editor.action.ToggleBlockSelection
import io.github.linreal.cascade.editor.action.ToggleTodo
import io.github.linreal.cascade.editor.action.UpdateDragTarget
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.SlashQueryRange
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy
import io.github.linreal.cascade.editor.ui.dispatchBlockDragStartIfAllowed
import io.github.linreal.cascade.editor.ui.dispatchBlockLongPressSelectionIfDragDisabled
import io.github.linreal.cascade.editor.ui.dispatchBlockSelectionToggleIfAllowed
import io.github.linreal.cascade.editor.ui.dispatchDragTargetUpdateIfAllowed
import io.github.linreal.cascade.editor.ui.focusLastTextBlockFromEmptySpace
import io.github.linreal.cascade.editor.ui.shouldInstallBlockGestureInput
import io.github.linreal.cascade.editor.ui.shouldRenderDragAffordances
import io.github.linreal.cascade.editor.ui.renderers.createTodoCheckedChangeAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadOnlyBlockAffordancesTest {

    private val blockId = BlockId("block")

    @Test
    fun `read-only policy does not install editor-owned block gesture input`() {
        assertFalse(shouldInstallBlockGestureInput(EditorInteractionPolicy.ReadOnly))
    }

    @Test
    fun `editable policy installs editor-owned block gesture input`() {
        assertTrue(shouldInstallBlockGestureInput(EditorInteractionPolicy.Editable))
    }

    @Test
    fun `read-only long press does not start block drag`() {
        val callbacks = RecordingCallbacks()

        val dispatched = dispatchBlockDragStartIfAllowed(
            policy = EditorInteractionPolicy.ReadOnly,
            callbacks = callbacks,
            blockId = blockId,
            touchOffsetY = 12f,
        )

        assertFalse(dispatched)
        assertEquals(emptyList<DragStartCall>(), callbacks.dragStarts)
    }

    @Test
    fun `editable long press starts block drag`() {
        val callbacks = RecordingCallbacks()

        val dispatched = dispatchBlockDragStartIfAllowed(
            policy = EditorInteractionPolicy.Editable,
            callbacks = callbacks,
            blockId = blockId,
            touchOffsetY = 12f,
        )

        assertTrue(dispatched)
        assertEquals(listOf(DragStartCall(blockId, 12f)), callbacks.dragStarts)
    }

    @Test
    fun `read-only selection gestures do not toggle block selection`() {
        val callbacks = RecordingCallbacks()

        val dispatched = dispatchBlockSelectionToggleIfAllowed(
            policy = EditorInteractionPolicy.ReadOnly,
            callbacks = callbacks,
            blockId = blockId,
        )

        assertFalse(dispatched)
        assertEquals(emptyList<EditorAction>(), callbacks.actions)
    }

    @Test
    fun `editable selection gestures toggle block selection`() {
        val callbacks = RecordingCallbacks()

        val dispatched = dispatchBlockSelectionToggleIfAllowed(
            policy = EditorInteractionPolicy.Editable,
            callbacks = callbacks,
            blockId = blockId,
        )

        assertTrue(dispatched)
        assertEquals<List<EditorAction>>(listOf(ToggleBlockSelection(blockId)), callbacks.actions)
    }

    @Test
    fun `selection-only policy toggles block selection from long press without drag`() {
        val callbacks = RecordingCallbacks()
        val policy = EditorInteractionPolicy.Editable.copy(canDragBlocks = false)

        val dispatched = dispatchBlockLongPressSelectionIfDragDisabled(
            policy = policy,
            callbacks = callbacks,
            blockId = blockId,
        )

        assertTrue(dispatched)
        assertEquals<List<EditorAction>>(listOf(ToggleBlockSelection(blockId)), callbacks.actions)
    }

    @Test
    fun `drag-enabled policy does not use selection-only long press fallback`() {
        val callbacks = RecordingCallbacks()

        val dispatched = dispatchBlockLongPressSelectionIfDragDisabled(
            policy = EditorInteractionPolicy.Editable,
            callbacks = callbacks,
            blockId = blockId,
        )

        assertFalse(dispatched)
        assertEquals(emptyList<EditorAction>(), callbacks.actions)
    }

    @Test
    fun `selection-disabled policy does not use selection-only long press fallback`() {
        val callbacks = RecordingCallbacks()
        val policy = EditorInteractionPolicy.Editable.copy(
            canDragBlocks = false,
            canSelectBlocks = false,
        )

        val dispatched = dispatchBlockLongPressSelectionIfDragDisabled(
            policy = policy,
            callbacks = callbacks,
            blockId = blockId,
        )

        assertFalse(dispatched)
        assertEquals(emptyList<EditorAction>(), callbacks.actions)
    }

    @Test
    fun `read-only drag hover does not update drop target`() {
        val callbacks = RecordingCallbacks()

        val dispatched = dispatchDragTargetUpdateIfAllowed(
            policy = EditorInteractionPolicy.ReadOnly,
            callbacks = callbacks,
            targetIndex = 2,
            futureRootIndentationLevel = 1,
        )

        assertFalse(dispatched)
        assertEquals(emptyList<EditorAction>(), callbacks.actions)
    }

    @Test
    fun `editable drag hover updates drop target`() {
        val callbacks = RecordingCallbacks()

        val dispatched = dispatchDragTargetUpdateIfAllowed(
            policy = EditorInteractionPolicy.Editable,
            callbacks = callbacks,
            targetIndex = 2,
            futureRootIndentationLevel = 1,
        )

        assertTrue(dispatched)
        assertEquals<List<EditorAction>>(listOf(UpdateDragTarget(2, futureRootIndentationLevel = 1)), callbacks.actions)
    }

    @Test
    fun `read-only policy hides drag affordance overlays even with external drag state`() {
        val state = startDragState()

        assertFalse(shouldRenderDragAffordances(EditorInteractionPolicy.ReadOnly, state))
    }

    @Test
    fun `editable policy shows drag affordance overlays when drag is active`() {
        val state = startDragState()

        assertTrue(shouldRenderDragAffordances(EditorInteractionPolicy.Editable, state))
    }

    @Test
    fun `read-only empty-space tap leaves last text block cursor and focus unchanged`() {
        val lastBlockId = BlockId("last")
        val textStates = BlockTextStates()
        // Initial cursor at position 2 (not the would-be-set value of 4) so the
        // test catches a regression where the handler moves the caret to an
        // unexpected position rather than not moving it at all.
        textStates.getOrCreate(lastBlockId, "last", initialCursorPosition = 2)
        val originalSelection = textStates.getSelection(lastBlockId)
        var focusedBlockId: BlockId? = null

        focusLastTextBlockFromEmptySpace(
            policy = EditorInteractionPolicy.ReadOnly,
            blocks = listOf(textBlock("first", "first"), textBlock(lastBlockId.value, "last")),
            textStates = textStates,
            dispatchFocusBlock = { focusedBlockId = it },
        )

        assertEquals(originalSelection, textStates.getSelection(lastBlockId))
        assertNull(focusedBlockId)
    }

    @Test
    fun `partial policy missing one edit capability leaves empty-space tap inert`() {
        // Spec requires conjunction of canEditText AND canEditBlockStructure.
        // A hypothetical future partial policy with only one capability should
        // not allow empty-space tap to move the caret.
        val partial = EditorInteractionPolicy.Editable.copy(canEditBlockStructure = false)
        val lastBlockId = BlockId("last")
        val textStates = BlockTextStates()
        textStates.getOrCreate(lastBlockId, "last", initialCursorPosition = 1)
        val originalSelection = textStates.getSelection(lastBlockId)
        var focusedBlockId: BlockId? = null

        focusLastTextBlockFromEmptySpace(
            policy = partial,
            blocks = listOf(textBlock("first", "first"), textBlock(lastBlockId.value, "last")),
            textStates = textStates,
            dispatchFocusBlock = { focusedBlockId = it },
        )

        assertEquals(originalSelection, textStates.getSelection(lastBlockId))
        assertNull(focusedBlockId)
    }

    @Test
    fun `editable empty-space tap moves cursor to last text block and focuses it`() {
        val lastBlockId = BlockId("last")
        val textStates = BlockTextStates()
        textStates.getOrCreate(lastBlockId, "last", initialCursorPosition = 0)
        var focusedBlockId: BlockId? = null

        focusLastTextBlockFromEmptySpace(
            policy = EditorInteractionPolicy.Editable,
            blocks = listOf(textBlock("first", "first"), textBlock(lastBlockId.value, "last")),
            textStates = textStates,
            dispatchFocusBlock = { focusedBlockId = it },
        )

        assertEquals(TextRange(4), textStates.getSelection(lastBlockId))
        assertEquals(lastBlockId, focusedBlockId)
    }

    @Test
    fun `read-only todo checkbox action does not dispatch toggle`() {
        val callbacks = RecordingCallbacks()
        val action = createTodoCheckedChangeAction(
            blockId = blockId,
            callbacksProvider = { callbacks },
            policyProvider = { EditorInteractionPolicy.ReadOnly },
        )

        action(true)

        assertEquals(emptyList<EditorAction>(), callbacks.actions)
    }

    @Test
    fun `todo checkbox action captured while editable reads latest read-only policy`() {
        val callbacks = RecordingCallbacks()
        var policy = EditorInteractionPolicy.Editable
        val action = createTodoCheckedChangeAction(
            blockId = blockId,
            callbacksProvider = { callbacks },
            policyProvider = { policy },
        )

        policy = EditorInteractionPolicy.ReadOnly
        action(true)

        assertEquals(emptyList<EditorAction>(), callbacks.actions)
    }

    @Test
    fun `editable todo checkbox action dispatches toggle`() {
        val callbacks = RecordingCallbacks()
        val action = createTodoCheckedChangeAction(
            blockId = blockId,
            callbacksProvider = { callbacks },
            policyProvider = { EditorInteractionPolicy.Editable },
        )

        action(true)

        assertEquals<List<EditorAction>>(listOf(ToggleTodo(blockId)), callbacks.actions)
    }

    private fun startDragState(): EditorState {
        return StartDrag(blockId, touchOffsetY = 0f)
            .reduce(EditorState.withBlocks(listOf(textBlock(blockId.value, "block"))))
    }

    private fun textBlock(id: String, text: String): Block {
        return Block(
            id = BlockId(id),
            type = BlockType.Paragraph,
            content = BlockContent.Text(text),
        )
    }

    private class RecordingCallbacks : BlockCallbacks {
        val actions = mutableListOf<EditorAction>()
        val dragStarts = mutableListOf<DragStartCall>()

        override fun dispatch(action: EditorAction) {
            actions += action
        }

        override fun onFocus(blockId: BlockId) = Unit

        override fun onEnter(blockId: BlockId, cursorPosition: Int) = Unit

        override fun onBackspaceAtStart(blockId: BlockId) = Unit

        override fun onDeleteAtEnd(blockId: BlockId) = Unit

        override fun onClick(blockId: BlockId) = Unit

        override fun onLongClick(blockId: BlockId) = Unit

        override fun onDragStart(blockId: BlockId, touchOffsetY: Float) {
            dragStarts += DragStartCall(blockId, touchOffsetY)
        }

        override fun onSlashCommand(
            blockId: BlockId,
            queryRange: SlashQueryRange,
            initialQuery: String,
        ) = Unit

    }

    private data class DragStartCall(
        val blockId: BlockId,
        val touchOffsetY: Float,
    )

}
