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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
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

private val ToolbarPillShape = RoundedCornerShape(percent = 50)
private val ToolbarDividerPadding = Modifier.padding(horizontal = 4.dp)

/**
 * Default config-driven rich text toolbar
 *
 * Layout has three zones: a fixed accent `/` button on the left, a horizontally
 * scrollable middle (formatting buttons, then structural indent/link controls),
 * and a fixed iOS-only hide-keyboard button on the right. Active formatting is
 * indicated by tinting the glyph with [CascadeEditorColors.primary]; there is no
 * per-button background fill.
 *
 * Buttons use [Modifier.focusProperties] to prevent stealing focus from the text
 * field.
 *
 * @param slashEnabled Whether the slash trigger button can write to the focused
 *        text field.
 */
@Composable
internal fun RichTextToolbar(
    formattingState: State<FormattingState>,
    actions: FormattingActions,
    indentationState: State<IndentationState>,
    indentationActions: IndentationActions,
    linkState: State<LinkState>,
    config: RichTextToolbarConfig,
    slashEnabled: Boolean,
    onSlashInsert: () -> Unit,
    onLinkClick: () -> Unit,
    onHideKeyboard: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state = formattingState.value
    val colors = LocalCascadeTheme.current.colors
    val typography = LocalCascadeTheme.current.typography
    val strings = LocalCascadeStrings.current

    val hasFormatting = config.buttons.isNotEmpty()
    val hasStructural = config.showIndentation || config.showLink

    Box(
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .padding(horizontal = 12.dp)
            .padding(top = 4.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, ToolbarPillShape)
                .clip(ToolbarPillShape)
                .background(colors.toolbarBackground)
                .padding(start = 10.dp, end = 8.dp)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SlashActionButton(
                enabled = slashEnabled,
                colors = colors,
                typography = typography,
                strings = strings,
                onClick = onSlashInsert,
            )

            if (hasFormatting || hasStructural) {
                Dividers.Vertical(color = colors.uiDivider, modifier = ToolbarDividerPadding)
            }

            val scrollState = rememberScrollState()
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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

                    if (hasFormatting && hasStructural) {
                        Dividers.Vertical(color = colors.uiDivider, modifier = ToolbarDividerPadding)
                    }

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
                }

                val showFade by remember(scrollState) {
                    derivedStateOf { scrollState.canScrollForward }
                }
                if (showFade) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    0.85f to Color.Transparent,
                                    1f to colors.toolbarBackground,
                                ),
                            ),
                    )
                }
            }

            if (onHideKeyboard != null) {
                Dividers.Vertical(color = colors.uiDivider, modifier = ToolbarDividerPadding)
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
    val contentColor = toolbarButtonContentColor(
        enabled = enabled,
        status = status,
        colors = colors,
    )

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
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
    val backgroundColor = if (enabled) colors.primary else colors.toolbarIcon.copy(alpha = 0.10f)
    val contentColor = if (enabled) colors.onPrimary else colors.toolbarIconDisabled

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .clip(CircleShape)
            .nonFocusableTap(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = strings.slashCommand },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "/",
                style = typography.toolbarButton.copy(
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
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
    val contentColor = toolbarButtonContentColor(enabled, status, colors)

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
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

/**
 * Glyph/icon tint for a toolbar button. Active styles are shown by accent color
 * only — there is no background fill.
 */
private fun toolbarButtonContentColor(
    enabled: Boolean,
    status: StyleStatus,
    colors: CascadeEditorColors,
): Color = when {
    !enabled -> colors.toolbarIconDisabled
    status == StyleStatus.FullyActive -> colors.primary
    status == StyleStatus.Partial -> colors.primary.copy(alpha = 0.6f)
    else -> colors.toolbarIcon
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
