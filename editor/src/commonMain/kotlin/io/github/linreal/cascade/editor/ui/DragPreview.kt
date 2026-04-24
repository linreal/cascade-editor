package io.github.linreal.cascade.editor.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme

/**
 * Default values for [DragPreview] styling.
 */
internal object DragPreviewDefaults {
    /** Alpha for the semi-transparent floating preview. */
    const val Alpha: Float = 0.7f

    /** Shadow elevation (in dp equivalent) for the "lifted" appearance. */
    const val ShadowElevationDp: Float = 8f

    val BadgeOffsetStart = 4.dp
    val BadgePaddingHorizontal = 6.dp
    val BadgePaddingVertical = 2.dp
}

/**
 * Returns the compact badge label for additional blocks in the drag payload.
 */
internal fun dragPreviewPayloadBadgeText(payloadBlockCount: Int): String? {
    val additionalBlockCount = payloadBlockCount - 1
    return if (additionalBlockCount > 0) "+$additionalBlockCount" else null
}

/**
 * Renders a semi-transparent copy of a dragged block that follows the user's finger.
 *
 * The preview is rendered outside the [LazyColumn] (in a parent Box overlay) so it
 * can move freely across the entire editor area without being clipped by the list.
 *
 * ## Performance
 * - Uses [graphicsLayer] with `translationY` for positioning — only triggers
 *   re-draw (not re-layout or recomposition) when drag position changes.
 * - The block content does not recompose for pure pointer-frame Y movement.
 * - [dragOffsetY] is provided as a lambda so the State read happens inside
 *   graphicsLayer, keeping position updates in the draw phase only.
 *
 * ## Coordinate System
 * - [dragOffsetY] should return the current finger Y position relative to the
 *   parent Box (same coordinate space as the LazyColumn).
 * - [initialTouchOffsetY] is the Y offset within the block where the touch started.
 * - Preview top = `dragOffsetY() - initialTouchOffsetY`.
 *
 * @param block The block being dragged (used to render its content via the registry).
 * @param dragOffsetY Lambda returning current drag Y position relative to the parent Box.
 *        Read inside [graphicsLayer] for draw-phase-only updates.
 * @param initialTouchOffsetY Y offset from the top of the block where the touch was initiated.
 *        Ensures the preview doesn't "jump" to the finger position.
 * @param futureRootIndentationLevel Resolved future lane for the primary dragged root.
 *        Applied to the rendered preview block so it lines up with [DropIndicator].
 * @param payloadBlockCount Number of blocks in the semantic drag payload. A compact badge
 *        communicates additional moved descendants/roots without rendering all ghosts.
 * @param registry Block registry to look up the correct renderer for this block type.
 * @param callbacks Block callbacks passed to the renderer (non-interactive during drag).
 * @param modifier Optional modifier for the preview container.
 */
@Composable
internal fun DragPreview(
    block: Block,
    dragOffsetY: () -> Float,
    initialTouchOffsetY: Float,
    futureRootIndentationLevel: Int,
    payloadBlockCount: Int,
    registry: BlockRegistry,
    callbacks: BlockCallbacks,
    modifier: Modifier = Modifier
) {
    val renderer = registry.getRenderer(block.type.typeId) ?: return
    val theme = LocalCascadeTheme.current
    val previewBlock = block.withPreviewIndentationLevel(futureRootIndentationLevel)
    val badgeText = dragPreviewPayloadBadgeText(payloadBlockCount)
    val previewDepth = if (previewBlock.type.supportsIndentation) {
        previewBlock.attributes.indentationLevel
    } else {
        0
    }
    val badgeStartPadding = theme.dimensions.blockHorizontalPadding +
        theme.dimensions.indentUnit * previewDepth.toFloat() +
        DragPreviewDefaults.BadgeOffsetStart
    val animatedBadgeStartPadding by animateDpAsState(
        targetValue = badgeStartPadding,
        animationSpec = tween(
            durationMillis = IndentationAnimation.DurationMillis,
            easing = IndentationAnimation.Easing,
        ),
        label = "DragPreviewBadgeIndentation",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                // Position preview so the touch point stays under the finger.
                // translationY (not offset) avoids re-layout — draw phase only.
                translationY = dragOffsetY() - initialTouchOffsetY
                alpha = DragPreviewDefaults.Alpha
                shadowElevation = DragPreviewDefaults.ShadowElevationDp
            }
    ) {
        renderer.Render(
            block = previewBlock,
            isSelected = false,
            isFocused = false,
            // Match the same padding as regular blocks in CascadeEditor
            modifier = Modifier.padding(
                horizontal = theme.dimensions.blockHorizontalPadding,
                vertical = 4.dp,
            ),
            callbacks = callbacks
        )
        if (badgeText != null) {
            BasicText(
                text = badgeText,
                style = theme.typography.body.copy(
                    color = theme.colors.onPrimary,
                    fontSize = 12.sp,
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = animatedBadgeStartPadding)
                    .background(
                        color = theme.colors.primary,
                        shape = CircleShape,
                    )
                    .padding(
                        horizontal = DragPreviewDefaults.BadgePaddingHorizontal,
                        vertical = DragPreviewDefaults.BadgePaddingVertical,
                    ),
            )
        }
    }
}

/**
 * Rewrites only the preview copy so the ghost renders in the resolved future lane.
 */
private fun Block.withPreviewIndentationLevel(indentationLevel: Int): Block {
    if (!type.supportsIndentation) return this

    val clampedDepth = indentationLevel.coerceIn(
        BlockAttributes.MIN_INDENTATION_LEVEL,
        BlockAttributes.MAX_INDENTATION_LEVEL,
    )
    if (attributes.indentationLevel == clampedDepth) return this

    return withAttributes(attributes.copy(indentationLevel = clampedDepth))
}
