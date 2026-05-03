package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import io.github.linreal.cascade.editor.action.CloseSlashCommand
import io.github.linreal.cascade.editor.action.UpdateSlashCommandSession
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.action.ConvertBlockType
import io.github.linreal.cascade.editor.action.UpdateBlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.richtext.SpanMapper
import io.github.linreal.cascade.editor.richtext.LinkHitTester
import io.github.linreal.cascade.editor.richtext.SpanMaintenanceTextObserver
import io.github.linreal.cascade.editor.slash.SlashCommandTextObserver
import io.github.linreal.cascade.editor.ui.observers.ListAutoDetectObserver
import io.github.linreal.cascade.editor.ui.BackspaceAwareTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import io.github.linreal.cascade.editor.ui.LocalBlockSpanStates
import io.github.linreal.cascade.editor.ui.LocalBlockTextStates
import io.github.linreal.cascade.editor.ui.LocalEditorStateHolder
import io.github.linreal.cascade.editor.ui.LocalFormattingActions
import io.github.linreal.cascade.editor.ui.LocalLinkOpener
import io.github.linreal.cascade.editor.ui.LocalSlashCaretRect
import io.github.linreal.cascade.editor.ui.LocalSlashCommandExecutor
import io.github.linreal.cascade.editor.ui.LocalSlashHighlightedCommandId
import io.github.linreal.cascade.editor.ui.LocalSlashPopupItems
import io.github.linreal.cascade.editor.ui.LocalSlashSessionAnchorBlockId
import io.github.linreal.cascade.editor.ui.visibleSelection
import io.github.linreal.cascade.editor.ui.visibleText
import io.github.linreal.cascade.editor.state.TextEditHistoryTracker
import io.github.linreal.cascade.editor.state.captureCheckpoint
import io.github.linreal.cascade.editor.state.captureFocusedEditingUiState
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme

/**
 * Shared text editing composable used by renderers that need formattable text input.
 *
 * Encapsulates all text editing infrastructure: [TextFieldState] lookup, span state,
 * [OutputTransformation], focus management, [BackspaceAwareTextField], and the
 * unfocused tap overlay for cursor placement.
 *
 * **Focus contract:** this composable does NOT call `clearFocus()` when [isFocused]
 * becomes false. The hosting composable (currently [CascadeEditor][io.github.linreal.cascade.editor.ui.CascadeEditor])
 * is responsible for clearing Compose focus when `focusedBlockId` becomes null.
 * This avoids keyboard blink during block-to-block focus transitions.
 *
 * @param block The block being rendered
 * @param isFocused Whether this block currently has editor focus
 * @param textStyle The text style to apply
 * @param modifier Modifier for the root container
 * @param callbacks Block interaction callbacks
 */
