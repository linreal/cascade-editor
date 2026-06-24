package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cascadeeditor.editor.generated.resources.Res
import cascadeeditor.editor.generated.resources.ic_hide_keyboard
import io.github.linreal.cascade.editor.theme.LocalCascadeStrings
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme
import org.jetbrains.compose.resources.painterResource

/**
 * Toolbar button that dismisses the software keyboard
 *
 * Shown by default only on iOS (via [RichTextToolbar]). Consumers using
 * [ToolbarSlot.Custom] can include this composable in their own toolbar layout.
 */
@Composable
public fun HideKeyboardToolbarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCascadeTheme.current.colors
    val strings = LocalCascadeStrings.current

    Box(
        modifier = modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .clip(CircleShape)
            .nonFocusableTap(onClick = onClick)
            .semantics { contentDescription = strings.hideKeyboard },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_hide_keyboard),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.toolbarIcon),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
