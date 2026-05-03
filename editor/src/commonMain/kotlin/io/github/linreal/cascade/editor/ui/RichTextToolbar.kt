package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cascadeeditor.editor.generated.resources.Res
import cascadeeditor.editor.generated.resources.ic_format_indent_decrease
import cascadeeditor.editor.generated.resources.ic_format_indent_increase
import cascadeeditor.editor.generated.resources.ic_link
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.indentation.IndentationActions
import io.github.linreal.cascade.editor.indentation.IndentationState
import io.github.linreal.cascade.editor.richtext.FormattingActions
import io.github.linreal.cascade.editor.richtext.FormattingState
import io.github.linreal.cascade.editor.richtext.LinkState
import io.github.linreal.cascade.editor.richtext.StyleStatus
import io.github.linreal.cascade.editor.theme.CascadeEditorColors
import io.github.linreal.cascade.editor.theme.CascadeEditorStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorTypography
import io.github.linreal.cascade.editor.theme.LocalCascadeStrings
import io.github.linreal.cascade.editor.theme.LocalCascadeTheme
import io.github.linreal.cascade.editor.ui.utils.Dividers
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Default config-driven rich text toolbar.
 *
 * Renders toggle buttons for each style in [config], plus structural indent
 * controls and the link editing entry point when enabled by [config].
 *
 * Buttons use [Modifier.focusProperties] to prevent stealing focus from the
 * text field. The toolbar supports horizontal scrolling for overflow.
 */
@Composable
internal fun RichTextToolbar(
    formattingState: State<FormattingState>,
    actions: FormattingActions,
    indentationState: State<IndentationState>,
    indentationActions: IndentationActions,
    linkState: State<LinkState>,
    config: RichTextToolbarConfig,
    onSlashInsert: () -> Unit,
    onLinkClick: () -> Unit,
    onHideKeyboard: (() -> Unit)? = null,
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
        Dividers.Horizontal(color = colors.uiDivider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SlashActionButton(
                    enabled = state.canFormat,
                    colors = colors,
                    typography = typography,
                    strings = strings,
                    onClick = onSlashInsert,
                )

                if (config.showIndentation) {
                    ToolbarIconButton(
                        icon = Res.drawable.ic_format_indent_decrease,
                        label = strings.indentBackward,
                        enabled = indentationState.value.canIndentBackward,
                        colors = colors,
                        onClick = indentationActions::indentBackward,
                    )
                    ToolbarIconButton(
                        icon = Res.drawable.ic_format_indent_increase,
                        label = strings.indentForward,
                        enabled = indentationState.value.canIndentForward,
                        colors = colors,
                        onClick = indentationActions::indentForward,
                    )
                }

                if (config.showLink) {
                    val linkPresentation = linkToolbarButtonPresentation(linkState.value)
                    ToolbarIconButton(
                        icon = Res.drawable.ic_link,
                        label = strings.link,
                        enabled = linkPresentation.enabled,
                        status = linkPresentation.status,
                        colors = colors,
                        onClick = onLinkClick,
                    )
                }

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
            if (onHideKeyboard != null) {
                HideKeyboardToolbarButton(onClick = onHideKeyboard)
            }
        }
    }
}

@Composable
private fun ToolbarIconButton(
    icon: DrawableResource,
    label: String,
    enabled: Boolean,
    status: StyleStatus = StyleStatus.Absent,
    colors: CascadeEditorColors,
    onClick: () -> Unit,
) {
    val backgroundColor = toolbarButtonBackgroundColor(
        enabled = enabled,
        status = status,
        colors = colors,
    )
    val contentColor = toolbarButtonContentColor(
        enabled = enabled,
        status = status,
        colors = colors,
    )
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .clip(shape)
            .background(backgroundColor)
            .nonFocusableTap(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(contentColor),
            modifier = Modifier.size(20.dp),
        )
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
            .nonFocusableTap(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = strings.slashCommand },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
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
    val backgroundColor = toolbarButtonBackgroundColor(enabled, status, colors)
    val contentColor = toolbarButtonContentColor(enabled, status, colors)

    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .clip(shape)
            .background(backgroundColor)
            .nonFocusableTap(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = localizedLabel(spec, strings) },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = buttonDisplayText(spec.style),
            style = buttonTextStyle(spec.style, contentColor, typography),
        )
    }
}

@Immutable
internal data class LinkToolbarButtonPresentation(
    val enabled: Boolean,
    val status: StyleStatus,
)

/**
 * Maps link editing state onto the default toolbar button's enabled and visual
 * status contract.
 */
internal fun linkToolbarButtonPresentation(linkState: LinkState): LinkToolbarButtonPresentation {
    if (!linkState.canLink) {
        return LinkToolbarButtonPresentation(
            enabled = false,
            status = StyleStatus.Absent,
        )
    }

    val status = when {
        linkState.existingUrl != null -> StyleStatus.FullyActive
        linkState.intersectsLink -> StyleStatus.Partial
        else -> StyleStatus.Absent
    }
    return LinkToolbarButtonPresentation(
        enabled = true,
        status = status,
    )
}

private fun toolbarButtonBackgroundColor(
    enabled: Boolean,
    status: StyleStatus,
    colors: CascadeEditorColors,
): Color {
    val active = status == StyleStatus.FullyActive
    val partial = status == StyleStatus.Partial
    return when {
        !enabled -> Color.Transparent
        active -> colors.primary
        partial -> colors.primary.copy(alpha = 0.15f)
        else -> Color.Transparent
    }
}

private fun toolbarButtonContentColor(
    enabled: Boolean,
    status: StyleStatus,
    colors: CascadeEditorColors,
): Color {
    val active = status == StyleStatus.FullyActive
    return when {
        !enabled -> colors.toolbarIconDisabled
        active -> colors.onPrimary
        else -> colors.toolbarIcon
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
    is SpanStyle.Link -> error("Links use RichTextToolbarConfig.showLink, not ToolbarButtonSpec.")
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
        is SpanStyle.Link -> error("Links use RichTextToolbarConfig.showLink, not ToolbarButtonSpec.")
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
