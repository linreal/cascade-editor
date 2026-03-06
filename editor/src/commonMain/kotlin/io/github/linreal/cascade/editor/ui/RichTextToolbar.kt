package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.richtext.FormattingActions
import io.github.linreal.cascade.editor.richtext.FormattingState
import io.github.linreal.cascade.editor.richtext.StyleStatus

/**
 * Default config-driven rich text toolbar.
 *
 * Renders toggle buttons for each style in [config], reflecting the current
 * [formattingState] and dispatching toggle actions via [actions].
 *
 * Buttons use [Modifier.focusProperties] to prevent stealing focus from the
 * text field. The toolbar supports horizontal scrolling for overflow.
 */
@Composable
internal fun RichTextToolbar(
    formattingState: State<FormattingState>,
    actions: FormattingActions,
    config: RichTextToolbarConfig,
    modifier: Modifier = Modifier,
) {
    val state = formattingState.value

    Box(
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false },
    ) {
        HorizontalDivider(color = Color(0xFFE0E0E0))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            config.buttons.forEach { spec ->
                ToolbarToggleButton(
                    spec = spec,
                    status = state.styleStatusOf(spec.style),
                    enabled = state.canFormat,
                    onClick = { actions.toggleStyle(spec.style) },
                )
            }
        }
    }
}

@Composable
private fun ToolbarToggleButton(
    spec: ToolbarButtonSpec,
    status: StyleStatus,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val active = status == StyleStatus.FullyActive
    val partial = status == StyleStatus.Partial

    val backgroundColor = when {
        !enabled -> Color.Transparent
        active -> Color(0xFF1A73E8)
        partial -> Color(0xFF1A73E8).copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    val contentColor = when {
        !enabled -> Color(0xFF999999)
        active -> Color.White
        else -> Color(0xFF333333)
    }

    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .clip(shape)
            .background(backgroundColor)
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .focusProperties { canFocus = false }
            .semantics { contentDescription = spec.label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = buttonDisplayText(spec.style),
            style = buttonTextStyle(spec.style, contentColor),
        )
    }
}

/**
 * Short display label that visually represents the style.
 */
private fun buttonDisplayText(style: SpanStyle): String = when (style) {
    SpanStyle.Bold -> "B"
    SpanStyle.Italic -> "I"
    SpanStyle.Underline -> "U"
    SpanStyle.StrikeThrough -> "S"
    SpanStyle.InlineCode -> "<>"
    is SpanStyle.Highlight -> "H"
    is SpanStyle.Link -> "L"
    is SpanStyle.Custom -> style.typeId.take(2).uppercase()
}

/**
 * Text style that self-describes the formatting (bold B, italic I, etc.).
 */
private fun buttonTextStyle(style: SpanStyle, color: Color): TextStyle {
    val base = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = color,
    )
    return when (style) {
        SpanStyle.Bold -> base.copy(fontWeight = FontWeight.Bold)
        SpanStyle.Italic -> base.copy(fontStyle = FontStyle.Italic)
        SpanStyle.Underline -> base.copy(textDecoration = TextDecoration.Underline)
        SpanStyle.StrikeThrough -> base.copy(textDecoration = TextDecoration.LineThrough)
        SpanStyle.InlineCode -> base.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
        )
        else -> base
    }
}
