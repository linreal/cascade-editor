package io.github.linreal.cascade.editor.ui.renderers

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.theme.CascadeEditorDimensions
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme
import io.github.linreal.cascade.editor.ui.IndentationAnimation

/**
 * Adds the animated leading inset for blocks that participate in indentation semantics.
 *
 * The inset composes with the editor's base block padding instead of replacing it.
 *
 * Unsupported block types ignore stored indentation here so stale or malformed hidden
 * attributes cannot leak into rendering.
 */
@Composable
internal fun Modifier.withBlockIndentation(block: Block): Modifier {
    if (!block.type.supportsIndentation) return this

    val targetInset = blockIndentationInset(
        block = block,
        dimensions = LocalCascadeTheme.current.dimensions,
    )
    val animatedInset by animateDpAsState(
        targetValue = targetInset,
        animationSpec = tween(
            durationMillis = IndentationAnimation.DurationMillis,
            easing = IndentationAnimation.Easing,
        ),
        label = "BlockIndentation",
    )

    return this.padding(start = animatedInset)
}

/**
 * Pure target inset calculation behind [withBlockIndentation].
 *
 * Keeping this separate lets renderer tests verify the outline geometry contract
 * without depending on Compose animation clocks.
 */
internal fun blockIndentationInset(
    block: Block,
    dimensions: CascadeEditorDimensions,
): Dp {
    val depth = if (block.type.supportsIndentation) block.attributes.indentationLevel else 0
    return if (depth == 0) {
        0.dp
    } else {
        dimensions.indentUnit * depth.toFloat()
    }
}
