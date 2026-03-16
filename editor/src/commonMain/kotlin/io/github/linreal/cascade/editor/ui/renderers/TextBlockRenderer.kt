package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockRenderer
import io.github.linreal.cascade.editor.ui.utils.Spacers

/** Padding between the list prefix gutter and the text field. */
private val ListPrefixGap = 8.dp

/** Minimum width for the prefix gutter — accommodates at least 2-digit numbers like `12.` */
private val ListPrefixMinWidth = 24.dp

/**
 * Renderer for text-supporting block types (paragraph, headings, lists, etc.).
 *
 * Delegates text editing to [TextBlockField] and applies type-specific text styling.
 * List block types ([BlockType.BulletList], [BlockType.NumberedList]) get a non-editable
 * prefix gutter to the left of the text field.
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
        val targetStyle = remember(block.type.typeId) {
            getTextStyleForType(block.type)
        }
        val animatedFontSize by animateFloatAsState(
            targetValue = targetStyle.fontSize.value,
            animationSpec = tween(durationMillis = 300),
        )
        val textStyle = targetStyle.copy(fontSize = animatedFontSize.sp)

        when (block.type) {
            is BlockType.BulletList, is BlockType.NumberedList -> {
                ListPrefixRow(
                    block = block,
                    isFocused = isFocused,
                    textStyle = textStyle,
                    modifier = modifier,
                    callbacks = callbacks,
                )
            }
            else -> {
                TextBlockField(
                    block = block,
                    isFocused = isFocused,
                    textStyle = textStyle,
                    modifier = modifier,
                    callbacks = callbacks,
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
                fontFamily = FontFamily.Monospace
            )

            else -> TextStyle(fontSize = 16.sp)
        }
    }
}

@Composable
private fun ListPrefixRow(
    block: Block,
    isFocused: Boolean,
    textStyle: TextStyle,
    modifier: Modifier,
    callbacks: BlockCallbacks,
) {
    val prefixText = when (val type = block.type) {
        is BlockType.BulletList -> "\u2022"
        is BlockType.NumberedList -> "${type.number}."
        else -> return // should not happen
    }

    Row(
        modifier = modifier,
    ) {
        Text(
            text = prefixText,
            style = textStyle.copy(
                color = textStyle.color.takeUnless { it == androidx.compose.ui.graphics.Color.Unspecified }
                    ?: LocalContentColor.current,
                textAlign = TextAlign.End,
            ),
            modifier = Modifier
                .widthIn(min = ListPrefixMinWidth)
                .alignByBaseline(),
        )
        Spacers.Horizontal(ListPrefixGap)
        TextBlockField(
            block = block,
            isFocused = isFocused,
            textStyle = textStyle,
            modifier = Modifier.weight(1f).alignByBaseline(),
            callbacks = callbacks,
        )
    }
}
