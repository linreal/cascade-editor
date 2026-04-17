package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties

@Composable
internal actual fun Modifier.nonFocusableTap(
    enabled: Boolean,
    onClick: () -> Unit,
): Modifier {
    if (!enabled) return this
    return this
        .clickable(onClick = onClick)
        .focusProperties { canFocus = false }
}
