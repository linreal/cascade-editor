package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockRenderer
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme
import io.github.linreal.cascade.editor.ui.EditorInteractionPolicy
import io.github.linreal.cascade.editor.ui.LocalEditorInteractionPolicy
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
        val interactionPolicy = LocalEditorInteractionPolicy.current
        val currentCallbacks = rememberUpdatedState(callbacks)
        val currentPolicy = rememberUpdatedState(interactionPolicy)
        val indentationLevel = block.attributes.indentationLevel
        val onCheckedChange = remember(block.id, callbacks, interactionPolicy) {
            createTodoCheckedChangeAction(
                blockId = block.id,
                callbacksProvider = { currentCallbacks.value },
                policyProvider = { currentPolicy.value },
            )
        }

        TodoBlockRowVisual(
            modifier = modifier.withBlockIndentation(block),
            indicator = {
                TodoCheckbox(
                    checked = todoType.checked,
                    primaryColor = theme.colors.primary,
                    onPrimaryColor = theme.colors.onPrimary,
                    borderColor = theme.colors.text.copy(alpha = 0.5f),
                    indentationLevel = indentationLevel,
                    enabled = interactionPolicy.canEditBlockControls,
                    onCheckedChange = onCheckedChange,
                )
            },
            content = { contentModifier ->
                TextBlockField(
                    block = block,
                    isFocused = isFocused,
                    textStyle = theme.typography.body.copy(
                        color = theme.colors.text,
                        textDecoration = if (todoType.checked) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        },
                    ),
                    modifier = contentModifier,
                    callbacks = callbacks,
                )
            },
        )
    }
}

private val CheckboxSize = 20.dp
private val CheckboxCorner = 5.dp
private val CheckboxStroke = 2.dp

private val IndentedCheckboxSize = 18.dp
private val RoundCheckboxCorner = 99.dp
private val RoundCheckboxStroke = 2.dp

internal data class TodoIndicatorGeometry(
    val size: Dp,
    val corner: Dp,
    val stroke: Dp,
)

/**
 * Resolves the todo indicator geometry shared by editor and preview paths.
 *
 * Odd nested lanes use the compact round marker; even nested lanes keep the compact
 * square marker. The root lane retains the original larger square checkbox.
 */
internal fun resolveTodoIndicatorGeometry(indentationLevel: Int): TodoIndicatorGeometry {
    val isRoot = indentationLevel == 0
    val useRoundCheckbox = indentationLevel % 2 != 0
    return TodoIndicatorGeometry(
        size = if (isRoot) CheckboxSize else IndentedCheckboxSize,
        corner = if (useRoundCheckbox) RoundCheckboxCorner else CheckboxCorner,
        stroke = if (useRoundCheckbox) RoundCheckboxStroke else CheckboxStroke,
    )
}

/**
 * Creates a todo checkbox handler that resolves policy and callbacks at invoke
 * time. Compose may keep an old input lambda alive briefly across recomposition,
 * so the handler reads provider values instead of trusting construction-time
 * captures.
 */
internal fun createTodoCheckedChangeAction(
    blockId: BlockId,
    callbacksProvider: () -> BlockCallbacks,
    policyProvider: () -> EditorInteractionPolicy,
): (Boolean) -> Unit {
    return {
        if (policyProvider().canEditBlockControls) {
            callbacksProvider().dispatch(ToggleTodo(blockId))
        }
    }
}

@Composable
private fun TodoCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    primaryColor: Color,
    onPrimaryColor: Color,
    borderColor: Color,
    indentationLevel: Int,
    enabled: Boolean,
) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "CheckboxProgress"
    )

    TodoCheckboxIndicator(
        progress = progress,
        primaryColor = primaryColor,
        onPrimaryColor = onPrimaryColor,
        borderColor = borderColor,
        indentationLevel = indentationLevel,
        inputModifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ),
    )
}

/**
 * Draws the todo indicator without imposing interaction or animation behavior.
 *
 * Editor rendering supplies an animated [progress] and a toggle modifier. Preview
 * rendering supplies a settled 0/1 progress and read-only checked semantics.
 */
@Composable
internal fun TodoCheckboxIndicator(
    progress: Float,
    primaryColor: Color,
    onPrimaryColor: Color,
    borderColor: Color,
    indentationLevel: Int,
    inputModifier: Modifier = Modifier,
) {
    val geometry = resolveTodoIndicatorGeometry(indentationLevel)
    val shape = RoundedCornerShape(geometry.corner)

    Box(
        modifier = Modifier
            .size(geometry.size)
            .clip(shape)
            .then(inputModifier)
            .drawWithCache {
                val strokeWidthPx = geometry.stroke.toPx()
                val cornerRadiusPx = geometry.corner.toPx()
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

/**
 * Todo row geometry shared by editable and static renderers.
 *
 * The supplied slots decide whether the indicator is interactive and whether the text
 * is an editor field or [BasicText][androidx.compose.foundation.text.BasicText].
 */
@Composable
internal fun TodoBlockRowVisual(
    modifier: Modifier,
    indicator: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
    ) {
        indicator()
        Spacers.Horizontal(12.dp)
        content(Modifier.weight(1f))
    }
}
