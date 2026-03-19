package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.slash.SlashCommandItem
import io.github.linreal.cascade.editor.slash.SlashCommandMenu
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme

/**
 * A single row in the slash command popup list.
 *
 * Shows the command title, optional description, and a chevron for submenu items.
 * Highlighted rows get a distinct background color.
 *
 * Uses [focusProperties] to prevent stealing focus from the text field.
 */
@Composable
internal fun SlashCommandRow(
    item: SlashCommandItem,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCascadeTheme.current.colors
    val typography = LocalCascadeTheme.current.typography
    val backgroundColor = if (isHighlighted) colors.slashSelectedItem else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SlashPopupDefaults.ROW_HEIGHT_DP.dp)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .focusProperties { canFocus = false }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = item.title,
                style = typography.slashItemTitle,
                color = colors.slashItemTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item is SlashCommandMenu) {
            Text(
                text = "\u203A", // single right-pointing angle quotation mark
                fontSize = 16.sp,
                color = colors.slashChevron,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
