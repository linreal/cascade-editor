package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import io.github.linreal.cascade.editor.action.CloseSlashCommand
import io.github.linreal.cascade.editor.action.UpdateDragTarget
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.richtext.DefaultFormattingActions
import io.github.linreal.cascade.editor.richtext.FormattingState
import io.github.linreal.cascade.editor.richtext.SpanActionDispatcher
import io.github.linreal.cascade.editor.richtext.rememberFormattingState
import io.github.linreal.cascade.editor.slash.BuiltInSlashCommandFactory
import io.github.linreal.cascade.editor.slash.SlashCommandExecutor
import io.github.linreal.cascade.editor.slash.SlashCommandItem
import io.github.linreal.cascade.editor.slash.SlashCommandRegistry
import io.github.linreal.cascade.editor.slash.createBuiltInSlashExecutor
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.theme.CascadeEditorBlockStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
import io.github.linreal.cascade.editor.theme.LocalCascadeBlockStrings
import io.github.linreal.cascade.editor.theme.LocalCascadeStrings
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme

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
 * @param textStates Runtime text field states. Pass your own instance to access
 *        live text from outside the composition (e.g., for save/load). Defaults to
 *        an internally remembered instance.
 * @param spanStates Runtime rich-text span states. Pass your own instance to access
 *        live spans from outside the composition (e.g., for save/load). Defaults to
 *        an internally remembered instance.
 * @param registry Block registry with renderers. Defaults to [createEditorRegistry].
 * @param slashRegistry Slash command registry. Register custom [SlashCommandItem]s here to
 *        make them appear alongside built-in block commands. Custom items override built-ins
 *        on ID collisions. Defaults to an empty registry.
 * @param theme Visual theme (colors + typography). Defaults to [CascadeEditorTheme.light].
 * @param strings Localized UI strings (toolbar labels, popup chrome). Defaults to
 *        [CascadeEditorStrings.default].
 * @param blockStrings Localized block names, descriptions, and keywords used in the slash
 *        command popup. Defaults to [CascadeEditorBlockStrings.default].
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
    textStates: BlockTextStates = remember { BlockTextStates() },
    spanStates: BlockSpanStates = remember { BlockSpanStates() },
    registry: BlockRegistry = remember { createEditorRegistry() },
    slashRegistry: SlashCommandRegistry = remember { SlashCommandRegistry() },
    theme: CascadeEditorTheme = CascadeEditorTheme.light(),
    strings: CascadeEditorStrings = CascadeEditorStrings.default(),
    blockStrings: CascadeEditorBlockStrings = CascadeEditorBlockStrings.default(),
    modifier: Modifier = Modifier,
    toolbar: ToolbarSlot = ToolbarSlot.Default(),
    onFormattingStateChanged: ((FormattingState) -> Unit)? = null,
) {
    val state = stateHolder.state

    // Slash wiring: built-in executor → built-in items → merged registry → executor.
    // The builtInExecutor lambda only needs stateHolder + blockRegistry, not the
    // SlashCommandRegistry, which breaks the circular dependency and lets us pass
    // the merged registry (built-in + custom) to the executor.
    val builtInExecutor = remember(stateHolder, registry) {
        createBuiltInSlashExecutor(stateHolder, registry)
    }

    val builtInSlashItems = remember(registry, builtInExecutor, blockStrings) {
        val builtInFactory = BuiltInSlashCommandFactory(builtInExecutor)
        builtInFactory.generate(registry.getAllDescriptors(), blockStrings)
    }

    // Use slashRegistry reference (stable) as the remember key — not getRootItems()
    // which creates a new List on every recomposition with fragile data-class equality.
    val effectiveSlashRegistry = remember(builtInSlashItems, slashRegistry) {
        createMergedSlashRegistry(
            builtInItems = builtInSlashItems,
            customItems = slashRegistry.getRootItems(),
        )
    }

    val slashExecutionScope = rememberCoroutineScope()
    val slashExecutor = remember(
        effectiveSlashRegistry,
        stateHolder,
        textStates,
        spanStates,
        slashExecutionScope,
        builtInExecutor,
    ) {
        SlashCommandExecutor(
            registry = effectiveSlashRegistry,
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
            executionScope = slashExecutionScope,
            builtInExecutor = builtInExecutor,
        )
    }

    // Cleanup stale states when blocks change
    LaunchedEffect(state.blocks) {
        val allBlockIds = state.blocks.map { it.id }.toSet()
        val textBlockIds = collectTextBlockIds(state.blocks)
        textStates.cleanup(allBlockIds)
        spanStates.cleanup(textBlockIds)
    }

    // Close slash session when drag, selection, or anchor deletion invalidates it.
    LaunchedEffect(stateHolder) {
        snapshotFlow { shouldInvalidateSlashSession(stateHolder.state) }
            .collect { if (it) stateHolder.dispatch(CloseSlashCommand) }
    }

    // Create span action dispatcher for coordinated runtime + snapshot style updates
    val spanActionDispatcher = remember(stateHolder, textStates, spanStates) {
        SpanActionDispatcher(
            dispatchFn = { action -> stateHolder.dispatch(action) },
            textStates = textStates,
            spanStates = spanStates,
        )
    }

    // Create callbacks with state access and text states for proper merge handling
    val callbacks = remember(stateHolder, textStates, spanStates) {
        DefaultBlockCallbacks(
            dispatchFn = { action -> stateHolder.dispatch(action) },
            stateProvider = { stateHolder.state },
            textStates = textStates,
            spanStates = spanStates,
        )
    }

    // Inject theme highlight color into the default toolbar config so that the
    // toolbar button's SpanStyle.Highlight matches CascadeEditorColors.highlight.
    // Only applies when the caller uses the exact RichTextToolbarConfig.Default sentinel;
    // custom configs are left untouched.
    val resolvedToolbar = remember(toolbar, theme.colors.highlight) {
        if (toolbar is ToolbarSlot.Default && toolbar.config === RichTextToolbarConfig.Default) {
            val highlightArgb = theme.colors.highlight.toArgb().toUInt().toLong()
            toolbar.copy(
                config = toolbar.config.copy(
                    buttons = toolbar.config.buttons.map { spec ->
                        if (spec.style is SpanStyle.Highlight) spec.copy(style = SpanStyle.Highlight(highlightArgb))
                        else spec
                    }
                )
            )
        } else toolbar
    }

    // Formatting state observer + actions
    // Only created when a toolbar is visible or an external callback is set.

    val needsFormattingState = resolvedToolbar !is ToolbarSlot.None || onFormattingStateChanged != null

    val trackedStyles = remember(resolvedToolbar) {
        when (resolvedToolbar) {
            is ToolbarSlot.Default -> resolvedToolbar.config.buttons.map { it.style }
            is ToolbarSlot.Custom -> resolvedToolbar.trackedStyles
            ToolbarSlot.None -> RichTextToolbarConfig.Default.buttons.map { it.style }
        }
    }

    val formattingState = if (needsFormattingState) {
        rememberFormattingState(
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
            trackedStyles = trackedStyles,
        )
    } else {
        null
    }

    // Always created — used by keyboard shortcuts (Cmd+B/I/U) even without a toolbar.
    val formattingActions = remember(stateHolder, textStates, spanActionDispatcher) {
        DefaultFormattingActions(
            stateHolder = stateHolder,
            textStates = textStates,
            spanActionDispatcher = spanActionDispatcher,
        )
    }

    // Fire external callback on formatting state changes (structural equality dedup)
    if (onFormattingStateChanged != null && formattingState != null) {
        val currentCallback by rememberUpdatedState(onFormattingStateChanged)
        LaunchedEffect(formattingState) {
            snapshotFlow { formattingState.value }
                .collect { currentCallback(it) }
        }
    }

    // Slash popup support: caret rect holder and search results for keyboard nav.
    val slashCaretRectHolder = remember { SlashCaretRectHolder() }
    val slashState = state.slashCommandState
    val slashPopupItems = remember(
        slashState?.query,
        slashState?.navigationPath,
        effectiveSlashRegistry,
    ) {
        if (slashState != null) {
            effectiveSlashRegistry.search(slashState.query, slashState.navigationPath)
        } else {
            emptyList()
        }
    }

    // Empty search results invalidate slash mode per feature spec.
    LaunchedEffect(slashState?.anchorBlockId, slashState?.query, slashState?.navigationPath, slashPopupItems) {
        if (slashState != null && slashPopupItems.isEmpty()) {
            stateHolder.dispatch(CloseSlashCommand)
        }
    }

    val textSelectionColors = remember(theme.colors.cursor, theme.colors.textSelectionBackground) {
        TextSelectionColors(
            handleColor = theme.colors.cursor,
            backgroundColor = theme.colors.textSelectionBackground,
        )
    }

    CompositionLocalProvider(
        LocalCascadeTheme provides theme,
        LocalCascadeStrings provides strings,
        LocalCascadeBlockStrings provides blockStrings,
        LocalTextSelectionColors provides textSelectionColors,
        LocalBlockTextStates provides textStates,
        LocalBlockSpanStates provides spanStates,
        LocalSpanActionDispatcher provides spanActionDispatcher,
        LocalFormattingActions provides formattingActions,
        LocalSlashCommandExecutor provides slashExecutor,
        LocalSlashSessionAnchorBlockId provides slashState?.anchorBlockId,
        LocalSlashHighlightedCommandId provides slashState?.highlightedCommandId,
        LocalSlashCaretRect provides slashCaretRectHolder,
        LocalSlashPopupItems provides slashPopupItems,
    ) {
        val lazyListState = rememberLazyListState()
        val focusManager = LocalFocusManager.current

        // Scroll-to-focus: ensure the focused block is visible before its
        // TextBlockField requests Compose focus. Also clears Compose focus
        // when no block has editor-level focus. This prevents keyboard blink
        // when pressing Enter near the bottom of the viewport; the old block
        // keeps keyboard open while we scroll the new block into view.
        LaunchedEffect(stateHolder, lazyListState) {
            snapshotFlow { stateHolder.state.focusedBlockId }
                .collectLatest { focusedId ->
                    if (focusedId == null) {
                        focusManager.clearFocus()
                        return@collectLatest
                    }
                    val currentState = stateHolder.state
                    val index = currentState.blocks.indexOfFirst { it.id == focusedId }
                    if (index < 0) return@collectLatest

                    val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
                    val isVisible = visibleItems.any { it.index == index }
                    if (!isVisible) {
                        lazyListState.animateScrollToItem(index)
                    }
                }
        }

        // Enable placement animation only during and briefly after drag,
        // so reorder looks smooth but keyboard resize doesn't cause overlap.
        var animatePlacement by remember { mutableStateOf(false) }
        LaunchedEffect(state.dragState) {
            if (state.dragState != null) {
                animatePlacement = true
            } else if (animatePlacement) {
                // Keep animation active after drag ends so the reorder
                // animation from CompleteDrag has time to play.
                delay(500)
                animatePlacement = false
            }
        }

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
                    .clipToBounds()
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
                    userScrollEnabled = state.dragState == null && state.slashCommandState == null,
                    // Extra space so the last block isn't flush against the viewport edge.
                    contentPadding = PaddingValues(bottom = 40.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = state.blocks,
                        key = { block -> block.id.value }
                    ) { block ->
                        val isFocused = state.focusedBlockId == block.id
                        val isSelected = block.id in state.selectedBlockIds
                        val isDragging = state.dragState?.draggingBlockIds?.contains(block.id) == true

                        // Look up renderer for this block type (includes unknown-block fallback)
                        val renderer = registry.getRenderer(block.type)

                        renderer?.Render(
                            block = block,
                            isSelected = isSelected,
                            isFocused = isFocused,
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = null,
                                    placementSpec = if (animatePlacement) spring() else null,
                                    fadeOutSpec = null,
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

                // Slash command popup overlay - shown when a slash session is active.
                if (slashState != null && slashPopupItems.isNotEmpty()) {
                    SlashCommandPopup(
                        slashState = slashState,
                        stateHolder = stateHolder,
                        slashExecutor = slashExecutor,
                    )
                }
            }

         // Toolbar
            when (resolvedToolbar) {
                is ToolbarSlot.Default -> if (formattingState != null) {
                    RichTextToolbar(
                        formattingState = formattingState,
                        actions = formattingActions,
                        config = resolvedToolbar.config,
                        onSlashInsert = {
                            val blockId = formattingState.value.focusedBlockId ?: return@RichTextToolbar
                            val tfs = textStates.get(blockId) ?: return@RichTextToolbar
                            val sel = tfs.visibleSelection()
                            tfs.edit {
                                replace(sel.min + 1, sel.max + 1, "/")
                                selection = TextRange(sel.min + 2)
                            }
                        },
                    )
                }
                is ToolbarSlot.Custom -> if (formattingState != null) {
                    resolvedToolbar.content(formattingState, formattingActions)
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

/**
 * Returns `true` when the current [EditorState] indicates the active slash session
 * should be closed. Reasons: drag active, block selection active, or the anchor block
 * no longer exists in the block list.
 *
 * Returns `false` when there is no active session (nothing to invalidate).
 */
internal fun shouldInvalidateSlashSession(state: EditorState): Boolean {
    val slash = state.slashCommandState ?: return false
    if (state.dragState != null) return true
    if (state.selectedBlockIds.isNotEmpty()) return true
    if (state.blocks.none { it.id == slash.anchorBlockId }) return true
    return false
}

internal fun createMergedSlashRegistry(
    builtInItems: List<SlashCommandItem>,
    customItems: List<SlashCommandItem>,
): SlashCommandRegistry {
    return SlashCommandRegistry().apply {
        // Built-ins register first, custom items override by ID when duplicated.
        builtInItems.forEach(::register)
        customItems.forEach(::register)
    }
}
