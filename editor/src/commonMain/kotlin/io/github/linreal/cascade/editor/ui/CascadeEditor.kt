package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import io.github.linreal.cascade.editor.action.CancelDrag
import io.github.linreal.cascade.editor.action.ClearSelection
import io.github.linreal.cascade.editor.action.ClearFocus
import io.github.linreal.cascade.editor.action.CloseSlashCommand
import io.github.linreal.cascade.editor.action.FocusBlock
import io.github.linreal.cascade.editor.isIos
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.registry.PolicyAwareBlockCallbacks
import io.github.linreal.cascade.editor.richtext.FormattingState
import io.github.linreal.cascade.editor.slash.BuiltInSlashCommandFactory
import io.github.linreal.cascade.editor.slash.SlashCommandExecutor
import io.github.linreal.cascade.editor.slash.SlashCommandItem
import io.github.linreal.cascade.editor.slash.SlashCommandRegistry
import io.github.linreal.cascade.editor.slash.createBuiltInSlashExecutor
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.ui.utils.DragHoverOutlineIndex
import io.github.linreal.cascade.editor.theme.CascadeEditorBlockStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
import io.github.linreal.cascade.editor.theme.LocalCascadeBlockStrings
import io.github.linreal.cascade.editor.theme.LocalCascadeStrings
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme
import io.github.linreal.cascade.editor.ui.renderers.LocalOrderedListPrefixStyles
import io.github.linreal.cascade.editor.ui.renderers.resolveOrderedListPrefixStyles

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
 *        an internally remembered instance. Custom instances should stay stable
 *        for the lifetime of the bound [stateHolder]; swapping them while reusing
 *        the same holder can desynchronize history replay from the live editor session.
 * @param spanStates Runtime rich-text span states. Pass your own instance to access
 *        live spans from outside the composition (e.g., for save/load). Defaults to
 *        an internally remembered instance. The same lifetime rule as [textStates]
 *        applies when history replay is enabled.
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
 *        Custom toolbars can read [LocalIndentationState] and [LocalIndentationActions]
 *        for indentation controls, and [LocalLinkState] / [LocalLinkActions]
 *        for link controls, without separate slot parameters.
 * @param linkPopup Controls editor-owned link popup rendering. [LinkPopupSlot.Default]
 *        selects the built-in popup, [LinkPopupSlot.Custom] uses editor-managed
 *        state/actions with consumer UI, and [LinkPopupSlot.None] disables
 *        editor-owned popup UI.
 * @param onOpenLink Optional app-controlled link opener. When null, links
 *        opened from unfocused text blocks use the platform [LocalUriHandler]
 *        and swallow platform opening failures.
 * @param onFormattingStateChanged Optional callback fired when the formatting
 *        state changes. Uses structural equality to avoid redundant calls.
 * @param config Cross-cutting editor behavior configuration. Defaults to
 *        [CascadeEditorConfig.Default] to preserve existing call sites.
 */

// Shorter than platform default (~400ms) to feel responsive for block selection
private const val BLOCK_LONG_PRESS_MS = 190L

private val SelectionOverlayShape = RoundedCornerShape(8.dp)

