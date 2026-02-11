package io.github.linreal.cascade.editor.ui

import androidx.compose.animation.core.FastOutSlowInEasing
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
import io.github.linreal.cascade.editor.ui.utils.calculateDropIndicatorY

/**
 * Default values for [DropIndicator] styling.
 */
internal object DropIndicatorDefaults {
    val Color: Color = Color(0xFF2196F3)
    val StrokeWidth: Dp = 2.dp
    val HorizontalPadding: Dp = 16.dp
    const val AnimationDurationMs: Int = 150
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
 * @param lazyListState The LazyListState of the block list, used to get item positions.
 * @param modifier Modifier for the Canvas.
 * @param color Color of the indicator line.
 * @param strokeWidth Thickness of the indicator line.
 * @param horizontalPadding Horizontal inset from the edges (matches block padding).
 */
@Composable
internal fun DropIndicator(
    targetIndex: Int?,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    color: Color = DropIndicatorDefaults.Color,
    strokeWidth: Dp = DropIndicatorDefaults.StrokeWidth,
    horizontalPadding: Dp = DropIndicatorDefaults.HorizontalPadding
) {
    if (targetIndex == null) return

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
            durationMillis = DropIndicatorDefaults.AnimationDurationMs,
            easing = FastOutSlowInEasing
        )
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val strokePx = strokeWidth.toPx()
        val paddingPx = horizontalPadding.toPx()

        drawLine(
            color = color,
            start = Offset(paddingPx, animatedY),
            end = Offset(size.width - paddingPx, animatedY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )
    }
}