@Composable
internal fun TextBlockField(
    block: Block,
    isFocused: Boolean,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    callbacks: BlockCallbacks,
) {
    val textContent = block.content as? BlockContent.Text ?: return
    val colors = LocalCascadeTheme.current.colors
    val focusRequester = remember { FocusRequester() }
    var hasComposeFocus by remember { mutableStateOf(false) }
    val stateHolder = LocalEditorStateHolder.current

    // Get TextFieldState from the shared holder.
    // generation key invalidates the cache when loadFromJson clears all states.
    val textStates = LocalBlockTextStates.current
    val textFieldState = remember(block.id, textStates.generation) {
        textStates.getOrCreate(block.id, textContent.text)
    }
    val formattingActions = LocalFormattingActions.current
    val slashCommandExecutor = LocalSlashCommandExecutor.current
    val slashSessionAnchorBlockId = LocalSlashSessionAnchorBlockId.current
    val slashHighlightedCommandId = LocalSlashHighlightedCommandId.current
    val slashCaretRectHolder = LocalSlashCaretRect.current
    val slashPopupItems = LocalSlashPopupItems.current
    // rememberUpdatedState keeps the latest opener available to the unfocused-tap
    // pointerInput without re-keying it. This matters because consumers commonly
    // pass an unstable lambda for onOpenLink, which would otherwise cancel and
    // restart the pointer-input coroutine on every recomposition of CascadeEditor.
    val currentLinkOpener by rememberUpdatedState(LocalLinkOpener.current)

    // Block types may opt out of spans (e.g. BlockType.Code). When opted out, span
    // state init, OutputTransformation, and the span maintenance observer all
    // collapse to span-free no-ops. Adding `supportsSpans` to the remember keys
    // forces fresh runtime state on same-id conversion (Paragraph -> Code or back).
    val supportsSpans = block.type.supportsSpans

    // Get span state from the shared holder — same generation-key rationale.
    val spanStates = LocalBlockSpanStates.current
    val spanState = remember(block.id, spanStates.generation, supportsSpans) {
        val initialSpans = if (supportsSpans) textContent.spans else emptyList()
        val state = spanStates.getOrCreate(block.id, initialSpans, textContent.text.length)
        if (!supportsSpans) {
            // Same-id conversion (Paragraph -> Code) keeps the existing State entry,
            // so getOrCreate returns the previous paragraph spans untouched. Reset them
            // here, and drop any pending styles so a later conversion back to a
            // spans-supporting type doesn't re-introduce stale toggles.
            spanStates.set(block.id, emptyList(), textContent.text.length)
            spanStates.clearPendingStyles(block.id)
        }
        state
    }
    val inlineCodeBackground = colors.inlineCodeBackground
    val highlightBackground = colors.highlight
    val linkText = colors.linkText
    val baseDecoration = textStyle.textDecoration
    val outputTransformation: OutputTransformation? = remember(
        block.id,
        spanState,
        supportsSpans,
        inlineCodeBackground,
        highlightBackground,
        linkText,
        baseDecoration,
    ) {
        if (!supportsSpans) {
            null
        } else {
            OutputTransformation {
                SpanMapper.run { applyStyles(spanState.value, inlineCodeBackground, highlightBackground, linkText, baseDecoration) }
            }
        }
    }
    val spanTextObserver: SpanMaintenanceTextObserver? = remember(block.id, textStates, spanStates, supportsSpans) {
        if (!supportsSpans) {
            null
        } else {
            SpanMaintenanceTextObserver(
                blockId = block.id,
                textStates = textStates,
                spanStates = spanStates,
                initialVisibleText = textFieldState.visibleText(),
            )
        }
    }
    // Code blocks suppress slash-menu detection: typing `/` in code is treated as
    // literal text. Suppression lives at the call site (no construction) so a stale
    // observer cannot emit UpdateSlashCommandSession / CloseSlashCommand after a
    // same-id Paragraph -> Code conversion.
    val slashSuppressed = block.type is BlockType.Code
    val slashTextObserver: SlashCommandTextObserver? = remember(
        block.id,
        callbacks,
        textStates.generation,
        slashSuppressed,
    ) {
        if (slashSuppressed) {
            null
        } else {
            SlashCommandTextObserver(
                blockId = block.id,
                onOpen = { id, range, query -> callbacks.onSlashCommand(id, range, query) },
                onUpdate = { query, range -> callbacks.dispatch(UpdateSlashCommandSession(query, range)) },
                onClose = { callbacks.dispatch(CloseSlashCommand) },
                initialVisibleText = textFieldState.visibleText(),
            )
        }
    }
    val textHistoryTracker = remember(block.id, stateHolder, textStates.generation, spanStates.generation) {
        // Generation only changes on hard runtime resets (load/replay clear paths).
        // By the time this tracker is recreated, the holder has already restored
        // the authoritative runtime state for the current composition pass.
        TextEditHistoryTracker(
            initialCheckpoint = stateHolder.captureCheckpoint(textStates, spanStates),
        )
    }
    DisposableEffect(block.id, stateHolder, textHistoryTracker) {
        // Toolbar-driven formatting bypasses this composable's snapshotFlow, so
        // the holder needs a back-reference to the live tracker for this block.
        stateHolder.registerTextHistoryTracker(block.id, textHistoryTracker)
        onDispose {
            stateHolder.unregisterTextHistoryTracker(block.id, textHistoryTracker)
        }
    }
    val isCurrentlyList = block.type is BlockType.BulletList || block.type is BlockType.NumberedList
    // Treat code blocks as already-list to short-circuit list auto-detect: typing
    // `- ` or `1. ` inside code is literal text. Same-id Paragraph -> Code drops the
    // old observer through the remember key, so a stale paragraph observer cannot
    // convert the new code block.
    val suppressListAutoDetect = isCurrentlyList || block.type is BlockType.Code
    val listAutoDetectObserver = remember(
        block.id,
        suppressListAutoDetect,
        callbacks,
        textStates,
        spanStates,
        textHistoryTracker,
    ) {
        ListAutoDetectObserver(
            isListBlock = { suppressListAutoDetect },
            onListDetected = { newType, prefixLength ->
                stateHolder.runStructuralHistoryTransaction(textStates, spanStates) {
                    textHistoryTracker.noteBatchBreaker()
                    // Remove trigger prefix and adjust spans
                    spanStates.adjustForRangeReplacement(
                        blockId = block.id,
                        start = 0,
                        endExclusive = prefixLength,
                        replacementLength = 0,
                    )
                    val newText = textStates.replaceVisibleRange(
                        blockId = block.id,
                        start = 0,
                        endExclusive = prefixLength,
                        replacement = "",
                        cursorPositionAfter = 0,
                    )
                    // Sync snapshot with cleaned text + adjusted spans before converting
                    if (newText != null) {
                        val spans = spanStates.getSpans(block.id)
                        callbacks.dispatch(UpdateBlockContent(block.id, BlockContent.Text(newText, spans)))
                    }
                    callbacks.dispatch(ConvertBlockType(block.id, newType))
                }
            },
            initialVisibleText = textFieldState.visibleText(),
        )
    }

    LaunchedEffect(isFocused, textHistoryTracker, stateHolder, textStates, spanStates) {
        textHistoryTracker.noteFocusChanged(
            stateHolder.captureCheckpoint(textStates, spanStates)
        )
        if (isFocused) {
            focusRequester.requestFocus()
        }
        // Don't call clearFocus() here when isFocused becomes false.
        // When focus moves to another block, that block's requestFocus()
        // moves Compose focus automatically, keeping the keyboard open.
        // CascadeEditor handles clearFocus() when focusedBlockId becomes null.
    }

    // Keep observer tracking in sync when slash session closes or moves externally.
    LaunchedEffect(slashSessionAnchorBlockId, slashTextObserver, block.id) {
        val observer = slashTextObserver ?: return@LaunchedEffect
        if (slashSessionAnchorBlockId != block.id && observer.isTracking) {
            observer.notifySessionClosed()
        }
    }

    // Combined text + selection observation keeps ordering deterministic.
    // We observe raw text snapshots and only derive visible text when text actually changes.
    LaunchedEffect(
        textFieldState,
        spanTextObserver,
        slashTextObserver,
        listAutoDetectObserver,
        stateHolder,
        textHistoryTracker,
    ) {
        var lastObservedVisibleText = textFieldState.visibleText()
        var lastObservedTextSnapshot = textFieldState.text
        fun noteSelectionState(selection: TextRange) {
            textHistoryTracker.noteSelectionChanged(
                selection = selection,
                ui = captureFocusedEditingUiState(stateHolder.state, textStates, spanStates),
            )
            slashTextObserver?.onSelectionChanged(selection.start, selection.end)
        }

        snapshotFlow {
            Pair(textFieldState.text, textFieldState.visibleSelection())
        }.collect { (currentTextSnapshot, selection) ->
            val textSnapshotChanged =
                currentTextSnapshot !== lastObservedTextSnapshot ||
                    currentTextSnapshot.length != lastObservedTextSnapshot.length
            val currentVisibleText = if (textSnapshotChanged) {
                visibleTextFromSnapshot(currentTextSnapshot)
            } else {
                lastObservedVisibleText
            }

            if (stateHolder.isApplyingHistory) {
                if (textSnapshotChanged) {
                    lastObservedVisibleText = currentVisibleText
                    lastObservedTextSnapshot = currentTextSnapshot
                }
                // Replay baseline sync is driven by the holder:
                // - BlockTextEntry replay patches the registered tracker directly
                // - Structural replay clears runtime holders, forcing tracker recreation
                return@collect
            }

            if (textSnapshotChanged) {
                // Peek before span observer consumes the commit
                val isProgrammatic = textStates.hasPendingProgrammaticCommit(block.id)
                val cursor = if (selection.collapsed) selection.start else -1
                if (currentVisibleText != lastObservedVisibleText) {
                    slashTextObserver?.onTextChanged(currentVisibleText, isProgrammatic, cursor)
                    spanTextObserver?.onCommittedVisibleText(currentVisibleText)
                    // SpanMaintenanceTextObserver is the canonical consumer. When the
                    // block opts out of spans (e.g. Code) the observer is null, so the
                    // pending entry would persist and misclassify the next real user
                    // edit as programmatic. Consume here when no observer owns it.
                    if (isProgrammatic && spanTextObserver == null) {
                        textStates.consumeProgrammaticCommit(block.id)
                    }
                    if (isProgrammatic) {
                        textHistoryTracker.onProgrammaticCommit(
                            stateHolder.captureCheckpoint(textStates, spanStates)
                        )
                    } else {
                        val afterCheckpoint = stateHolder.captureCheckpoint(textStates, spanStates)
                        textHistoryTracker.onUserTextCommit(afterCheckpoint)?.let { push ->
                            stateHolder.pushHistoryEntry(push.entry, push.policy)
                        }
                    }
                    listAutoDetectObserver.onTextChanged(currentVisibleText, isProgrammatic)
                    lastObservedVisibleText = currentVisibleText
                } else {
                    // Snapshot identity can change without visible text mutation
                    // (e.g. setText with identical content during split).
                    // Consume any stale programmatic commit so it doesn't cause
                    // the next real user edit to be misidentified as programmatic.
                    if (isProgrammatic) {
                        textStates.consumeProgrammaticCommit(block.id)
                        textHistoryTracker.onProgrammaticCommit(
                            stateHolder.captureCheckpoint(textStates, spanStates)
                        )
                    } else {
                        noteSelectionState(selection)
                    }
                }
                lastObservedTextSnapshot = currentTextSnapshot
            } else {
                // Selection-only change: check slash session cursor validity
                noteSelectionState(selection)
            }
        }
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val isSlashAnchor = slashSessionAnchorBlockId == block.id

    // Report caret rect in window coordinates when this block is the slash anchor.
    LaunchedEffect(isSlashAnchor, textLayoutResult, layoutCoordinates, textFieldState.selection) {
        if (!isSlashAnchor) {
            // Only the previous anchor owner can clear the shared caret rect.
            slashCaretRectHolder.clear(block.id)
            return@LaunchedEffect
        }
        val layout = textLayoutResult ?: return@LaunchedEffect
        val coords = layoutCoordinates ?: return@LaunchedEffect
        if (!coords.isAttached) return@LaunchedEffect

        val cursorOffset = textFieldState.selection.start
        val safeOffset = cursorOffset.coerceIn(0, layout.layoutInput.text.length)
        val localCursorRect = layout.getCursorRect(safeOffset)
        val windowTopLeft = coords.localToWindow(
            androidx.compose.ui.geometry.Offset(localCursorRect.left, localCursorRect.top)
        )
        val windowBottomRight = coords.localToWindow(
            androidx.compose.ui.geometry.Offset(localCursorRect.right, localCursorRect.bottom)
        )
        slashCaretRectHolder.update(
            blockId = block.id,
            caretRect = Rect(
                left = windowTopLeft.x,
                top = windowTopLeft.y,
                right = windowBottomRight.x,
                bottom = windowBottomRight.y,
            )
        )
    }

    val cursorBrush = remember(colors.cursor) { SolidColor(colors.cursor) }

    val keyHandler = remember(formattingActions, callbacks, textHistoryTracker, stateHolder) {
        TextBlockKeyHandler(
            formattingActions = formattingActions,
            callbacks = callbacks,
            isSlashAnchor = { isSlashAnchor },
            slashHighlightedCommandId = { slashHighlightedCommandId },
            slashPopupItems = { slashPopupItems },
            slashCommandExecutor = { slashCommandExecutor },
            onBatchBreaker = { textHistoryTracker.noteBatchBreaker() },
            onPasteShortcutDetected = { textHistoryTracker.noteExplicitPaste() },
            onUndo = stateHolder::undo,
            onRedo = stateHolder::redo,
        )
    }
    var handledPhysicalEnterKey by remember { mutableStateOf<Key?>(null) }

    fun handleEditorEnter(cursorPosition: Int) {
        val highlightedItemId = slashHighlightedCommandId
        val highlightedItem = if (highlightedItemId != null) {
            slashPopupItems.firstOrNull { it.id == highlightedItemId }
        } else {
            null
        }
        val executor = slashCommandExecutor
        if (
            slashSessionAnchorBlockId == block.id &&
            highlightedItem != null &&
            executor != null
        ) {
            textHistoryTracker.noteBatchBreaker()
            executor.execute(highlightedItem)
            return
        }
        textHistoryTracker.noteBatchBreaker()
        callbacks.onEnter(block.id, cursorPosition)
    }

    fun handlePhysicalEnterKeyEvent(keyEvent: KeyEvent): Boolean {
        val enterKey = when (keyEvent.key) {
            Key.Enter, Key.NumPadEnter -> keyEvent.key
            else -> null
        } ?: return false

        if (keyEvent.isShiftPressed) return false

        return when (keyEvent.type) {
            KeyEventType.KeyUp -> {
                if (handledPhysicalEnterKey == enterKey) {
                    handledPhysicalEnterKey = null
                    true
                } else {
                    false
                }
            }
            KeyEventType.KeyDown -> {
                if (handledPhysicalEnterKey != enterKey) {
                    handledPhysicalEnterKey = enterKey
                    handleEditorEnter(textFieldState.visibleSelection().start)
                }
                true
            }
            else -> true
        }
    }

    Box(modifier = modifier) {
        BackspaceAwareTextField(
            state = textFieldState,
            modifier = Modifier.fillMaxWidth()
                .onGloballyPositioned { coords -> layoutCoordinates = coords }
                .onPreviewKeyEvent {
                    keyHandler.onPreviewKeyEvent(it) || handlePhysicalEnterKeyEvent(it)
                }
                .onFocusChanged { focusState ->
                    val wasFocused = hasComposeFocus
                    hasComposeFocus = focusState.isFocused
                    if (focusState.isFocused && !isFocused) {
                        callbacks.onFocus(block.id)
                    }
                    if (!focusState.isFocused && wasFocused) {
                        slashTextObserver?.onFocusLost()
                        // KeyUp after a split lands on the new block (focus has already
                        // migrated), so it never clears this block's KeyDown marker.
                        // Reset on focus loss so a later re-focus can handle Enter again.
                        handledPhysicalEnterKey = null
                    }
                },
            textStyle = textStyle,
            cursorBrush = cursorBrush,
            outputTransformation = outputTransformation,
            onTextLayout = { result -> textLayoutResult = result() },
            focusRequester = focusRequester,
            onBackspaceAtStart = {
                textHistoryTracker.noteBatchBreaker()
                callbacks.onBackspaceAtStart(block.id)
            },
            onEnterPressed = { cursorPosition ->
                handleEditorEnter(cursorPosition)
            }
        )

        if (!isFocused) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    // Keys deliberately exclude the link opener: the opener is read
                    // through the captured State, which always returns the latest
                    // value without restarting the pointer-input coroutine.
                    .pointerInput(block.id) {
                        detectTapGestures(
                            onTap = { offset ->
                                val layout = textLayoutResult
                                val cursorIndex = layout?.getOffsetForPosition(offset)
                                    ?: textFieldState.text.length
                                val currentEditorState = stateHolder.state
                                val hasTextSelection = currentEditorState.focusedBlockId?.let { focusedBlockId ->
                                    textStates.getSelection(focusedBlockId)?.collapsed == false
                                } == true
                                // The overlay is only mounted when the block is unfocused, so we
                                // pass `isFocused = false` literally. LinkHitTester still validates
                                // drag / block-selection / text-selection gating internally.
                                val linkUrl = LinkHitTester.linkUrlForTap(
                                    isFocused = false,
                                    isDragging = currentEditorState.dragState != null,
                                    hasBlockSelection = currentEditorState.hasSelection,
                                    hasTextSelection = hasTextSelection,
                                    visibleOffset = layout?.let {
                                        visibleOffsetForRawOffset(textFieldState.text, cursorIndex)
                                    },
                                    // Non-spans block types cannot host links. Hand the hit
                                    // tester an empty list so a malformed runtime span list
                                    // (left over from a prior type) cannot surface a tappable link.
                                    spans = if (supportsSpans) spanState.value else emptyList(),
                                )
                                val opener = currentLinkOpener
                                if (linkUrl != null && opener != null) {
                                    opener(linkUrl)
                                } else {
                                    textFieldState.edit {
                                        selection = TextRange(cursorIndex)
                                    }
                                    // Route focus through editor callbacks so selection mode
                                    // can veto focus (focus/selection are mutually exclusive).
                                    callbacks.onFocus(block.id)
                                }
                            }
                        )
                    }
            )
        }
    }
}

private const val ZWSP_SENTINEL: Char = '\u200B'

private fun visibleTextFromSnapshot(text: CharSequence): String {
    if (text.isEmpty()) return ""
    return if (text[0] == ZWSP_SENTINEL) {
        text.subSequence(1, text.length).toString()
    } else {
        text.toString()
    }
}

/**
 * Converts a BasicTextField buffer offset, which includes the invisible leading
 * sentinel when present, into the editor's public visible-text coordinate space.
 */
private fun visibleOffsetForRawOffset(text: CharSequence, rawOffset: Int): Int {
    val sentinelOffset = if (text.isNotEmpty() && text[0] == ZWSP_SENTINEL) 1 else 0
    val visibleLength = (text.length - sentinelOffset).coerceAtLeast(0)
    return (rawOffset - sentinelOffset).coerceIn(0, visibleLength)
}
