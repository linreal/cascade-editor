package io.github.linreal.cascade.editor.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal providing the current [CascadeEditorTheme].
 *
 * Set by `CascadeEditor` via `CompositionLocalProvider`. All internal
 * composables read colors and typography from this local instead of
 * using hardcoded values.
 */
internal val LocalCascadeTheme = staticCompositionLocalOf<CascadeEditorTheme> {
    CascadeEditorTheme.light()
}
