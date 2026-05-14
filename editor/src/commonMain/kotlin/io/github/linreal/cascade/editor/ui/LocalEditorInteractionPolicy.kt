package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Provides the internal interaction policy to built-in editor composables.
 */
internal val LocalEditorInteractionPolicy: ProvidableCompositionLocal<EditorInteractionPolicy> =
    compositionLocalOf { EditorInteractionPolicy.Editable }
