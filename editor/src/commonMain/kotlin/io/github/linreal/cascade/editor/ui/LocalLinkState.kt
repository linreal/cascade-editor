package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.linreal.cascade.editor.richtext.LinkState

/**
 * Provides reactive [LinkState] for custom editor chrome.
 *
 * Custom toolbars can read this CompositionLocal instead of requiring a new
 * [ToolbarSlot.Custom] lambda parameter.
 */
public val LocalLinkState: ProvidableCompositionLocal<State<LinkState>?> =
    staticCompositionLocalOf { null }
