package io.github.linreal.cascade.editor.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme
import io.github.linreal.cascade.editor.ui.utils.calculateDropIndicatorY

/**
 * Default values for [DropIndicator] styling.
 */
internal object DropIndicatorDefaults {
    val StrokeWidth: Dp = 2.dp
}

internal data class DropIndicatorGeometry(
    val startX: Float,
    val endX: Float,
)

/**
 * Calculates horizontal indicator bounds for a resolved future indentation lane.
 *
 * The start edge follows normal block content geometry: base editor padding plus one
 * indent unit per future root level. The end edge stays pinned to the editor's base
 * trailing padding so the visible line shortens as depth increases.
 */
internal fun calculateDropIndicatorGeometry(
    viewportWidthPx: Float,
    futureRootIndentationLevel: Float,
    blockHorizontalPaddingPx: Float,
    indentUnitPx: Float,
): DropIndicatorGeometry? {
    if (viewportWidthPx <= 0f || blockHorizontalPaddingPx < 0f || indentUnitPx < 0f) return null

    val startX = blockHorizontalPaddingPx +
        indentUnitPx * futureRootIndentationLevel.coerceAtLeast(0f)
    val endX = viewportWidthPx - blockHorizontalPaddingPx
    if (startX >= endX) return null

    return DropIndicatorGeometry(startX = startX, endX = endX)
}

/**
 * Renders a horizontal line overlay indicating where a dragged block will be dropped.
 *
 * This composable is designed to be placed in a [Box] on top of a [LazyColumn].
 * It uses [Canvas] for rendering (single draw call, no layout overhead) and does not
 * consume touch events, so all gestures pass through to the list underneath.
 *
 * ## Coordinate System
 * The Y position is calculated from [LazyListState.layoutInfo] via [calculateDropIndicatorY].
 * Since the Canvas and LazyColumn share the same parent [Box], the Y values map directly.
 *
 * ## Performance
 * - [derivedStateOf] prevents recomposition when layoutInfo updates don't change the Y position
 * - [animateFloatAsState] smooths transitions between gap positions (150ms tween)
 * - Canvas draws a single line per frame
 *
 * @param targetIndex The visual gap position (0 to itemCount) from [DragState.targetIndex].
 *        Null means no valid drop target - the indicator is not rendered.
 * @param futureRootIndentationLevel Resolved future depth for the primary dragged root.
 * @param lazyListState The LazyListState of the block list, used to get item positions.
 * @param modifier Modifier for the Canvas.
 * @param color Color of the indicator line.
 * @param strokeWidth Thickness of the indicator line.
 */
@Composable
internal fun DropIndicator(
    targetIndex: Int?,
    futureRootIndentationLevel: Int,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = DropIndicatorDefaults.StrokeWidth
) {
    if (targetIndex == null) return
    val theme = LocalCascadeTheme.current
    val resolvedColor = if (color == Color.Unspecified) theme.colors.primary else color
    val dimensions = theme.dimensions

    // derivedStateOf ensures we only recompose when the computed Y actually changes,
    // filtering out layoutInfo updates that don't affect the indicator position.
    // Keyed on targetIndex because it's a plain parameter (not State) - without the key,
    // the closure would capture the initial targetIndex value permanently.
    val indicatorY by remember(targetIndex) {
        derivedStateOf {
            calculateDropIndicatorY(
                layoutInfo = lazyListState.layoutInfo,
                visualGap = targetIndex
            )
        }
    }

    val currentY = indicatorY ?: return

    // Animate Y position for smooth transitions between gap positions.
    // On first composition, starts at targetValue (no visible jump).
    // On subsequent targetIndex changes, produces a smooth 150ms transition.
    val animatedY by animateFloatAsState(
        targetValue = currentY,
        animationSpec = tween(
            durationMillis = IndentationAnimation.DurationMillis,
            easing = IndentationAnimation.Easing
        )
    )
    val animatedFutureDepth by animateFloatAsState(
        targetValue = futureRootIndentationLevel.toFloat(),
        animationSpec = tween(
            durationMillis = IndentationAnimation.DurationMillis,
            easing = IndentationAnimation.Easing
        ),
        label = "DropIndicatorFutureDepth",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val strokePx = strokeWidth.toPx()
        val geometry = calculateDropIndicatorGeometry(
            viewportWidthPx = size.width,
            futureRootIndentationLevel = animatedFutureDepth,
            blockHorizontalPaddingPx = dimensions.blockHorizontalPadding.toPx(),
            indentUnitPx = dimensions.indentUnit.toPx(),
        ) ?: return@Canvas

        drawLine(
            color = resolvedColor,
            start = Offset(geometry.startX, animatedY),
            end = Offset(geometry.endX, animatedY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )
    }
}
