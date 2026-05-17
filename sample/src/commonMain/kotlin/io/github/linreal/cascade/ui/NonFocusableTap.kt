package io.github.linreal.cascade.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties

/**
 * Click helper for toolbar controls that should not steal text-field focus.
 *
 * Formatting buttons need to react to taps while leaving the editor selection in
 * place, so the sample uses this modifier for external and custom toolbar chrome.
 */
internal fun Modifier.nonFocusableTap(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    if (!enabled) return this
    return this
        .clickable(onClick = onClick)
        .focusProperties { canFocus = false }
}
