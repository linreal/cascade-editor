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
    val backgroundColor = if (isHighlighted) Color(0xFFE3F2FD) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SlashPopupDefaults.ROW_HEIGHT_DP.dp)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .focusProperties { canFocus = false }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = item.title,
                fontSize = 16.sp,
                color = Color(0xFF212121),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.description.isNotEmpty()) {
                Text(
                    text = item.description,
                    fontSize = 12.sp,
                    color = Color(0xFF757575),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item is SlashCommandMenu) {
            Text(
                text = "\u203A", // single right-pointing angle quotation mark
                fontSize = 18.sp,
                color = Color(0xFF9E9E9E),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