@Composable
public fun CascadeEditor(
    stateHolder: EditorStateHolder,
    textStates: BlockTextStates = remember { BlockTextStates() },
    spanStates: BlockSpanStates = remember { BlockSpanStates() },
    registry: BlockRegistry = remember { createEditorRegistry() },
    slashRegistry: SlashCommandRegistry = remember { SlashCommandRegistry() },
    slashCommand: SlashCommandSlot = SlashCommandSlot.Default,
    theme: CascadeEditorTheme = CascadeEditorTheme.light(),
    strings: CascadeEditorStrings = CascadeEditorStrings.default(),
    blockStrings: CascadeEditorBlockStrings = CascadeEditorBlockStrings.default(),
    modifier: Modifier = Modifier,
    toolbar: ToolbarSlot = ToolbarSlot.Default(),
    linkPopup: LinkPopupSlot = LinkPopupSlot.Default,
    onOpenLink: ((String) -> Unit)? = null,
    onFormattingStateChanged: ((FormattingState) -> Unit)? = null,
    config: CascadeEditorConfig = CascadeEditorConfig.Default,
) {
    val state = stateHolder.state
    val interactionPolicy = remember(config) {
        config.toInteractionPolicy()
    }
    val orderedListPrefixStyles = remember(state.blocks) {
        resolveOrderedListPrefixStyles(state.blocks)
    }

    // History replay uses the live runtime holders owned by this composition.
    // Caller-provided instances should remain stable for the lifetime of stateHolder.
    DisposableEffect(stateHolder, textStates, spanStates) {
        stateHolder.bindHistoryRuntime(textStates, spanStates)
        onDispose {
            stateHolder.unbindHistoryRuntime(textStates, spanStates)
        }
    }

    // Single high-level gate: when disabled, the entire slash subsystem is skipped.
    val slashEnabled = isSlashCommandSubsystemEnabled(slashCommand, interactionPolicy)

    // Slash wiring: built-in executor → built-in items → merged registry → executor.
    // The builtInExecutor lambda only needs stateHolder + blockRegistry, not the
    // SlashCommandRegistry, which breaks the circular dependency and lets us pass
    // the merged registry (built-in + custom) to the executor.
    val builtInExecutor = remember(stateHolder, registry, slashEnabled) {
        if (slashEnabled) createBuiltInSlashExecutor(stateHolder, registry) else null
    }

    val builtInSlashItems = remember(registry, builtInExecutor, blockStrings) {
        if (builtInExecutor == null) {
            emptyList()
        } else {
            val builtInFactory = BuiltInSlashCommandFactory(builtInExecutor)
            builtInFactory.generate(registry.getAllDescriptors(), blockStrings)
        }
    }

    // Use slashRegistry reference (stable) as the remember key — not getRootItems()
    // which creates a new List on every recomposition with fragile data-class equality.
    val effectiveSlashRegistry = remember(builtInSlashItems, slashRegistry, slashEnabled) {
        if (slashEnabled) {
            createMergedSlashRegistry(
                builtInItems = builtInSlashItems,
                customItems = slashRegistry.getRootItems(),
            )
        } else {
            null
        }
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
        if (effectiveSlashRegistry != null && builtInExecutor != null) {
            SlashCommandExecutor(
                registry = effectiveSlashRegistry,
                stateHolder = stateHolder,
                textStates = textStates,
                spanStates = spanStates,
                executionScope = slashExecutionScope,
                builtInExecutor = builtInExecutor,
            )
        } else {
            null
        }
    }

    // Cleanup stale states when blocks change
    LaunchedEffect(state.blocks) {
        val allBlockIds = state.blocks.map { it.id }.toSet()
        val textBlockIds = collectTextBlockIds(state.blocks)
        textStates.cleanup(allBlockIds)
        spanStates.cleanup(textBlockIds)
    }

    // Close slash session when drag, selection, or anchor deletion invalidates it.
    if (slashEnabled) {
        LaunchedEffect(stateHolder) {
            snapshotFlow { shouldInvalidateSlashSession(stateHolder.state) }
                .collect { if (it) stateHolder.dispatch(CloseSlashCommand) }
        }
    }

    // Create callbacks with state access and text states for proper merge handling.
    val defaultCallbacks = remember(stateHolder, textStates, spanStates) {
        DefaultBlockCallbacks(
            dispatchFn = { action -> stateHolder.dispatch(action) },
            stateProvider = { stateHolder.state },
            textStates = textStates,
            spanStates = spanStates,
            stateHolder = stateHolder,
        )
    }
    val callbacks = remember(defaultCallbacks, interactionPolicy) {
        PolicyAwareBlockCallbacks(defaultCallbacks, interactionPolicy)
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
                        if (spec.style is SpanStyle.Highlight) spec.copy(
                            style = SpanStyle.Highlight(
                                highlightArgb
                            )
                        )
                        else spec
                    }
                )
            )
        } else toolbar
    }

    // Formatting state observer + actions are assembled by the shared toolbar
    // runtime factory. The formatting state observer is created only when a
    // toolbar is visible or an external callback is set, to avoid the
    // derivedStateOf chain in headless configurations.
    val needsFormattingState =
        resolvedToolbar !is ToolbarSlot.None || onFormattingStateChanged != null

    val trackedStyles = remember(resolvedToolbar) {
        when (resolvedToolbar) {
            is ToolbarSlot.Default -> resolvedToolbar.config.buttons.map { it.style }
            is ToolbarSlot.Custom -> resolvedToolbar.trackedStyles
            ToolbarSlot.None -> RichTextToolbarConfig.Default.buttons.map { it.style }
        }
    }

    val toolbarRuntime = rememberEditorToolbarRuntime(
        stateHolder = stateHolder,
        textStates = textStates,
        spanStates = spanStates,
        trackedStyles = trackedStyles,
        interactionPolicy = interactionPolicy,
        needsFormattingState = needsFormattingState,
    )
    val formattingState = toolbarRuntime.formattingState
    val formattingActions = toolbarRuntime.formattingActions
    val spanActionDispatcher = toolbarRuntime.spanActionDispatcher
    val indentationState = toolbarRuntime.indentationState
    val indentationActions = toolbarRuntime.indentationActions
    val linkState = toolbarRuntime.linkState
    val linkActions = toolbarRuntime.linkActions
    val uriHandler = LocalUriHandler.current
    val linkOpener = remember(onOpenLink, uriHandler) {
        createLinkOpener(onOpenLink, uriHandler)
    }
    // Owns popup session lifecycle: open/dismiss, anchor rect, focus restoration,
    // and stale-target invalidation. See [LinkPopupController].
    val linkPopupController = rememberLinkPopupController(
        linkPopup = linkPopup,
        linkActions = linkActions,
        stateHolder = stateHolder,
        textStates = textStates,
        spanStates = spanStates,
        linkState = linkState,
        policy = interactionPolicy,
    )

    // Cleanup re-runs only when the effective interaction policy changes. Holder/controller are
    // stable for the composition's lifetime and are routed through
    // rememberUpdatedState so the latest references are read inside the effect
    // without adding them to the keys.
    val currentLinkPopupController by rememberUpdatedState(linkPopupController)
    val currentStateHolderForCleanup by rememberUpdatedState(stateHolder)
    LaunchedEffect(interactionPolicy) {
        applyReadOnlyTransitionCleanup(
            policy = interactionPolicy,
            stateHolder = currentStateHolderForCleanup,
            linkPopupController = currentLinkPopupController,
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
        if (slashState != null && effectiveSlashRegistry != null) {
            effectiveSlashRegistry.search(slashState.query, slashState.navigationPath)
        } else {
            emptyList()
        }
    }

    // Empty search results invalidate slash mode.
    if (slashEnabled) {
        LaunchedEffect(
            slashState?.anchorBlockId,
            slashState?.query,
            slashState?.navigationPath,
            slashPopupItems
        ) {
            if (slashState != null && slashPopupItems.isEmpty()) {
                stateHolder.dispatch(CloseSlashCommand)
            }
        }
    }

    val textSelectionColors = remember(theme.colors.cursor, theme.colors.textSelectionBackground) {
        TextSelectionColors(
            handleColor = theme.colors.cursor,
            backgroundColor = theme.colors.textSelectionBackground,
        )
    }

    CompositionLocalProvider(
        LocalCascadeEditorConfig provides config,
        LocalEditorInteractionPolicy provides interactionPolicy,
        LocalCascadeTheme provides theme,
        LocalCascadeStrings provides strings,
        LocalCascadeBlockStrings provides blockStrings,
        LocalTextSelectionColors provides textSelectionColors,
        LocalOrderedListPrefixStyles provides orderedListPrefixStyles,
        LocalEditorStateHolder provides stateHolder,
        LocalBlockTextStates provides textStates,
        LocalBlockSpanStates provides spanStates,
        LocalSpanActionDispatcher provides spanActionDispatcher,
        LocalFormattingActions provides formattingActions,
        LocalIndentationState provides indentationState,
        LocalIndentationActions provides indentationActions,
        LocalLinkState provides linkState,
        LocalLinkActions provides linkActions,
        LocalLinkOpener provides linkOpener,
        LocalSlashCommandsEnabled provides slashEnabled,
        LocalSlashCommandExecutor provides slashExecutor,
        LocalSlashSessionAnchorBlockId provides slashState?.anchorBlockId,
        LocalSlashHighlightedCommandId provides slashState?.highlightedCommandId,
        LocalSlashCaretRect provides slashCaretRectHolder,
        LocalSlashPopupItems provides slashPopupItems,
    ) {
        val lazyListState = rememberLazyListState()
        val focusManager = LocalFocusManager.current
        val density = LocalDensity.current
        val focusedBlockBottomMarginPx = with(density) {
            FocusedBlockBottomMargin.roundToPx()
        }

        // Scroll-to-focus: ensure the focused block is visible before its
        // TextBlockField requests Compose focus. Also clears Compose focus
        // when editor focus is absent OR block selection mode is active.
        // This prevents keyboard blink when pressing Enter near the bottom
        // of the viewport; the old block keeps keyboard open while we scroll
        // the new block into view.
        LaunchedEffect(stateHolder, lazyListState, focusedBlockBottomMarginPx) {
            snapshotFlow {
                val currentState = stateHolder.state
                currentState.hasSelection to currentState.focusedBlockId
            }.collectLatest { (hasSelection, focusedId) ->
                if (hasSelection || focusedId == null) {
                    focusManager.clearFocus()
                    return@collectLatest
                }
                val currentState = stateHolder.state
                val index = currentState.blocks.indexOfFirst { it.id == focusedId }
                if (index < 0) return@collectLatest

                val layoutInfo = lazyListState.layoutInfo
                val target = focusedBlockScrollTarget(
                    focusedIndex = index,
                    visibleItems = layoutInfo.visibleItemsInfo.map { item ->
                        VisibleLazyListItemBounds(
                            index = item.index,
                            offset = item.offset,
                            size = item.size,
                        )
                    },
                    viewportStartOffset = layoutInfo.viewportStartOffset,
                    viewportEndOffset = layoutInfo.viewportEndOffset,
                    bottomMarginPx = focusedBlockBottomMarginPx,
                )
                when (target) {
                    is FocusedBlockScrollTarget.By ->
                        lazyListState.animateScrollBy(target.deltaPx.toFloat())
                    is FocusedBlockScrollTarget.ToItem ->
                        lazyListState.animateScrollToItem(target.index, target.scrollOffset)
                    null -> Unit
                }
            }
        }

        // Enable placement animation during drag, selection, and briefly after
        // either ends, so reorder and deletion both animate smoothly but
        // keyboard resize doesn't cause overlap.
        var animatePlacement by remember { mutableStateOf(false) }
        LaunchedEffect(state.dragState, state.hasSelection) {
            if (state.dragState != null || state.hasSelection) {
                animatePlacement = true
            } else if (animatePlacement) {
                // Keep animation active briefly so the reorder/deletion
                // animation has time to play.
                delay(500)
                animatePlacement = false
            }
        }
        val selectionOverlayColor =
            LocalCascadeTheme.current.colors.selectionOverlay
        val dimensions = LocalCascadeTheme.current.dimensions
        val blockHorizontalPadding = dimensions.blockHorizontalPadding
        val indentUnitPx = with(density) { dimensions.indentUnit.toPx() }
        val dragHoverOutlineIndex = remember(state.blocks) {
            DragHoverOutlineIndex(state.blocks)
        }
        val currentDragHoverOutlineIndex by rememberUpdatedState(dragHoverOutlineIndex)

        // Current drag Y position — local state, NOT in EditorState.
        // Updated at ~60-120fps during drag; keeping it local avoids full-tree
        // recomposition on every pointer move.
        val dragOffsetY = remember { mutableFloatStateOf(0f) }
        val dragDeltaX = remember { mutableFloatStateOf(0f) }
        val renderDragAffordances = shouldRenderDragAffordances(interactionPolicy, state)

        // Outer Column: editor content (weight=1f) + toolbar at bottom.
        // Toolbar is OUTSIDE the drag gesture Box to prevent drag events
        // hitting toolbar children.
        Column(modifier = modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .blockGestures(
                        lazyListState = lazyListState,
                        dragOffsetY = dragOffsetY,
                        dragDeltaX = dragDeltaX,
                        indentUnitPx = indentUnitPx,
                        stateProvider = { stateHolder.state },
                        outlineIndexProvider = { currentDragHoverOutlineIndex },
                        callbacks = callbacks,
                        policy = interactionPolicy,
                        longPressTimeoutMillis = BLOCK_LONG_PRESS_MS,
                        onEmptySpaceTap = {
                            focusLastTextBlockFromEmptySpace(
                                policy = interactionPolicy,
                                blocks = stateHolder.state.blocks,
                                textStates = textStates,
                                dispatchFocusBlock = { blockId ->
                                    stateHolder.dispatch(FocusBlock(blockId))
                                },
                            )
                        },
                    )
            ) {
                LazyColumn(
                    state = lazyListState,
                    // Disable LazyColumn's built-in scroll gesture during drag so
                    // its Scrollable modifier does not consume pointer events that
                    // belong to our drag handler. Auto-scroll uses dispatchRawDelta
                    // which bypasses gesture handling entirely.
                    userScrollEnabled = !renderDragAffordances && state.slashCommandState == null,
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
                        val isDragging =
                            renderDragAffordances &&
                                state.dragState?.draggingBlockIds?.contains(block.id) == true
                        val hasSelection = state.hasSelection

                        // Look up renderer for this block type (includes unknown-block fallback)
                        val renderer = registry.getRenderer(block.type)

                        Box(
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = null,
                                    placementSpec = if (animatePlacement) spring() else null,
                                    fadeOutSpec = null,
                                )
                        ) {
                            renderer?.Render(
                                block = block,
                                isSelected = isSelected,
                                isFocused = isFocused,
                                modifier = Modifier
                                    .then(
                                        if (isSelected && renderer.handlesSelectionVisual.not()) {
                                            Modifier
                                                .padding(vertical = 2.dp)
                                                .background(
                                                    color = selectionOverlayColor,
                                                    shape = SelectionOverlayShape,
                                                )
                                        } else {
                                            Modifier.padding(vertical = 2.dp)
                                        }
                                    )
                                    .padding(horizontal = blockHorizontalPadding, vertical = 2.dp)
                                    .graphicsLayer {
                                        // Apply 50% transparency to blocks being dragged
                                        alpha = if (isDragging) 0.5f else 1f
                                    },
                                callbacks = callbacks
                            )
                            if (hasSelection) {
                                // overlay to consume all clicks on elements inside blocks
                                Box(
                                    modifier = Modifier.matchParentSize()
                                        .clickable(enabled = false) {}
                                )
                            }
                        }
                    }
                }

                // Auto-scroll when drag gesture enters hot zones near viewport edges.
                // Runs a coroutine loop during drag; cancels automatically when drag ends.
                AutoScrollDuringDrag(
                    lazyListState = lazyListState,
                    dragOffsetY = { dragOffsetY.floatValue },
                    dragDeltaX = { dragDeltaX.floatValue },
                    isDragging = renderDragAffordances,
                    stateProvider = { stateHolder.state },
                    outlineIndexProvider = { currentDragHoverOutlineIndex },
                    indentUnitPx = indentUnitPx,
                    onDropTargetChanged = { hoverTarget ->
                        dispatchDragTargetUpdateIfAllowed(
                            policy = interactionPolicy,
                            callbacks = callbacks,
                            targetIndex = hoverTarget?.visualGap,
                            futureRootIndentationLevel = hoverTarget?.futureRootIndentationLevel,
                        )
                    }
                )

                // Drop indicator overlay - rendered on top of LazyColumn.
                // Canvas does not consume touch events, so gestures pass through.
                if (renderDragAffordances) state.dragState?.let { dragState ->
                    DropIndicator(
                        targetIndex = dragState.targetIndex,
                        futureRootIndentationLevel = dragState.futureRootIndentationLevel,
                        lazyListState = lazyListState
                    )
                }

                // Drag preview overlay - semi-transparent block following the finger.
                // Rendered last in Box so it draws on top of both list and indicator.
                // Position updates happen in graphicsLayer (draw phase only).
                if (renderDragAffordances) state.dragState?.let { dragState ->
                    val primaryBlockId =
                        dragState.primaryRootId ?: dragState.draggingBlockIds.firstOrNull()
                    val draggedBlock = primaryBlockId?.let { id ->
                        state.blocks.find { it.id == id }
                    }
                    if (draggedBlock != null) {
                        DragPreview(
                            block = draggedBlock,
                            dragOffsetY = { dragOffsetY.floatValue },
                            initialTouchOffsetY = dragState.initialTouchOffsetY,
                            futureRootIndentationLevel = dragState.futureRootIndentationLevel,
                            payloadBlockCount = dragState.payloadBlockIds.size,
                            registry = registry,
                            callbacks = callbacks
                        )
                    }
                }

                // Slash command popup overlay - shown when an enabled session is active.
                if (
                    shouldRenderSlashCommandPopup(
                        slashEnabled = slashEnabled,
                        hasSlashState = slashState != null,
                        hasPopupItems = slashPopupItems.isNotEmpty(),
                        hasExecutor = slashExecutor != null,
                    )
                ) {
                    SlashCommandPopup(
                        slashState = slashState!!,
                        stateHolder = stateHolder,
                        slashExecutor = slashExecutor!!,
                    )
                }

                linkPopupController.session?.let { session ->
                    when (val popup = linkPopup) {
                        LinkPopupSlot.Default -> LinkPopup(
                            state = session.state,
                            actions = session,
                        )
                        is LinkPopupSlot.Custom -> popup.content(session.state, session)
                        LinkPopupSlot.None -> Unit
                    }
                }
            }

            // Toolbar
            when (resolvedToolbar) {
                is ToolbarSlot.Default -> if (formattingState != null) {
                    val toolbarSlashEnabled = isToolbarSlashInsertEnabled(
                        slashEnabled = slashEnabled,
                        policy = interactionPolicy,
                    )
                    val currentToolbarSlashEnabled = rememberUpdatedState(toolbarSlashEnabled)
                    // Enablement is read at invocation time through the provider so
                    // stale capture cannot mutate text after a policy flip — the
                    // remember keys intentionally exclude the Boolean.
                    val onToolbarSlashInsert = remember(formattingState, textStates) {
                        createToolbarSlashInsertAction(
                            slashEnabledProvider = { currentToolbarSlashEnabled.value },
                            formattingState = formattingState,
                            textStates = textStates,
                        )
                    }
                    val onLinkClick: () -> Unit = remember(linkPopupController) {
                        { linkPopupController.open() }
                    }
                    val onHideKeyboard: (() -> Unit)? = rememberIosHideKeyboardCallback(stateHolder)
                    RichTextToolbar(
                        formattingState = formattingState,
                        actions = formattingActions,
                        indentationState = indentationState,
                        indentationActions = indentationActions,
                        linkState = linkState,
                        config = resolvedToolbar.config,
                        slashEnabled = toolbarSlashEnabled,
                        onSlashInsert = onToolbarSlashInsert,
                        onLinkClick = onLinkClick,
                        onHideKeyboard = onHideKeyboard,
                    )
                }

                is ToolbarSlot.Custom -> if (formattingState != null) {
                    resolvedToolbar.content(formattingState, formattingActions)
                }

                ToolbarSlot.None -> { /* no toolbar */
                }
            }
        }
    }
}

