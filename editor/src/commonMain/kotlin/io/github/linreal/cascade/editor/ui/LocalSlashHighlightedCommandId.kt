package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import io.github.linreal.cascade.editor.slash.SlashCommandId

/**
 * Provides the currently highlighted slash command id, if any.
 */
internal val LocalSlashHighlightedCommandId: ProvidableCompositionLocal<SlashCommandId?> =
    compositionLocalOf { null }
