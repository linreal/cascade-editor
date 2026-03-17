package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import io.github.linreal.cascade.editor.action.CloseSlashCommand
import io.github.linreal.cascade.editor.action.HighlightSlashCommand
import io.github.linreal.cascade.editor.action.UpdateSlashCommandSession
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.action.ConvertBlockType
import io.github.linreal.cascade.editor.action.UpdateBlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.richtext.SpanMapper
import io.github.linreal.cascade.editor.richtext.SpanMaintenanceTextObserver
import io.github.linreal.cascade.editor.slash.SlashCommandTextObserver
import io.github.linreal.cascade.editor.ui.observers.ListAutoDetectObserver
import io.github.linreal.cascade.editor.ui.BackspaceAwareTextField
import io.github.linreal.cascade.editor.ui.LocalBlockSpanStates
import io.github.linreal.cascade.editor.ui.LocalBlockTextStates
import io.github.linreal.cascade.editor.ui.LocalSlashCaretRect
import io.github.linreal.cascade.editor.ui.LocalSlashCommandExecutor
import io.github.linreal.cascade.editor.ui.LocalSlashHighlightedCommandId
import io.github.linreal.cascade.editor.ui.LocalSlashPopupItems
import io.github.linreal.cascade.editor.ui.LocalSlashSessionAnchorBlockId
import io.github.linreal.cascade.editor.ui.SlashPopupDefaults
import io.github.linreal.cascade.editor.ui.visibleSelection
import io.github.linreal.cascade.editor.ui.visibleText

