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
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.richtext.FormattingActions
import io.github.linreal.cascade.editor.richtext.FormattingState
import io.github.linreal.cascade.editor.richtext.StyleStatus
import io.github.linreal.cascade.editor.theme.CascadeEditorColors
import io.github.linreal.cascade.editor.theme.CascadeEditorStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorTypography
import io.github.linreal.cascade.editor.theme.LocalCascadeStrings
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme

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
    onSlashInsert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = formattingState.value
    val colors = LocalCascadeTheme.current.colors
    val typography = LocalCascadeTheme.current.typography
    val strings = LocalCascadeStrings.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false },
    ) {
        HorizontalDivider(color = colors.uiDivider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SlashActionButton(
                enabled = state.canFormat,
                colors = colors,
                typography = typography,
                strings = strings,
                onClick = onSlashInsert,
            )
            config.buttons.forEach { spec ->
                ToolbarToggleButton(
                    spec = spec,
                    status = state.styleStatusOf(spec.style),
                    enabled = state.canFormat,
                    colors = colors,
                    typography = typography,
                    strings = strings,
                    onClick = { actions.toggleStyle(spec.style) },
                )
            }
        }
    }
}

@Composable
private fun SlashActionButton(
    enabled: Boolean,
    colors: CascadeEditorColors,
    typography: CascadeEditorTypography,
    strings: CascadeEditorStrings,
    onClick: () -> Unit,
) {
    val contentColor = if (enabled) colors.toolbarIcon else colors.toolbarIconDisabled
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .clip(shape)
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .focusProperties { canFocus = false }
            .semantics { contentDescription = strings.slashCommand },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "/",
            style = typography.toolbarButton.copy(color = contentColor),
        )
    }
}

@Composable
private fun ToolbarToggleButton(
    spec: ToolbarButtonSpec,
    status: StyleStatus,
    enabled: Boolean,
    colors: CascadeEditorColors,
    typography: CascadeEditorTypography,
    strings: CascadeEditorStrings,
    onClick: () -> Unit,
) {
    val active = status == StyleStatus.FullyActive
    val partial = status == StyleStatus.Partial

    val backgroundColor = when {
        !enabled -> Color.Transparent
        active -> colors.primary
        partial -> colors.primary.copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    val contentColor = when {
        !enabled -> colors.toolbarIconDisabled
        active -> colors.onPrimary
        else -> colors.toolbarIcon
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
            .semantics { contentDescription = localizedLabel(spec, strings) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = buttonDisplayText(spec.style),
            style = buttonTextStyle(spec.style, contentColor, typography),
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
 * Resolves the localized accessibility label for a toolbar button.
 * Falls back to [ToolbarButtonSpec.label] for custom span styles.
 */
private fun localizedLabel(spec: ToolbarButtonSpec, strings: CascadeEditorStrings): String =
    when (spec.style) {
        SpanStyle.Bold -> strings.bold
        SpanStyle.Italic -> strings.italic
        SpanStyle.Underline -> strings.underline
        SpanStyle.StrikeThrough -> strings.strikethrough
        SpanStyle.InlineCode -> strings.inlineCode
        is SpanStyle.Highlight -> strings.highlight
        else -> spec.label
    }

/**
 * Text style that self-describes the formatting (bold B, italic I, etc.).
 */
private fun buttonTextStyle(style: SpanStyle, color: Color, typography: CascadeEditorTypography): TextStyle {
    val base = typography.toolbarButton.copy(color = color)
    return when (style) {
        SpanStyle.Bold -> base.copy(fontWeight = FontWeight.Bold)
        SpanStyle.Underline -> base.copy(textDecoration = TextDecoration.Underline)
        SpanStyle.StrikeThrough -> base.copy(textDecoration = TextDecoration.LineThrough)
        SpanStyle.InlineCode -> base.copy(
            fontFamily = FontFamily.Monospace,
        )
        else -> base
    }
}
