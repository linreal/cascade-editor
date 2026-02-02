package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.loge
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockRenderer
import io.github.linreal.cascade.editor.ui.BackspaceAwareTextField

/**
 * Renderer for text-supporting block types (paragraph, headings, lists, etc.).
 *
 * Uses [BackspaceAwareTextField] for text editing with backspace detection.
 * Handles focus management internally based on [isFocused] parameter.
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
        loge(
            tag = "DefaultBlockCallbacks",
            message = "comose: ${block.content}"
        )
        val textContent = block.content as? BlockContent.Text ?: return
        val focusRequester = remember { FocusRequester() }

        // Request focus when this block becomes focused
        LaunchedEffect(isFocused) {
            if (isFocused) {
                focusRequester.requestFocus()
            }
        }

        val textStyle = remember(block.type) {
            getTextStyleForType(block.type)
        }

        loge(
            tag = "DefaultBlockCallbacks",
            message = "comose2: ${textContent.text}"
        )

        BackspaceAwareTextField(
            modifier = modifier.fillMaxWidth(),
            initialText = textContent.text,
            textStyle = textStyle,
            focusRequester = focusRequester,
            onTextChange = { text, _ ->
                callbacks.onTextChange(block.id, text)
            },
            onBackspaceAtStart = {
                callbacks.onBackspaceAtStart(block.id)
            },
            onEnterPressed = { cursorPosition ->
                callbacks.onEnter(block.id, cursorPosition)
            }
        )
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