/**
 * Shared text editing composable used by renderers that need formattable text input.
 *
 * Encapsulates all text editing infrastructure: [TextFieldState] lookup, span state,
 * [OutputTransformation], focus management, [BackspaceAwareTextField], and the
 * unfocused tap overlay for cursor placement.
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
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var hasComposeFocus by remember { mutableStateOf(false) }

    // Get TextFieldState from the shared holder.
    // generation key invalidates the cache when loadFromJson clears all states.
    val textStates = LocalBlockTextStates.current
    val textFieldState = remember(block.id, textStates.generation) {
        textStates.getOrCreate(block.id, textContent.text)
    }
    val slashCommandExecutor = LocalSlashCommandExecutor.current
    val slashSessionAnchorBlockId = LocalSlashSessionAnchorBlockId.current
    val slashHighlightedCommandId = LocalSlashHighlightedCommandId.current
    val slashCaretRectHolder = LocalSlashCaretRect.current
    val slashPopupItems = LocalSlashPopupItems.current

    // Get span state from the shared holder — same generation-key rationale.
    val spanStates = LocalBlockSpanStates.current
    val spanState = remember(block.id, spanStates.generation) {
        spanStates.getOrCreate(block.id, textContent.spans, textContent.text.length)
    }
    val outputTransformation = remember(block.id, spanState) {
        OutputTransformation {
            SpanMapper.run { applyStyles(spanState.value) }
        }
    }
    val spanTextObserver = remember(block.id, textStates, spanStates) {
        SpanMaintenanceTextObserver(
            blockId = block.id,
            textStates = textStates,
            spanStates = spanStates,
            initialVisibleText = textFieldState.visibleText(),
        )
    }
    val slashTextObserver = remember(block.id, callbacks) {
        SlashCommandTextObserver(
            blockId = block.id,
            onOpen = { id, range, query -> callbacks.onSlashCommand(id, range, query) },
            onUpdate = { query, range -> callbacks.dispatch(UpdateSlashCommandSession(query, range)) },
            onClose = { callbacks.dispatch(CloseSlashCommand) },
            initialVisibleText = textFieldState.visibleText(),
        )
    }
    val isCurrentlyList = block.type is BlockType.BulletList || block.type is BlockType.NumberedList
    val listAutoDetectObserver = remember(block.id, isCurrentlyList, callbacks, textStates, spanStates) {
        ListAutoDetectObserver(
            isListBlock = { isCurrentlyList },
            onListDetected = { newType, prefixLength ->
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
            },
            initialVisibleText = textFieldState.visibleText(),
        )
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
        } else if (hasComposeFocus) {
            focusManager.clearFocus()
        }
    }

    // Keep observer tracking in sync when slash session closes or moves externally.
    LaunchedEffect(slashSessionAnchorBlockId, slashTextObserver, block.id) {
        if (slashSessionAnchorBlockId != block.id && slashTextObserver.isTracking) {
            slashTextObserver.notifySessionClosed()
        }
    }

    // Combined text + selection observation keeps ordering deterministic.
    // We observe raw text snapshots and only derive visible text when text actually changes.
    LaunchedEffect(textFieldState, spanTextObserver, slashTextObserver, listAutoDetectObserver) {
        var lastObservedVisibleText = textFieldState.visibleText()
        var lastObservedTextSnapshot = textFieldState.text

        snapshotFlow {
            Pair(textFieldState.text, textFieldState.visibleSelection())
        }.collect { (currentTextSnapshot, selection) ->
            val textSnapshotChanged =
                currentTextSnapshot !== lastObservedTextSnapshot ||
                    currentTextSnapshot.length != lastObservedTextSnapshot.length

            if (textSnapshotChanged) {
                val currentVisibleText = visibleTextFromSnapshot(currentTextSnapshot)
                // Peek before span observer consumes the commit
                val isProgrammatic = textStates.hasPendingProgrammaticCommit(block.id)
                val cursor = if (selection.collapsed) selection.start else -1
                if (currentVisibleText != lastObservedVisibleText) {
                    slashTextObserver.onTextChanged(currentVisibleText, isProgrammatic, cursor)
                    spanTextObserver.onCommittedVisibleText(currentVisibleText)
                    listAutoDetectObserver.onTextChanged(currentVisibleText, isProgrammatic)
                    lastObservedVisibleText = currentVisibleText
                } else {
                    // Snapshot identity can change without visible text mutation.
                    slashTextObserver.onSelectionChanged(selection.start, selection.end)
                }
                lastObservedTextSnapshot = currentTextSnapshot
            } else {
                // Selection-only change: check slash session cursor validity
                slashTextObserver.onSelectionChanged(selection.start, selection.end)
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

    Box(modifier = modifier) {
        BackspaceAwareTextField(
            state = textFieldState,
            modifier = Modifier.fillMaxWidth()
                .onGloballyPositioned { coords -> layoutCoordinates = coords }
                .onPreviewKeyEvent { keyEvent ->
                    if (!isSlashAnchor) return@onPreviewKeyEvent false
                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            val nextId = SlashPopupDefaults.resolveNextHighlight(
                                slashHighlightedCommandId, slashPopupItems, direction = -1
                            )
                            callbacks.dispatch(HighlightSlashCommand(nextId))
                            true
                        }
                        Key.DirectionDown -> {
                            val nextId = SlashPopupDefaults.resolveNextHighlight(
                                slashHighlightedCommandId, slashPopupItems, direction = 1
                            )
                            callbacks.dispatch(HighlightSlashCommand(nextId))
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            val highlightedItemId = slashHighlightedCommandId
                            val highlightedItem = if (highlightedItemId != null) {
                                slashPopupItems.firstOrNull { it.id == highlightedItemId }
                            } else {
                                null
                            }
                            val executor = slashCommandExecutor
                            if (highlightedItem != null && executor != null) {
                                executor.execute(highlightedItem)
                                true
                            } else {
                                false
                            }
                        }
                        Key.Escape -> {
                            callbacks.dispatch(CloseSlashCommand)
                            true
                        }
                        else -> false
                    }
                }
                .onFocusChanged { focusState ->
                    val wasFocused = hasComposeFocus
                    hasComposeFocus = focusState.isFocused
                    if (focusState.isFocused && !isFocused) {
                        callbacks.onFocus(block.id)
                    }
                    if (!focusState.isFocused && wasFocused) {
                        slashTextObserver.onFocusLost()
                    }
                },
            textStyle = textStyle,
            outputTransformation = outputTransformation,
            onTextLayout = { result -> textLayoutResult = result() },
            focusRequester = focusRequester,
            onBackspaceAtStart = {
                callbacks.onBackspaceAtStart(block.id)
            },
            onEnterPressed = { cursorPosition ->
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
                    executor.execute(highlightedItem)
                    return@BackspaceAwareTextField
                }
                callbacks.onEnter(block.id, cursorPosition)
            }
        )

        if (!isFocused) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(block.id) {
                        detectTapGestures(
                            onTap = { offset ->
                                val cursorIndex = textLayoutResult?.getOffsetForPosition(offset)
                                    ?: textFieldState.text.length
                                textFieldState.edit {
                                    selection = TextRange(cursorIndex)
                                }
                                focusRequester.requestFocus()
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
