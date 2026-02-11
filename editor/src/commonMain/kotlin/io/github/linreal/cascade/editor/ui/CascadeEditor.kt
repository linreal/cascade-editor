package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.action.CancelDrag
import io.github.linreal.cascade.editor.action.CompleteDrag
import io.github.linreal.cascade.editor.action.UpdateDragTarget
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.ui.utils.calculateDropTargetIndex

/**
 * Main editor composable for CascadeEditor.
 *
 * Renders a list of blocks using registered renderers with:
 * - Text editing with backspace/enter detection
 * - Block splitting on Enter
 * - Block merging on Backspace at start
 * - Focus management between blocks
 * - Long-press drag-and-drop with auto-scroll
 *
 * Text state is managed via [BlockTextStates], which provides a single
 * source of truth for all text content. This enables direct manipulation
 * of text for operations like merge without LaunchedEffect syncing issues.
 *
 * ## Drag Gesture Architecture
 * The drag gesture is detected on the [Box] wrapper, **not** on individual
 * LazyColumn items. This is critical because programmatic scrolling during
 * drag (auto-scroll) would otherwise cause item recycling or layout changes
 * that cancel the per-item pointer input coroutine. With the gesture on the
 * Box, it is immune to LazyColumn's internal scroll/layout lifecycle.
 *
 * During drag, [LazyColumn]'s `userScrollEnabled` is set to false so its
 * internal [Scrollable] modifier does not compete for pointer events.
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
        // recomposition on every pointer move.
        var dragOffsetY by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = modifier
                .fillMaxWidth()
                // Long-press drag gesture detected at the Box level.
                // This keeps the gesture coroutine alive regardless of
                // LazyColumn item recycling or layout changes from auto-scroll.
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            // Find which block is under the touch point
                            val item = lazyListState.layoutInfo.visibleItemsInfo
                                .find { info ->
                                    offset.y >= info.offset &&
                                        offset.y < info.offset + info.size
                                }
                            if (item != null) {
                                val blockId = BlockId(item.key as String)
                                val touchWithinBlock = offset.y - item.offset
                                dragOffsetY = offset.y
                                callbacks.onDragStart(blockId, touchWithinBlock)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Guard: only process if a drag was actually started
                            // (long press may have landed on empty space)
                            if (stateHolder.state.dragState != null) {
                                dragOffsetY += dragAmount.y
                                val newTarget = calculateDropTargetIndex(
                                    lazyListState.layoutInfo,
                                    dragOffsetY,
                                    stateHolder.state.blocks.size
                                )
                                callbacks.dispatch(UpdateDragTarget(newTarget))
                            }
                        },
                        onDragEnd = {
                            if (stateHolder.state.dragState != null) {
                                callbacks.dispatch(CompleteDrag)
                            }
                        },
                        onDragCancel = {
                            if (stateHolder.state.dragState != null) {
                                callbacks.dispatch(CancelDrag)
                            }
                        }
                    )
                }
        ) {
            LazyColumn(
                state = lazyListState,
                // Disable LazyColumn's built-in scroll gesture during drag so
                // its Scrollable modifier does not consume pointer events that
                // belong to our drag handler. Auto-scroll uses dispatchRawDelta
                // which bypasses gesture handling entirely.
                userScrollEnabled = state.dragState == null,
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
                            .animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null
                            )
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .graphicsLayer {
                                // Apply 50% transparency to blocks being dragged
                                alpha = if (isDragging) 0.5f else 1f
                            },
                        callbacks = callbacks
                    )
                }
            }

            // Auto-scroll when drag gesture enters hot zones near viewport edges.
            // Runs a coroutine loop during drag; cancels automatically when drag ends.
            AutoScrollDuringDrag(
                lazyListState = lazyListState,
                dragOffsetY = { dragOffsetY },
                isDragging = state.dragState != null,
                blockCount = state.blocks.size,
                onDropTargetChanged = { newTarget ->
                    callbacks.dispatch(UpdateDragTarget(newTarget))
                }
            )

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
