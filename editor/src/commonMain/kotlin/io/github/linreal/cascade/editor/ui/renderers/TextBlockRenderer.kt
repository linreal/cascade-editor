package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.loge
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockRenderer
import io.github.linreal.cascade.editor.ui.BackspaceAwareTextField
import io.github.linreal.cascade.editor.ui.LocalBlockTextStates

/**
 * Renderer for text-supporting block types (paragraph, headings, lists, etc.).
 *
 * Uses [BackspaceAwareTextField] for text editing with backspace detection.
 * Handles focus management internally based on [isFocused] parameter.
 *
 * Text state is managed via [LocalBlockTextStates], which provides a
 * [TextFieldState] per block. This enables direct manipulation of text
 * for operations like merge without LaunchedEffect syncing.
 */
public class TextBlockRenderer : BlockRenderer<BlockType> {

    @Composable
    override fun Render(
        block: Block,
        isSelected: Boolean,
        isFocused: Boolean,
        modifier: Modifier,
        callbacks: BlockCallbacks
    ) {
        val textContent = block.content as? BlockContent.Text ?: return
        val focusRequester = remember { FocusRequester() }

        // Get TextFieldState from the shared holder
        val blockTextStates = LocalBlockTextStates.current
        val textFieldState = remember(block.id) {
            blockTextStates.getOrCreate(block.id, textContent.text)
        }

        LaunchedEffect(isFocused) {
            if (isFocused) {
                focusRequester.requestFocus()
            }
        }

        val textStyle = remember(block.type) {
            getTextStyleForType(block.type)
        }
        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
        Box {


            BackspaceAwareTextField(
                state = textFieldState,
                modifier = modifier.fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && !isFocused) {
                            callbacks.onFocus(block.id)
                        }
                    },
                textStyle = textStyle,
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
                                onLongPress = {
                                    callbacks.onLongClick(block.id)
                                },
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

    private fun getTextStyleForType(type: BlockType): TextStyle {
        return when (type) {
            is BlockType.Heading -> TextStyle(
                fontSize = when (type.level) {
                    1 -> 32.sp
                    2 -> 28.sp
                    3 -> 24.sp
                    4 -> 20.sp
                    5 -> 18.sp
                    else -> 16.sp
                }
            )

            is BlockType.Code -> TextStyle(
                fontSize = 14.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )

            else -> TextStyle(fontSize = 16.sp)
        }
    }
}