/**
 * Returns the iOS hide-keyboard callback when running on iOS, or `null`
 * elsewhere. The lambda is `remember`-keyed on the state holder so the toolbar
 * receives a stable reference across recompositions.
 */
@Composable
private fun rememberIosHideKeyboardCallback(
    stateHolder: EditorStateHolder,
): (() -> Unit)? {
    if (!isIos) return null
    return remember(stateHolder) { { stateHolder.dispatch(ClearFocus) } }
}

/**
 * Handles an empty editor-area tap by moving the caret to the end of the final
 * text block, but only while the current interaction policy still allows that
 * edit-focused convenience.
 */
internal fun focusLastTextBlockFromEmptySpace(
    policy: EditorInteractionPolicy,
    blocks: List<Block>,
    textStates: BlockTextStates,
    dispatchFocusBlock: (BlockId) -> Unit,
) {
    if (!canFocusLastTextBlockFromEmptySpace(policy)) return
    val lastTextBlock = blocks.lastOrNull { it.type.supportsText } ?: return
    val visibleText = textStates.getVisibleText(lastTextBlock.id)
    textStates.setCursorPosition(lastTextBlock.id, visibleText?.length ?: 0)
    dispatchFocusBlock(lastTextBlock.id)
}

/**
 * Clears editor-owned transient UI state that can otherwise keep mutating
 * workflows alive after the interaction policy no longer allows them.
 *
 * The dispatched actions only affect UI state: slash session, active drag, and
 * block selection. Document blocks, text focus, caret, and text selection are
 * intentionally left untouched.
 *
 * Synchronous: returns once dispatched cleanup actions have been queued. The
 * caller wraps it in `LaunchedEffect(policy)` so cleanup re-runs whenever the
 * policy identity changes, not because cleanup itself needs to suspend.
 */
