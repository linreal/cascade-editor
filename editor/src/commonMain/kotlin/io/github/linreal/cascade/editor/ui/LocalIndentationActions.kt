package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import io.github.linreal.cascade.editor.indentation.IndentationActions

/**
 * Provides [IndentationActions] to custom editor chrome.
 *
 * Returns `null` outside of [CascadeEditor].
 */
public val LocalIndentationActions: ProvidableCompositionLocal<IndentationActions?> =
    compositionLocalOf { null }
