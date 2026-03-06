package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import io.github.linreal.cascade.editor.richtext.FormattingActions
import io.github.linreal.cascade.editor.richtext.FormattingState

/**
 * Default config-driven rich text toolbar.
 *
 * Renders toggle buttons for each style in [config], reflecting the current
 * [formattingState] and dispatching toggle actions via [actions].
 *
 * Full implementation in Subtask 10.7.
 */
@Composable
internal fun RichTextToolbar(
    formattingState: State<FormattingState>,
    actions: FormattingActions,
    config: RichTextToolbarConfig,
    modifier: Modifier = Modifier,
) {
    // Placeholder — Subtask 10.7 implements the full toolbar UI.
}
