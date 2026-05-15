package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Single high-level gate signaling whether the slash command palette is enabled.
 *
 * Provided by [CascadeEditor] from [SlashCommandSlot] and the current interaction
 * policy, then read by [io.github.linreal.cascade.editor.ui.renderers.TextBlockField]
 * to decide whether to construct a [io.github.linreal.cascade.editor.slash.SlashCommandTextObserver]
 * for the block. When `false`, no observer is created so `/` is a literal character
 * and no `OpenSlashCommand` is ever dispatched.
 */
internal val LocalSlashCommandsEnabled: ProvidableCompositionLocal<Boolean> =
    compositionLocalOf { true }
