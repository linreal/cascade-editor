package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.action.HighlightSlashCommand
import io.github.linreal.cascade.editor.action.NavigateSlashBack
import io.github.linreal.cascade.editor.slash.SlashCommandExecutor
import io.github.linreal.cascade.editor.slash.SlashCommandItem
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.SlashCommandState
import kotlin.math.roundToInt

/**
 * Slash command popup overlay.
 *
 * Rendered inside the main editor [Box] as a sibling overlay (same pattern as
 * [DropIndicator] and [DragPreview]). Positioned relative to the caret rect
 * provided via [LocalSlashCaretRect].
 *
 * Uses [focusProperties] to prevent the popup from stealing text field focus.
 */
@Composable
internal fun SlashCommandPopup(
    slashState: SlashCommandState,
    stateHolder: EditorStateHolder,
    slashExecutor: SlashCommandExecutor,
) {
    val caretHolder = LocalSlashCaretRect.current
    val caretRect = caretHolder.rect ?: return

    // Clear caret rect when popup leaves the tree.
    DisposableEffect(Unit) {
        onDispose { caretHolder.clearAll() }
    }

    val items = LocalSlashPopupItems.current

    // Auto-highlight first item when results change.
    LaunchedEffect(items) {
        stateHolder.dispatch(HighlightSlashCommand(items.firstOrNull()?.id))
    }

    val density = LocalDensity.current
    val estimatedPopupHeightDp = remember(items.size, slashState.navigationPath) {
        SlashPopupDefaults.estimatePopupHeightDp(
            itemCount = items.size,
            hasBackHeader = slashState.navigationPath.isNotEmpty(),
        )
    }
    val estimatedPopupHeightPx = with(density) { estimatedPopupHeightDp.dp.toPx() }
    val widthDp = SlashPopupDefaults.WIDTH_DP.dp
    val gapPx = with(density) { SlashPopupDefaults.CARET_GAP_DP.dp.toPx() }

    // Track the parent Box position in window coordinates for offset calculation.
    var parentWindowOffset by remember { mutableStateOf(Offset.Zero) }
    // Viewport dimensions from the editor content Box (the actual available space).
    var viewportHeight by remember { mutableStateOf(Float.MAX_VALUE) }
    var viewportWidth by remember { mutableStateOf(Float.MAX_VALUE) }

    Box(
        modifier = Modifier
            .onGloballyPositioned { coords ->
                parentWindowOffset = coords.positionInWindow()
                // The popup's parent Box shares the editor content Box size.
                coords.parentLayoutCoordinates?.let { parent ->
                    viewportHeight = parent.size.height.toFloat()
                    viewportWidth = parent.size.width.toFloat()
                }
            }
            .focusProperties { canFocus = false },
    ) {
        // Convert caret rect from window to local coordinates.
        val localCaretRect = Rect(
            left = caretRect.left - parentWindowOffset.x,
            top = caretRect.top - parentWindowOffset.y,
            right = caretRect.right - parentWindowOffset.x,
            bottom = caretRect.bottom - parentWindowOffset.y,
        )

        val popupWidthPx = with(density) { widthDp.toPx() }

        val popupOffset = remember(localCaretRect, estimatedPopupHeightPx, popupWidthPx, viewportHeight, viewportWidth) {
            SlashPopupDefaults.calculatePopupOffset(
                caretRect = localCaretRect,
                popupHeight = estimatedPopupHeightPx,
                popupWidth = popupWidthPx,
                viewportHeight = viewportHeight,
                viewportWidth = viewportWidth,
                gap = gapPx,
            )
        }

        Column(
            modifier = Modifier
                .offset { IntOffset(popupOffset.x.roundToInt(), popupOffset.y.roundToInt()) }
                .width(widthDp)
                .heightIn(max = SlashPopupDefaults.MAX_HEIGHT_DP.dp)
                .shadow(8.dp, RoundedCornerShape(8.dp))
                .background(Color.White, RoundedCornerShape(8.dp))
                .focusProperties { canFocus = false },
        ) {
            // Back header for submenu navigation
            if (slashState.navigationPath.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SlashPopupDefaults.BACK_HEADER_HEIGHT_DP.dp)
                        .clickable { stateHolder.dispatch(NavigateSlashBack) }
                        .focusProperties { canFocus = false }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "\u2039 Back",
                        fontSize = 14.sp,
                        color = Color(0xFF1A73E8),
                    )
                }
                HorizontalDivider(color = Color(0xFFE0E0E0))
            }

            // Scrollable item list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(vertical = SlashPopupDefaults.CONTENT_PADDING_DP.dp)
                    .focusProperties { canFocus = false },
            ) {
                items(
                    items = items,
                    key = { item -> item.id.value },
                ) { item ->
                    SlashCommandRow(
                        item = item,
                        isHighlighted = item.id == slashState.highlightedCommandId,
                        onClick = { onItemClicked(item, slashExecutor) },
                    )
                }
            }
        }
    }
}

private fun onItemClicked(
    item: SlashCommandItem,
    executor: SlashCommandExecutor,
) {
    executor.execute(item)
}
