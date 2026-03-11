package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockRenderer

/**
 * Renderer for text-supporting block types (paragraph, headings, lists, etc.).
 *
 * Delegates text editing to [TextBlockField] and applies type-specific text styling.
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
        val targetStyle = remember(block.type) {
            getTextStyleForType(block.type)
        }
        val animatedFontSize by animateFloatAsState(
            targetValue = targetStyle.fontSize.value,
            animationSpec = tween(durationMillis = 300),
        )
        val textStyle = targetStyle.copy(fontSize = animatedFontSize.sp)

        TextBlockField(
            block = block,
            isFocused = isFocused,
            textStyle = textStyle,
            modifier = modifier,
            callbacks = callbacks,
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
                fontFamily = FontFamily.Monospace
            )

            else -> TextStyle(fontSize = 16.sp)
        }
    }
}