internal fun applyReadOnlyTransitionCleanup(
    policy: EditorInteractionPolicy,
    stateHolder: EditorStateHolder,
    linkPopupController: LinkPopupController,
) {
    if (!policy.canUseSlashCommands && stateHolder.state.slashCommandState != null) {
        stateHolder.dispatch(CloseSlashCommand)
    }
    if (!policy.canDragBlocks && stateHolder.state.dragState != null) {
        stateHolder.dispatch(CancelDrag)
    }
    if (!policy.canSelectBlocks && stateHolder.state.selectedBlockIds.isNotEmpty()) {
        stateHolder.dispatch(ClearSelection)
    }
    if (!policy.canEditLinks) {
        linkPopupController.forceDismiss()
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
 * Resolves the editor-owned slash subsystem gate from the public slot and
 * interaction policy.
 *
 * The toolbar applies an additional text-edit gate because its slash button
 * directly writes into the focused text field.
 */
internal fun isSlashCommandSubsystemEnabled(
    slashCommand: SlashCommandSlot,
    policy: EditorInteractionPolicy,
): Boolean {
    return slashCommand !is SlashCommandSlot.None && policy.canUseSlashCommands
}

/**
 * Resolves whether the default toolbar's slash insert control can edit text.
 */
internal fun isToolbarSlashInsertEnabled(
    slashEnabled: Boolean,
    policy: EditorInteractionPolicy,
): Boolean {
    return slashEnabled && policy.canEditText
}

/**
 * Builds the default toolbar slash action, checking enablement at invocation
 * time so a callback captured before a runtime policy change cannot mutate text
 * after slash insertion becomes unavailable.
 */
internal fun createToolbarSlashInsertAction(
    slashEnabledProvider: () -> Boolean,
    formattingState: State<FormattingState>,
    textStates: BlockTextStates,
): () -> Unit {
    return action@{
        if (!slashEnabledProvider()) return@action
        val blockId = formattingState.value.focusedBlockId ?: return@action
        val textFieldState = textStates.get(blockId) ?: return@action
        insertSlashTriggerAtVisibleSelection(textFieldState)
    }
}

/**
 * Inserts the slash trigger using visible-text coordinates.
 *
 * Editor text fields store an internal sentinel before visible text, so the raw
 * mutation offsets are shifted by one.
 */
internal fun insertSlashTriggerAtVisibleSelection(textFieldState: TextFieldState) {
    val selection = textFieldState.visibleSelection()
    textFieldState.edit {
        replace(selection.min + 1, selection.max + 1, "/")
        this.selection = TextRange(selection.min + 2)
    }
}

/**
 * Resolves whether the editor-owned slash popup has all dependencies needed to render.
 */
internal fun shouldRenderSlashCommandPopup(
    slashEnabled: Boolean,
    hasSlashState: Boolean,
    hasPopupItems: Boolean,
    hasExecutor: Boolean,
): Boolean {
    return slashEnabled && hasSlashState && hasPopupItems && hasExecutor
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
