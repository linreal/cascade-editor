package io.github.linreal.cascade.editor.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal providing the current [CascadeEditorStrings].
 *
 * Set by `CascadeEditor` via `CompositionLocalProvider`. Internal
 * composables read UI chrome strings (toolbar labels, popup text)
 * from this local.
 */
internal val LocalCascadeStrings = staticCompositionLocalOf<CascadeEditorStrings> {
    CascadeEditorStrings.default()
}

/**
 * CompositionLocal providing the current [CascadeEditorBlockStrings].
 *
 * Set by `CascadeEditor` via `CompositionLocalProvider`. Used during
 * slash item generation to resolve localized block names, descriptions,
 * and keywords.
 */
internal val LocalCascadeBlockStrings = staticCompositionLocalOf<CascadeEditorBlockStrings> {
    CascadeEditorBlockStrings.default()
}
