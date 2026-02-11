package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockRegistry

/**
 * Default values for [DragPreview] styling.
 */
internal object DragPreviewDefaults {
    /** Alpha for the semi-transparent floating preview. */
    const val Alpha: Float = 0.7f

    /** Shadow elevation (in dp equivalent) for the "lifted" appearance. */
    const val ShadowElevationDp: Float = 8f
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
 * - The block content is composed **once** and does not recompose during drag movement.
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
 * @param registry Block registry to look up the correct renderer for this block type.
 * @param callbacks Block callbacks passed to the renderer (non-interactive during drag).
 * @param modifier Optional modifier for the preview container.
 */
@Composable
internal fun DragPreview(
    block: Block,
    dragOffsetY: () -> Float,
    initialTouchOffsetY: Float,
    registry: BlockRegistry,
    callbacks: BlockCallbacks,
    modifier: Modifier = Modifier
) {
    val renderer = registry.getRenderer(block.type.typeId) ?: return

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
            block = block,
            isSelected = false,
            isFocused = false,
            // Match the same padding as regular blocks in CascadeEditor
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            callbacks = callbacks
        )
    }
}
