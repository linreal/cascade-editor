package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.Immutable
import io.github.linreal.cascade.editor.core.SpanStyle

/**
 * Specification for a single toolbar button.
 *
 * @param style The [SpanStyle] this button toggles.
 * @param label Accessibility label for the button.
 */
@Immutable
public data class ToolbarButtonSpec(
    val style: SpanStyle,
    val label: String,
)

/**
 * Configuration for the default rich text toolbar.
 * Controls which buttons appear and in what order.
 *
 * @param buttons Text formatting buttons to render in order.
 * @param showIndentation Whether the default toolbar renders indent/outdent buttons.
 */
@Immutable
public data class RichTextToolbarConfig(
    val buttons: List<ToolbarButtonSpec>,
    val showIndentation: Boolean = true,
) {
    public companion object {
        /** Default V1 toolbar buttons. */
        public val Default: RichTextToolbarConfig = RichTextToolbarConfig(
            buttons = listOf(
                ToolbarButtonSpec(SpanStyle.Bold, "Bold"),
                ToolbarButtonSpec(SpanStyle.Italic, "Italic"),
                ToolbarButtonSpec(SpanStyle.Underline, "Underline"),
                ToolbarButtonSpec(SpanStyle.StrikeThrough, "Strikethrough"),
                ToolbarButtonSpec(SpanStyle.InlineCode, "Inline Code"),
                ToolbarButtonSpec(SpanStyle.Highlight(0xFFFFEB3B), "Highlight"),
            )
        )
    }
}
