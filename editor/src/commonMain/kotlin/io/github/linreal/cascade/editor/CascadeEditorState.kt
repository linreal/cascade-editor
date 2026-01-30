package io.github.linreal.cascade.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * State holder for the Cascade editor.
 * Manages blocks, selection, and editing state.
 */
@Stable
public class CascadeEditorState {

    internal var blocks by mutableStateOf<List<Block>>(emptyList())
        private set

    internal var focusedBlockId by mutableStateOf<String?>(null)
        private set

    internal var selectedBlockIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * Returns the current list of blocks (read-only).
     */
    public fun getBlocks(): List<Block> = blocks

    /**
     * Returns the currently focused block ID, if any.
     */
    public fun getFocusedBlockId(): String? = focusedBlockId

    /**
     * Returns the set of selected block IDs.
     */
    public fun getSelectedBlockIds(): Set<String> = selectedBlockIds

    /**
     * Updates the blocks in the editor.
     */
    public fun updateBlocks(newBlocks: List<Block>) {
        blocks = newBlocks
    }

    /**
     * Sets focus to a specific block.
     */
    public fun focusBlock(blockId: String?) {
        focusedBlockId = blockId
    }

    /**
     * Updates the selection state.
     */
    public fun selectBlocks(blockIds: Set<String>) {
        selectedBlockIds = blockIds
    }

    /**
     * Clears the current selection.
     */
    public fun clearSelection() {
        selectedBlockIds = emptySet()
    }
}

/**
 * Creates and remembers a [CascadeEditorState].
 */
@Composable
public fun rememberCascadeEditorState(): CascadeEditorState {
    return remember { CascadeEditorState() }
}
