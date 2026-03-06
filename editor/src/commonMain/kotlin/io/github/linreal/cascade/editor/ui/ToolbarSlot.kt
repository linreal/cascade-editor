package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.richtext.FormattingActions
import io.github.linreal.cascade.editor.richtext.FormattingState

/**
 * Determines what toolbar (if any) is shown at the bottom of the editor.
 */
public sealed interface ToolbarSlot {

    /** Default config-driven toolbar with toggle buttons for built-in styles. */
    public data class Default(
        val config: RichTextToolbarConfig = RichTextToolbarConfig.Default,
    ) : ToolbarSlot

    /** No toolbar rendered. */
    public data object None : ToolbarSlot

    /**
     * Consumer-provided composable toolbar receiving reactive state and actions.
     *
     * @param trackedStyles Styles to include in [FormattingState.styles].
     *        Defaults to the default toolbar styles. Override to track additional
     *        styles (e.g., [SpanStyle.Link][io.github.linreal.cascade.editor.core.SpanStyle.Link]).
     */
    public data class Custom(
        val trackedStyles: List<SpanStyle> =
            RichTextToolbarConfig.Default.buttons.map { it.style },
        val content: @Composable (
            formattingState: State<FormattingState>,
            actions: FormattingActions,
        ) -> Unit,
    ) : ToolbarSlot
}
