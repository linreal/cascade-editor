package io.github.linreal.cascade.editor.registry

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
import io.github.linreal.cascade.editor.action.MoveDragPayload
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
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.state.SlashQueryRange
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy

/**
 * Central policy gate for block-renderer callbacks owned by [io.github.linreal.cascade.editor.ui.CascadeEditor].
 *
 * The wrapped [delegate] remains the only object that knows how to mutate
 * editor state. This facade decides whether a built-in interaction is allowed
 * to reach it under the current [policy].
 */
internal class PolicyAwareBlockCallbacks(
    private val delegate: BlockCallbacks,
    private val policy: EditorInteractionPolicy,
) : BlockCallbacks {

    override fun dispatch(action: EditorAction) {
        if (policy.canDispatch(action)) {
            delegate.dispatch(action)
        }
    }

    override fun onFocus(blockId: BlockId) {
        delegate.onFocus(blockId)
    }

    override fun onEnter(blockId: BlockId, cursorPosition: Int) {
        if (policy.canEditBlockStructure) {
            delegate.onEnter(blockId, cursorPosition)
        }
    }

    override fun onBackspaceAtStart(blockId: BlockId) {
        if (policy.canEditBlockStructure) {
            delegate.onBackspaceAtStart(blockId)
        }
    }

    override fun onDeleteAtEnd(blockId: BlockId) {
        if (policy.canEditBlockStructure) {
            delegate.onDeleteAtEnd(blockId)
        }
    }

    override fun onClick(blockId: BlockId) {
        delegate.onClick(blockId)
    }

    override fun onLongClick(blockId: BlockId) {
        delegate.onLongClick(blockId)
    }

    override fun onDragStart(blockId: BlockId, touchOffsetY: Float) {
        if (policy.canDragBlocks) {
            delegate.onDragStart(blockId, touchOffsetY)
        }
    }

    override fun onSlashCommand(
        blockId: BlockId,
        queryRange: SlashQueryRange,
        initialQuery: String,
    ) {
        if (policy.canUseSlashCommands) {
            delegate.onSlashCommand(blockId, queryRange, initialQuery)
        }
    }
}

/**
 * Returns whether a generic [EditorAction] may cross the editor UI policy boundary.
 *
 * The fully editable singleton forwards all actions to preserve existing
 * behavior. Partial and read-only policies classify every [EditorAction] subtype
 * with an exhaustive `when`, so the compiler fails the build until any new
 * subtype is explicitly attached to a capability.
 */
private fun EditorInteractionPolicy.canDispatch(action: EditorAction): Boolean {
    if (this === EditorInteractionPolicy.Editable) return true

    return when (action) {
        // Text-block focus remains available for caret placement and selection.
        is FocusBlock -> true
        // Keyboard dismissal and focus teardown must stay available.
        ClearFocus -> true
        // Stale slash sessions can exist when read-only flips on at runtime.
        CloseSlashCommand -> true
        // Active drags can exist when read-only flips on at runtime.
        CancelDrag -> true
        // Active block selection can exist when read-only flips on at runtime.
        ClearSelection -> true

        is InsertBlock,
        is InsertBlockAfter,
        is InsertBlockBefore,
        is DeleteBlock,
        is DeleteBlocks,
        DeleteSelectedOrFocused,
        is ConvertBlockType,
        is MoveBlocks,
        is MergeBlocks,
        is ReplaceBlock,
        is SplitBlock,
        IndentForward,
        IndentBackward -> canEditBlockStructure

        is UpdateBlockContent,
        is UpdateBlockText -> canEditText

        is ToggleTodo -> canEditBlockControls

        is ApplySpanStyle,
        is RemoveSpanStyle -> canFormatText

        is SelectBlock,
        is SelectBlockRange,
        is AddBlockRangeToSelection,
        is ToggleBlockSelection,
        SelectAll -> canSelectBlocks

        FocusNextBlock,
        FocusPreviousBlock -> canEditText

        is StartDrag,
        is UpdateDragTarget,
        CompleteDrag,
        is MoveDragPayload -> canDragBlocks && canEditBlockStructure

        is OpenSlashCommand,
        is UpdateSlashCommandSession,
        is HighlightSlashCommand,
        is NavigateSlashSubmenu,
        NavigateSlashBack -> canUseSlashCommands
    }
}
