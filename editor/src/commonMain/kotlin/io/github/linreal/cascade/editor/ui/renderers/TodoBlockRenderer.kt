package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.action.ToggleTodo
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockRenderer
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme
import io.github.linreal.cascade.editor.ui.utils.Spacers

/**
 * Renderer for Todo blocks — a checkbox alongside formattable text.
 *
 * Delegates text editing to [TextBlockField] and renders a custom checkbox
 * that dispatches [ToggleTodo] on state change.
 */
public class TodoBlockRenderer : BlockRenderer<BlockType.Todo> {

    @Composable
    override fun Render(
        block: Block,
        isSelected: Boolean,
        isFocused: Boolean,
        modifier: Modifier,
        callbacks: BlockCallbacks
    ) {
        val todoType = block.type as? BlockType.Todo ?: return
        val theme = LocalCascadeTheme.current
        val indentationLevel = block.attributes.indentationLevel
        val useRoundCheckbox = indentationLevel % 2 != 0

        Row(
            modifier = modifier.withBlockIndentation(block),
            verticalAlignment = Alignment.Top,
        ) {
            TodoCheckbox(
                checked = todoType.checked,
                primaryColor = theme.colors.primary,
                onPrimaryColor = theme.colors.onPrimary,
                borderColor = theme.colors.text.copy(alpha = 0.5f),
                checkboxSize = if (indentationLevel == 0) CheckboxSize else IndentedCheckboxSize,
                corner = if (useRoundCheckbox) RoundCheckboxCorner else CheckboxCorner,
                stroke = if (useRoundCheckbox) RoundCheckboxStroke else CheckboxStroke,
                onCheckedChange = { callbacks.dispatch(ToggleTodo(block.id)) },
            )
            Spacers.Horizontal(12.dp)
            TextBlockField(
                block = block,
                isFocused = isFocused,
                textStyle = theme.typography.body.copy(
                    color = theme.colors.text,
                    textDecoration = if (todoType.checked) TextDecoration.LineThrough else TextDecoration.None
                ),
                modifier = Modifier.weight(1f),
                callbacks = callbacks,
            )
        }
    }
}

private val CheckboxSize = 20.dp
private val CheckboxCorner = 5.dp
private val CheckboxStroke = 2.dp

private val IndentedCheckboxSize = 18.dp
private val RoundCheckboxCorner = 99.dp
private val RoundCheckboxStroke = 2.dp

@Composable
private fun TodoCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    primaryColor: Color,
    onPrimaryColor: Color,
    borderColor: Color,
    checkboxSize: Dp,
    corner: Dp,
    stroke: Dp,
) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "CheckboxProgress"
    )

    Box(
        modifier = modifier
            .size(checkboxSize)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .drawWithCache {
                val strokeWidthPx = stroke.toPx()
                val cornerRadiusPx = corner.toPx()
                val canvasSize = size.minDimension
                val inset = strokeWidthPx / 2f

                val fullPath = Path().apply {
                    moveTo(canvasSize * 0.22f, canvasSize * 0.50f)
                    lineTo(canvasSize * 0.40f, canvasSize * 0.68f)
                    lineTo(canvasSize * 0.78f, canvasSize * 0.30f)
                }

                val pathMeasure = PathMeasure().apply {
                    setPath(fullPath, false)
                }

                onDrawBehind {
                    val animatedBorder = lerp(borderColor, primaryColor, progress)

                    if (progress > 0f) {
                        drawRoundRect(
                            color = primaryColor,
                            cornerRadius = CornerRadius(cornerRadiusPx),
                            alpha = progress
                        )
                    }

                    drawRoundRect(
                        color = animatedBorder,
                        topLeft = Offset(inset, inset),
                        size = Size(
                            width = canvasSize - strokeWidthPx,
                            height = canvasSize - strokeWidthPx
                        ),
                        cornerRadius = CornerRadius(cornerRadiusPx),
                        style = Stroke(width = strokeWidthPx)
                    )

                    if (progress > 0f) {
                        val segmentPath = Path()
                        pathMeasure.getSegment(
                            startDistance = 0f,
                            stopDistance = pathMeasure.length * progress,
                            destination = segmentPath,
                            startWithMoveTo = true
                        )

                        drawPath(
                            path = segmentPath,
                            color = onPrimaryColor,
                            style = Stroke(
                                width = strokeWidthPx,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {}
}
