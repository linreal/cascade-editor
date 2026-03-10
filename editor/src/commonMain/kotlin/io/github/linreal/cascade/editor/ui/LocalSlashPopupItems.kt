package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import io.github.linreal.cascade.editor.slash.SlashCommandItem

/**
 * Provides the current flat list of slash popup items for keyboard navigation.
 *
 * Computed in [CascadeEditor] from the slash registry search results.
 * Read by [TextBlockField] for Up/Down arrow key navigation.
 */
internal val LocalSlashPopupItems: ProvidableCompositionLocal<List<SlashCommandItem>> =
    compositionLocalOf { emptyList() }
