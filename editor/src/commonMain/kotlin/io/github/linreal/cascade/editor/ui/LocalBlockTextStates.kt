package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import io.github.linreal.cascade.editor.state.BlockTextStates

/**
 * Provides [BlockTextStates] to text-capable block renderers.
 *
 * This CompositionLocal allows renderers to access the shared text state
 * manager without explicit parameter passing through the entire hierarchy.
 *
 * Usage in renderers:
 * ```
 * val blockTextStates = LocalBlockTextStates.current
 * val textFieldState = blockTextStates.getOrCreate(block.id, initialText)
 * ```
 */
public val LocalBlockTextStates: ProvidableCompositionLocal<BlockTextStates> = compositionLocalOf<BlockTextStates> {
    error("BlockTextStates not provided. Ensure CascadeEditor is properly initialized.")
}
