package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Provides the public [CascadeEditorConfig] to custom renderer and chrome content.
 *
 * Built-in editor code should use the internal interaction policy instead of
 * reading this local directly.
 */
public val LocalCascadeEditorConfig: ProvidableCompositionLocal<CascadeEditorConfig> =
    compositionLocalOf { CascadeEditorConfig.Default }
