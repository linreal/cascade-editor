package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockRenderer
import io.github.linreal.cascade.editor.theme.CascadeEditorTypography
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme
import io.github.linreal.cascade.editor.ui.utils.Spacers

/** Padding between the list prefix gutter and the text content. */
private val ListPrefixGap = 8.dp

/** Minimum width for the prefix gutter — accommodates at least 2-digit numbers like `12.` */
private val ListPrefixMinWidth = 24.dp

/** Width of the vertical left border for quote blocks. */
private val QuoteBorderWidth = 3.dp

/** Horizontal padding between the quote border and the text content. */
private val QuoteContentPaddingStart = 12.dp

/** Vertical padding inside the quote block. */
private val QuoteContentPaddingVertical = 4.dp

/** Corner radius for the code block tinted surface. */
private val CodeBlockCornerRadius = 6.dp

/** Horizontal padding between the code block tinted surface and its text. */
private val CodeBlockPaddingHorizontal = 12.dp

/** Vertical padding between the code block tinted surface and its text. */
private val CodeBlockPaddingVertical = 8.dp

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
        val theme = LocalCascadeTheme.current
        val targetStyle = remember(block.type.typeId, theme.typography) {
            resolveTextBlockStyle(block.type, theme.typography)
        }

        // Code uses a static monospace style — short-circuit before constructing the
        // animated font size so Paragraph -> Code conversion does not drive a 300ms
        // tween + recompositions for a value the Code branch ignores.
        if (block.type is BlockType.Code) {
            CodeBlock(
                block = block,
                isFocused = isFocused,
                textStyle = targetStyle.copy(color = theme.colors.text),
                modifier = modifier,
                callbacks = callbacks,
            )
            return
        }

        val animatedFontSize by animateFloatAsState(
            targetValue = targetStyle.fontSize.value,
            animationSpec = tween(durationMillis = 300),
        )
        val textStyle = targetStyle.copy(fontSize = animatedFontSize.sp, color = theme.colors.text)

        when (block.type) {
            is BlockType.BulletList, is BlockType.NumberedList -> {
                ListPrefixRow(
                    block = block,
                    isFocused = isFocused,
                    textStyle = textStyle,
                    modifier = modifier.withBlockIndentation(block),
                    callbacks = callbacks,
                )
            }
            is BlockType.Quote -> {
                QuoteBlock(
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
                    modifier = modifier.withBlockIndentation(block),
                    callbacks = callbacks,
                )
            }
        }
    }

}

/**
 * Resolves the static target typography shared by editor and preview renderers.
 *
 * The editor may animate toward this style during block conversion; preview rendering
 * deliberately consumes the target directly so card grids do not create animation state.
 */
internal fun resolveTextBlockStyle(
    type: BlockType,
    typography: CascadeEditorTypography,
): TextStyle {
    return when (type) {
        is BlockType.Heading -> when (type.level) {
            1 -> typography.heading1
            2 -> typography.heading2
            3 -> typography.heading3
            4 -> typography.heading4
            5 -> typography.heading5
            else -> typography.heading6
        }

        is BlockType.Quote -> typography.body.copy(fontStyle = FontStyle.Italic)

        is BlockType.Code -> typography.code

        else -> typography.body
    }
}

@Composable
private fun QuoteBlock(
    block: Block,
    isFocused: Boolean,
    textStyle: TextStyle,
    modifier: Modifier,
    callbacks: BlockCallbacks,
) {
    QuoteBlockVisual(
        modifier = modifier,
    ) {
        TextBlockField(
            block = block,
            isFocused = isFocused,
            textStyle = textStyle,
            modifier = Modifier.fillMaxWidth(),
            callbacks = callbacks,
        )
    }
}

/**
 * Quote chrome shared by the editable renderer and the static preview renderer.
 *
 * [content] owns only the text primitive; this wrapper contains no focus, input, pointer,
 * or animation behavior.
 */
@Composable
internal fun QuoteBlockVisual(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalCascadeTheme.current.colors
    val borderColor = colors.quoteBorder
    val backgroundColor = colors.quoteBackground

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(color = backgroundColor)
                drawRect(
                    color = borderColor,
                    size = Size(QuoteBorderWidth.toPx(), size.height),
                )
            }
            .padding(
                start = QuoteBorderWidth + QuoteContentPaddingStart,
                top = QuoteContentPaddingVertical,
                bottom = QuoteContentPaddingVertical,
            ),
    ) {
        content()
    }
}

/**
 * Slack-style code block surface: a tinted rounded rectangle hosting a
 * monospace [TextBlockField].
 *
 * Visual contract:
 *  - Fills parent width and paints [CascadeEditorColors.codeBlockBackground]
 *    clipped to a [CodeBlockCornerRadius] rounded rectangle.
 *  - Internal padding is [CodeBlockPaddingHorizontal] x [CodeBlockPaddingVertical].
 *  - Renders no chrome (no language label, no copy button, no indentation
 *    rail). Code blocks do not support indentation.
 *
 * Selection chrome is intentionally absent here: the renderer relies on the
 * wrapper-level selection overlay (`handlesSelectionVisual = false`).
 */
@Composable
private fun CodeBlock(
    block: Block,
    isFocused: Boolean,
    textStyle: TextStyle,
    modifier: Modifier,
    callbacks: BlockCallbacks,
) {
    CodeBlockVisual(
        modifier = modifier,
    ) {
        TextBlockField(
            block = block,
            isFocused = isFocused,
            textStyle = textStyle,
            modifier = Modifier.fillMaxWidth(),
            callbacks = callbacks,
        )
    }
}

/**
 * Code-block surface shared by editor and preview paths.
 *
 * The caller supplies either [TextBlockField] or a static text primitive. Keeping that
 * boundary explicit prevents preview mode from inheriting editor runtime state.
 */
@Composable
internal fun CodeBlockVisual(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val backgroundColor = LocalCascadeTheme.current.colors.codeBlockBackground

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = backgroundColor,
                    cornerRadius = CornerRadius(CodeBlockCornerRadius.toPx()),
                )
            }
            .padding(
                horizontal = CodeBlockPaddingHorizontal,
                vertical = CodeBlockPaddingVertical,
            ),
    ) {
        content()
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
    ListPrefixRowVisual(
        block = block,
        textStyle = textStyle,
        modifier = modifier,
    ) { contentModifier ->
        TextBlockField(
            block = block,
            isFocused = isFocused,
            textStyle = textStyle,
            modifier = contentModifier,
            callbacks = callbacks,
        )
    }
}

/**
 * List marker gutter shared by editable and static text content.
 *
 * Marker formatting and baseline geometry live here so numbered-list ancestry styles
 * cannot drift between editor and preview rendering.
 */
@Composable
internal fun ListPrefixRowVisual(
    block: Block,
    textStyle: TextStyle,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val orderedListPrefixStyles = LocalOrderedListPrefixStyles.current
    val prefixText = when (val type = block.type) {
        is BlockType.BulletList -> "\u2022"
        is BlockType.NumberedList -> formatOrderedListPrefix(
            number = type.number,
            style = orderedListPrefixStyles.styleFor(block.id),
        )
        else -> return
    }

    Row(
        modifier = modifier,
    ) {
        BasicText(
            text = prefixText,
            style = textStyle.copy(textAlign = TextAlign.End),
            modifier = Modifier
                .widthIn(min = ListPrefixMinWidth)
                .alignByBaseline(),
        )
        Spacers.Horizontal(ListPrefixGap)
        content(Modifier.weight(1f).alignByBaseline())
    }
}
