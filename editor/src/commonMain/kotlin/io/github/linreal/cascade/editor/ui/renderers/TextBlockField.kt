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
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.richtext.SpanMapper
import io.github.linreal.cascade.editor.richtext.SpanMaintenanceTextObserver
import io.github.linreal.cascade.editor.ui.BackspaceAwareTextField
import io.github.linreal.cascade.editor.ui.LocalBlockSpanStates
import io.github.linreal.cascade.editor.ui.LocalBlockTextStates
import io.github.linreal.cascade.editor.ui.visibleText
import kotlinx.coroutines.flow.collect

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

    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
        } else if (hasComposeFocus) {
            focusManager.clearFocus()
        }
    }
    LaunchedEffect(textFieldState, spanTextObserver) {
        snapshotFlow { textFieldState.visibleText() }.collect { currentVisibleText ->
            spanTextObserver.onCommittedVisibleText(currentVisibleText)
        }
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(modifier = modifier) {
        BackspaceAwareTextField(
            state = textFieldState,
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { focusState ->
                    hasComposeFocus = focusState.isFocused
                    if (focusState.isFocused && !isFocused) {
                        callbacks.onFocus(block.id)
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
