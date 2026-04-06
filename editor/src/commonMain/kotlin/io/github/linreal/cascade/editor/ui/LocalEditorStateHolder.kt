package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import io.github.linreal.cascade.editor.state.EditorStateHolder

/**
 * Provides the live [EditorStateHolder] to internal editor infrastructure that
 * needs history-aware coordination but is not wired by explicit parameters.
 */
internal val LocalEditorStateHolder: ProvidableCompositionLocal<EditorStateHolder> =
    compositionLocalOf {
        error("EditorStateHolder not provided. Ensure CascadeEditor is properly initialized.")
    }
