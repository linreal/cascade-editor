package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.theme.LocalCascadeStrings
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme
import kotlin.math.roundToInt

/**
 * Foundation-only default link editing popup.
 *
 * The popup centers itself in the editor content viewport. A transparent
 * modal scrim fills the same viewport: it consumes the full gesture lifecycle
 * for any touch outside the popup body so blocks beneath cannot start
 * drag-to-reorder, long-press, or selection gestures while the popup is open,
 * and routes that gesture's release through [LinkPopupActions.dismiss], which
 * is equivalent to pressing Cancel.
 */
@Composable
internal fun LinkPopup(
    state: LinkPopupState,
    actions: LinkPopupActions,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCascadeTheme.current.colors
    val typography = LocalCascadeTheme.current.typography
    val strings = LocalCascadeStrings.current
    val density = LocalDensity.current
    val widthDp = LinkPopupDefaults.WIDTH_DP.dp
    val popupWidthPx = with(density) { widthDp.toPx() }
    val estimatedPopupHeightPx = with(density) { LinkPopupDefaults.ESTIMATED_HEIGHT_DP.dp.toPx() }

    var viewportHeight by remember { mutableStateOf(Float.MAX_VALUE) }
    var viewportWidth by remember { mutableStateOf(Float.MAX_VALUE) }

    val titleFocusRequester = remember { FocusRequester() }
    val urlFocusRequester = remember { FocusRequester() }

    // When the popup opens, request keyboard focus deterministically:
    // - Editing an existing link → focus title (URL is already valid; users
    //   most often rename existing links).
    // - New link / empty URL → focus URL so the user can type the value
    //   that's actually missing.
    // The FocusRequester targets are stable (same identities for the popup
    // lifetime), so launching once on first composition is sufficient.
    LaunchedEffect(Unit) {
        val target = if (state.existingUrl != null) titleFocusRequester else urlFocusRequester
        try {
            target.requestFocus()
        } catch (_: IllegalStateException) {
            // Focus request can fail if the popup is mounted then immediately
            // dismissed (e.g. document reset races popup open). Treat as best-effort.
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Modal scrim: claim the full gesture lifecycle for any touch that
            // lands outside the popup body so blocks beneath cannot start
            // drag-to-reorder, long-press, or selection gestures while the
            // popup is open. Touches that land on the popup body itself are
            // claimed by the body's own pointerInput first (Main pass is
            // child-first), so they don't reach this handler.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                    actions.dismiss()
                }
            }
            .onGloballyPositioned { coords ->
                viewportHeight = coords.size.height.toFloat()
                viewportWidth = coords.size.width.toFloat()
            },
    ) {
        val popupOffset = remember(
            estimatedPopupHeightPx,
            popupWidthPx,
            viewportHeight,
            viewportWidth,
        ) {
            LinkPopupDefaults.calculatePopupOffset(
                popupHeight = estimatedPopupHeightPx,
                popupWidth = popupWidthPx,
                viewportHeight = viewportHeight,
                viewportWidth = viewportWidth,
            )
        }
        val shape = RoundedCornerShape(8.dp)

        Column(
            modifier = Modifier
                .offset { IntOffset(popupOffset.x.roundToInt(), popupOffset.y.roundToInt()) }
                .width(widthDp)
                .heightIn(max = LinkPopupDefaults.MAX_HEIGHT_DP.dp)
                .shadow(8.dp, shape)
                .background(colors.popupBackground, shape)
                // Consume taps inside the popup so the scrim's outside-tap detector
                // doesn't dismiss the popup when the user is interacting with it.
                .pointerInput(Unit) {
                    detectTapGestures { /* swallow */ }
                }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LinkPopupTextField(
                label = strings.linkTitle,
                value = state.title,
                onValueChange = actions::updateTitle,
                textStyle = typography.body.copy(color = colors.text),
                labelStyle = typography.slashItemTitle.copy(color = colors.toolbarIconDisabled),
                cursorColor = colors.cursor,
                borderColor = colors.uiDivider,
                focusRequester = titleFocusRequester,
            )
            LinkPopupTextField(
                label = strings.linkUrl,
                value = state.url,
                onValueChange = actions::updateUrl,
                textStyle = typography.body.copy(color = colors.text),
                labelStyle = typography.slashItemTitle.copy(color = colors.toolbarIconDisabled),
                cursorColor = colors.cursor,
                borderColor = colors.uiDivider,
                focusRequester = urlFocusRequester,
            )
            if (state.validationError != null && state.url.isNotBlank()) {
                BasicText(
                    text = strings.linkValidationError(state.validationError),
                    style = typography.slashItemTitle.copy(color = colors.error),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.canRemove) {
                    LinkPopupButton(
                        label = strings.linkRemove,
                        enabled = true,
                        primary = false,
                        onClick = actions::remove,
                    )
                }
                LinkPopupButton(
                    label = strings.linkCancel,
                    enabled = true,
                    primary = false,
                    onClick = actions::dismiss,
                )
                LinkPopupButton(
                    label = strings.linkApply,
                    enabled = state.canApply,
                    primary = true,
                    onClick = actions::apply,
                )
            }
        }
    }
}

@Composable
private fun LinkPopupTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    labelStyle: TextStyle,
    cursorColor: Color,
    borderColor: Color,
    focusRequester: FocusRequester,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(text = label, style = labelStyle)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(cursorColor),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .focusRequester(focusRequester),
        )
    }
}

@Composable
private fun LinkPopupButton(
    label: String,
    enabled: Boolean,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalCascadeTheme.current.colors
    val typography = LocalCascadeTheme.current.typography
    val backgroundColor = when {
        primary && enabled -> colors.primary
        primary -> colors.primary.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    val contentColor = when {
        primary && enabled -> colors.onPrimary
        primary -> colors.onPrimary.copy(alpha = 0.65f)
        enabled -> colors.primary
        else -> colors.toolbarIconDisabled
    }

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 56.dp, minHeight = 36.dp)
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .nonFocusableTap(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = typography.slashBackButton.copy(color = contentColor),
        )
    }
}

/**
 * Pure placement helper for the default link popup.
 */
internal object LinkPopupDefaults {
    internal const val WIDTH_DP: Int = 320
    internal const val MAX_HEIGHT_DP: Int = 260
    internal const val ESTIMATED_HEIGHT_DP: Int = 220

    /**
     * Calculates the top-left popup offset to center the popup in the editor
     * content viewport. Both axes clamp to `[0, viewport - popup]` so the popup
     * remains fully visible even when the viewport is smaller than the popup.
     */
    internal fun calculatePopupOffset(
        popupHeight: Float,
        popupWidth: Float,
        viewportHeight: Float,
        viewportWidth: Float,
    ): Offset {
        val x = ((viewportWidth - popupWidth) / 2f)
            .coerceAtLeast(0f)
        val y = ((viewportHeight - popupHeight) / 2f)
            .coerceAtLeast(0f)
        return Offset(x, y)
    }
}
