package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import io.github.linreal.cascade.editor.state.BlockSpanStates

/**
 * Provides [BlockSpanStates] to text-capable block renderers.
 *
 * This CompositionLocal allows renderers to access the shared span state
 * manager without explicit parameter passing through the entire hierarchy.
 *
 * Usage in renderers:
 * ```
 * val blockSpanStates = LocalBlockSpanStates.current
 * val spanState = blockSpanStates.getOrCreate(block.id, textLength = text.length)
 * ```
 */
public val LocalBlockSpanStates: ProvidableCompositionLocal<BlockSpanStates> = compositionLocalOf<BlockSpanStates> {
    error("BlockSpanStates not provided. Ensure CascadeEditor is properly initialized.")
}
