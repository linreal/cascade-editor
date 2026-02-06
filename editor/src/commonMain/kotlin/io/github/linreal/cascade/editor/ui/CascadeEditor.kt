package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.state.BlockTextStates
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
 * Text state is managed via [BlockTextStates], which provides a single
 * source of truth for all text content. This enables direct manipulation
 * of text for operations like merge without LaunchedEffect syncing issues.
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

    // Create and remember the text states holder
    val blockTextStates = remember { BlockTextStates() }

    // Cleanup stale states when blocks change
    LaunchedEffect(state.blocks) {
        val existingIds = state.blocks.map { it.id }.toSet()
        blockTextStates.cleanup(existingIds)
    }

    // Create callbacks with state access and text states for proper merge handling
    val callbacks = remember(stateHolder, blockTextStates) {
        DefaultBlockCallbacks(
            dispatchFn = { action -> stateHolder.dispatch(action) },
            stateProvider = { stateHolder.state },
            blockTextStates = blockTextStates
        )
    }

    CompositionLocalProvider(LocalBlockTextStates provides blockTextStates) {
        val lazyListState = rememberLazyListState()

        // Current drag Y position — local state, NOT in EditorState.
        // Updated at ~60-120fps during drag; keeping it local avoids full-tree
        // recomposition on every pointer move. Task 10 will connect this to
        // the drag gesture modifier.
        var dragOffsetY by remember { mutableFloatStateOf(0f) }

        Box(modifier = modifier.fillMaxWidth()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    items = state.blocks,
                    key = { block -> block.id.value }
                ) { block ->
                    val isFocused = state.focusedBlockId == block.id
                    val isSelected = block.id in state.selectedBlockIds
                    val isDragging = state.dragState?.draggingBlockIds?.contains(block.id) == true

                    // Look up renderer for this block type
                    val renderer = registry.getRenderer(block.type.typeId)

                    renderer?.Render(
                        block = block,
                        isSelected = isSelected,
                        isFocused = isFocused,
                        modifier = Modifier
                            // Smooth placement animation for reorder, insert, delete.
                            // Fade disabled: during block merge, TextFieldState text moves
                            // to target before DeleteBlock runs — a fade-out would briefly
                            // show an empty block. Placement-only is clean.
                            .animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null
                            )
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .graphicsLayer {
                                // Apply 50% transparency to blocks being dragged
                                // Using graphicsLayer (not alpha()) for performance:
                                // only triggers re-draw, not re-layout
                                alpha = if (isDragging) 0.5f else 1f
                            },
                        callbacks = callbacks
                    )
                    // Blocks without registered renderers are silently skipped
                }
            }

            // Drop indicator overlay - rendered on top of LazyColumn.
            // Canvas does not consume touch events, so gestures pass through.
            state.dragState?.let { dragState ->
                DropIndicator(
                    targetIndex = dragState.targetIndex,
                    lazyListState = lazyListState
                )
            }

            // Drag preview overlay - semi-transparent block following the finger.
            // Rendered last in Box so it draws on top of both list and indicator.
            // Position updates happen in graphicsLayer (draw phase only).
            state.dragState?.let { dragState ->
                val primaryBlockId = dragState.draggingBlockIds.firstOrNull()
                val draggedBlock = primaryBlockId?.let { id ->
                    state.blocks.find { it.id == id }
                }
                if (draggedBlock != null) {
                    DragPreview(
                        block = draggedBlock,
                        dragOffsetY = { dragOffsetY },
                        initialTouchOffsetY = dragState.initialTouchOffsetY,
                        registry = registry,
                        callbacks = callbacks
                    )
                }
            }
        }
    }
}
