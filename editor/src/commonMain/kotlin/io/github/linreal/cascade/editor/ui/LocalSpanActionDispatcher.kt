package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import io.github.linreal.cascade.editor.richtext.SpanActionDispatcher

/**
 * Provides [SpanActionDispatcher] to composables for rich text formatting operations.
 *
 * Returns `null` if no dispatcher has been provided (e.g., outside of [CascadeEditor]).
 */
public val LocalSpanActionDispatcher: ProvidableCompositionLocal<SpanActionDispatcher?> =
    compositionLocalOf { null }
