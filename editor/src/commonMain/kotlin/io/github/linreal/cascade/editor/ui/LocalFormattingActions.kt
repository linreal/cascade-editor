package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import io.github.linreal.cascade.editor.richtext.FormattingActions

/**
 * Provides [FormattingActions] to composables for rich text formatting operations
 * (toolbar buttons, keyboard shortcuts).
 *
 * Returns `null` if no actions have been provided (e.g., outside of [CascadeEditor]).
 */
public val LocalFormattingActions: ProvidableCompositionLocal<FormattingActions?> =
    compositionLocalOf { null }
