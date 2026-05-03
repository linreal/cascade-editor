package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.linreal.cascade.editor.richtext.LinkChromeActions

/**
 * Provides [LinkChromeActions] to custom editor chrome.
 *
 * Returns `null` outside of [CascadeEditor]. Consumers that only need the
 * target-based subset can read this value as
 * `LinkActions?` since [LinkChromeActions] extends `LinkActions`.
 */
public val LocalLinkActions: ProvidableCompositionLocal<LinkChromeActions?> =
    staticCompositionLocalOf { null }
