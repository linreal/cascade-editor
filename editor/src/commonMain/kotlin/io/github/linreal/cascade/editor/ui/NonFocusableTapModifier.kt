package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Tap handler that never steals focus from the currently focused text field.
 *
 * On mobile (Android / iOS) this delegates to [Modifier.clickable][androidx.compose.foundation.clickable]
 * which provides native indication (ripple) and does not cause focus theft.
 *
 * On desktop this uses raw [pointerInput][androidx.compose.ui.input.pointer.pointerInput]
 * to avoid the focus-steal that [clickable][androidx.compose.foundation.clickable] triggers
 * when a mouse click moves platform focus away from the text field.
 */
@Composable
internal expect fun Modifier.nonFocusableTap(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier
