package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.state.EditorStateHolder

/**
 * Main editor composable for CascadeEditor.
 *
 * Renders a list of blocks using registered renderers with:
 * - Text editing with backspace/enter detection
 * - Block splitting on Enter
 * - Block merging on Backspace at start
 * - Focus management between blocks
 *
 * @param stateHolder The state holder managing editor state
 * @param registry Block registry with renderers. Defaults to [createEditorRegistry].
 * @param modifier Modifier for the editor container
 */
@Composable
public fun CascadeEditor(
    stateHolder: EditorStateHolder,
    registry: BlockRegistry = remember { createEditorRegistry() },
    modifier: Modifier = Modifier
) {
    val state = stateHolder.state

    // Create callbacks with state access for proper merge/delete handling
    val callbacks = remember(stateHolder) {
        DefaultBlockCallbacks(
            dispatchFn = { action -> stateHolder.dispatch(action) },
            stateProvider = { stateHolder.state }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth()
    ) {
        items(
            items = state.blocks,
            key = { block -> block.id.value }
        ) { block ->
            val isFocused = state.focusedBlockId == block.id
            val isSelected = block.id in state.selectedBlockIds

            // Look up renderer for this block type
            val renderer = registry.getRenderer(block.type.typeId)

            renderer?.Render(
                block = block,
                isSelected = isSelected,
                isFocused = isFocused,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                callbacks = callbacks
            )
            // Blocks without registered renderers are silently skipped
        }
    }
}
