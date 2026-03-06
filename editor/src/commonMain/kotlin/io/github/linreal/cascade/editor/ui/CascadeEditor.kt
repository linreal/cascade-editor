package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.action.UpdateDragTarget
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.richtext.DefaultFormattingActions
import io.github.linreal.cascade.editor.richtext.FormattingState
import io.github.linreal.cascade.editor.richtext.SpanActionDispatcher
import io.github.linreal.cascade.editor.richtext.rememberFormattingState
import io.github.linreal.cascade.editor.state.BlockSpanStates
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
 * @param toolbar Toolbar slot controlling what formatting toolbar is shown.
 *        [ToolbarSlot.Default] renders the built-in config-driven toolbar.
 *        [ToolbarSlot.None] hides the toolbar.
 *        [ToolbarSlot.Custom] renders a consumer-provided composable with
 *        reactive [FormattingState] and [FormattingActions][io.github.linreal.cascade.editor.richtext.FormattingActions].
 * @param onFormattingStateChanged Optional callback fired when the formatting
 *        state changes. Uses structural equality to avoid redundant calls.
 */
@Composable
public fun CascadeEditor(
    stateHolder: EditorStateHolder,
    registry: BlockRegistry = remember { createEditorRegistry() },
    modifier: Modifier = Modifier,
    toolbar: ToolbarSlot = ToolbarSlot.Default(),
    onFormattingStateChanged: ((FormattingState) -> Unit)? = null,
) {
    val state = stateHolder.state

    // Create and remember the text states holder
    val blockTextStates = remember { BlockTextStates() }
    val blockSpanStates = remember { BlockSpanStates() }

    // Cleanup stale states when blocks change
    LaunchedEffect(state.blocks) {
        val allBlockIds = state.blocks.map { it.id }.toSet()
        val textBlockIds = collectTextBlockIds(state.blocks)
        blockTextStates.cleanup(allBlockIds)
        blockSpanStates.cleanup(textBlockIds)
    }

    // Create span action dispatcher for coordinated runtime + snapshot style updates
    val spanActionDispatcher = remember(stateHolder, blockTextStates, blockSpanStates) {
        SpanActionDispatcher(
            dispatchFn = { action -> stateHolder.dispatch(action) },
            blockTextStates = blockTextStates,
            blockSpanStates = blockSpanStates,
        )
    }

    // Create callbacks with state access and text states for proper merge handling
    val callbacks = remember(stateHolder, blockTextStates, blockSpanStates) {
        DefaultBlockCallbacks(
            dispatchFn = { action -> stateHolder.dispatch(action) },
            stateProvider = { stateHolder.state },
            blockTextStates = blockTextStates,
            blockSpanStates = blockSpanStates,
        )
    }

 // Formatting state observer + actions
    // Only created when a toolbar is visible or an external callback is set.

    val needsFormattingState = toolbar !is ToolbarSlot.None || onFormattingStateChanged != null

    val trackedStyles = remember(toolbar) {
        when (toolbar) {
            is ToolbarSlot.Default -> toolbar.config.buttons.map { it.style }
            is ToolbarSlot.Custom -> toolbar.trackedStyles
            ToolbarSlot.None -> RichTextToolbarConfig.Default.buttons.map { it.style }
        }
    }

    val formattingState = if (needsFormattingState) {
        rememberFormattingState(
            stateHolder = stateHolder,
            blockTextStates = blockTextStates,
            blockSpanStates = blockSpanStates,
            trackedStyles = trackedStyles,
        )
    } else {
        null
    }

    val formattingActions = if (needsFormattingState) {
        remember(stateHolder, blockTextStates, spanActionDispatcher) {
            DefaultFormattingActions(
                stateHolder = stateHolder,
                blockTextStates = blockTextStates,
                spanActionDispatcher = spanActionDispatcher,
            )
        }
    } else {
        null
    }

    // Fire external callback on formatting state changes (structural equality dedup)
    if (onFormattingStateChanged != null && formattingState != null) {
        val currentCallback by rememberUpdatedState(onFormattingStateChanged)
        LaunchedEffect(formattingState) {
            snapshotFlow { formattingState.value }
                .collect { currentCallback(it) }
        }
    }

    CompositionLocalProvider(
        LocalBlockTextStates provides blockTextStates,
        LocalBlockSpanStates provides blockSpanStates,
        LocalSpanActionDispatcher provides spanActionDispatcher,
    ) {
        val lazyListState = rememberLazyListState()

        // Current drag Y position — local state, NOT in EditorState.
        // Updated at ~60-120fps during drag; keeping it local avoids full-tree
        // recomposition on every pointer move.
        val dragOffsetY = remember { mutableFloatStateOf(0f) }

        // Outer Column: editor content (weight=1f) + toolbar at bottom.
        // Toolbar is OUTSIDE the drag gesture Box to prevent drag events
        // hitting toolbar children.
        Column(modifier = modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .blockDragGesture(
                        lazyListState = lazyListState,
                        dragOffsetY = dragOffsetY,
                        stateProvider = { stateHolder.state },
                        callbacks = callbacks,
                    )
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
                    dragOffsetY = { dragOffsetY.floatValue },
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
                            dragOffsetY = { dragOffsetY.floatValue },
                            initialTouchOffsetY = dragState.initialTouchOffsetY,
                            registry = registry,
                            callbacks = callbacks
                        )
                    }
                }
            }

         // Toolbar
            // formattingState/formattingActions are non-null when toolbar is
            // Default or Custom (guarded by needsFormattingState).
            when (toolbar) {
                is ToolbarSlot.Default -> if (formattingState != null && formattingActions != null) {
                    RichTextToolbar(
                        formattingState = formattingState,
                        actions = formattingActions,
                        config = toolbar.config,
                    )
                }
                is ToolbarSlot.Custom -> if (formattingState != null && formattingActions != null) {
                    toolbar.content(formattingState, formattingActions)
                }
                ToolbarSlot.None -> { /* no toolbar */ }
            }
        }
    }
}

internal fun collectTextBlockIds(blocks: List<Block>): Set<BlockId> {
    return blocks
        .asSequence()
        .filter { block -> block.type.supportsText && block.content is BlockContent.Text }
        .map { block -> block.id }
        .toSet()
}
