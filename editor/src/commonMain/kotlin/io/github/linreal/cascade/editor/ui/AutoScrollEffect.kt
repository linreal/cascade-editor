package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.ui.utils.calculateDropTargetIndex
import kotlinx.coroutines.delay

/**
 * Default hot zone size at the top and bottom edges of the viewport.
 * When the drag finger enters this zone, auto-scrolling begins.
 */
private val HOT_ZONE_DP = 80.dp

/**
 * Maximum scroll speed as a fraction of viewport height per second.
 * At 1.5, the list scrolls 1.5× its visible height per second when
 * the finger is at the very edge of (or beyond) the hot zone.
 *
 * Viewport-relative speed (adopted from the Reorderable library approach)
 * ensures consistent feel across phones and tablets.
 */
private const val MAX_SCROLL_SPEED_VIEWPORTS_PER_SECOND = 1.5f

/**
 * Target frame interval for the scroll loop. At 60 fps this is ~16 ms.
 */
private const val FRAME_DELAY_MS = 16L

/**
 * Calculates how many pixels to scroll per frame based on the drag Y position.
 *
 * Returns a negative value (scroll up) when the finger is in the top hot zone,
 * a positive value (scroll down) when in the bottom hot zone, and `0f` when
 * outside both hot zones.
 *
 * Speed scales linearly from 0 at the hot-zone boundary to [maxSpeedPxPerFrame]
 * at the viewport edge, and clamps at max speed when the finger goes beyond the edge.
 *
 * @param dragY Current drag Y position relative to the viewport top
 * @param viewportHeight Total height of the viewport in pixels
 * @param hotZonePx Size of each hot zone in pixels
 * @param maxSpeedPxPerFrame Maximum scroll speed in pixels per frame
 * @return Scroll amount in pixels (negative = up, positive = down, 0 = no scroll)
 */
internal fun calculateAutoScrollAmount(
    dragY: Float,
    viewportHeight: Float,
    hotZonePx: Float,
    maxSpeedPxPerFrame: Float
): Float {
    if (viewportHeight <= 0f || hotZonePx <= 0f) return 0f

    // Top hot zone: dragY in [0, hotZonePx) → scroll up
    if (dragY < hotZonePx) {
        // depth = 1.0 at edge (dragY=0 or negative), 0.0 at boundary (dragY=hotZonePx)
        val depth = ((hotZonePx - dragY) / hotZonePx).coerceIn(0f, 1f)
        return -maxSpeedPxPerFrame * depth
    }

    // Bottom hot zone: dragY in (viewportHeight - hotZonePx, viewportHeight] → scroll down
    val bottomBoundary = viewportHeight - hotZonePx
    if (dragY > bottomBoundary) {
        // depth = 0.0 at boundary, 1.0 at edge (dragY=viewportHeight or beyond)
        val depth = ((dragY - bottomBoundary) / hotZonePx).coerceIn(0f, 1f)
        return maxSpeedPxPerFrame * depth
    }

    return 0f
}

/**
 * Composable effect that auto-scrolls the [LazyListState] when a drag gesture
 * enters the hot zones near the top or bottom edges of the viewport.
 *
 * While [isDragging] is true, a coroutine loop runs at ~60 fps:
 * 1. Reads the current [dragOffsetY]
 * 2. Calculates scroll amount via [calculateAutoScrollAmount]
 * 3. Scrolls the list by that amount
 * 4. Recalculates the drop target (items have shifted) and dispatches [UpdateDragTarget]
 *
 * The loop cancels automatically when [isDragging] becomes false.
 *
 * @param lazyListState The scroll state of the LazyColumn
 * @param dragOffsetY Lambda returning the current drag Y position (viewport-relative)
 * @param isDragging Whether a drag operation is currently active
 * @param blockCount Total number of blocks in the list
 * @param onDropTargetChanged Callback to dispatch [UpdateDragTarget] with the new target index
 */
@Composable
internal fun AutoScrollDuringDrag(
    lazyListState: LazyListState,
    dragOffsetY: () -> Float,
    isDragging: Boolean,
    blockCount: Int,
    onDropTargetChanged: (Int?) -> Unit
) {
    val density = LocalDensity.current
    val hotZonePx = with(density) { HOT_ZONE_DP.toPx() }

    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect

        while (true) {
            delay(FRAME_DELAY_MS)

            val currentDragY = dragOffsetY()
            val viewportHeight = lazyListState.layoutInfo.viewportSize.height.toFloat()
            // Viewport-relative max speed: adapts to screen size automatically
            val maxSpeedPxPerFrame =
                viewportHeight * MAX_SCROLL_SPEED_VIEWPORTS_PER_SECOND * FRAME_DELAY_MS / 1000f

            val scrollAmount = calculateAutoScrollAmount(
                dragY = currentDragY,
                viewportHeight = viewportHeight,
                hotZonePx = hotZonePx,
                maxSpeedPxPerFrame = maxSpeedPxPerFrame
            )

            if (scrollAmount != 0f) {
                // Use dispatchRawDelta instead of scroll { scrollBy() }.
                // scroll {} acquires the ScrollableState's MutatorMutex, which
                // interferes with LazyColumn's internal gesture handling and
                // causes drag(pointerId) to return false → onDragCancel.
                // dispatchRawDelta bypasses the mutex entirely.
                // Sign is negated: dispatchRawDelta uses gesture convention
                // (positive = toward start/up), our scrollAmount uses content
                // convention (positive = forward/down).
                lazyListState.dispatchRawDelta(scrollAmount)

                // After scrolling, items have shifted — recalculate drop target
                val newTarget = calculateDropTargetIndex(
                    lazyListState.layoutInfo,
                    currentDragY,
                    blockCount
                )
                onDropTargetChanged(newTarget)
            }
        }
    }
}
