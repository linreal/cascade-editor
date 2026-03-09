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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import io.github.linreal.cascade.editor.action.CloseSlashCommand
import io.github.linreal.cascade.editor.action.UpdateSlashCommandSession
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.richtext.SpanMapper
import io.github.linreal.cascade.editor.richtext.SpanMaintenanceTextObserver
import io.github.linreal.cascade.editor.slash.SlashCommandTextObserver
import io.github.linreal.cascade.editor.ui.BackspaceAwareTextField
import io.github.linreal.cascade.editor.ui.LocalBlockSpanStates
import io.github.linreal.cascade.editor.ui.LocalBlockTextStates
import io.github.linreal.cascade.editor.ui.LocalSlashSessionAnchorBlockId
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

    // Get TextFieldState from the shared holder
    val blockTextStates = LocalBlockTextStates.current
    val textFieldState = remember(block.id) {
        blockTextStates.getOrCreate(block.id, textContent.text)
    }
    val slashSessionAnchorBlockId = LocalSlashSessionAnchorBlockId.current

    // Get span state from the shared holder
    val blockSpanStates = LocalBlockSpanStates.current
    val spanState = remember(block.id) {
        blockSpanStates.getOrCreate(block.id, textContent.spans, textContent.text.length)
    }
    val outputTransformation = remember(block.id, spanState) {
        OutputTransformation {
            SpanMapper.run { applyStyles(spanState.value) }
        }
    }
    val spanTextObserver = remember(block.id, blockTextStates, blockSpanStates) {
        SpanMaintenanceTextObserver(
            blockId = block.id,
            blockTextStates = blockTextStates,
            blockSpanStates = blockSpanStates,
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
    LaunchedEffect(textFieldState, spanTextObserver, slashTextObserver) {
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
                val isProgrammatic = blockTextStates.hasPendingProgrammaticCommit(block.id)
                val cursor = if (selection.collapsed) selection.start else -1
                if (currentVisibleText != lastObservedVisibleText) {
                    slashTextObserver.onTextChanged(currentVisibleText, isProgrammatic, cursor)
                    spanTextObserver.onCommittedVisibleText(currentVisibleText)
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

    Box(modifier = modifier) {
        BackspaceAwareTextField(
            state = textFieldState,
            modifier = Modifier.fillMaxWidth()
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
