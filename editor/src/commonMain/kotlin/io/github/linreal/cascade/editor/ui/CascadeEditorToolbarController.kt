package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.indentation.IndentationActions
import io.github.linreal.cascade.editor.indentation.IndentationState
import io.github.linreal.cascade.editor.richtext.FormattingActions
import io.github.linreal.cascade.editor.richtext.FormattingState
import io.github.linreal.cascade.editor.richtext.LinkChromeActions
import io.github.linreal.cascade.editor.richtext.LinkState
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder

/**
 * Public state and action surface for app-owned editor toolbars.
 *
 * The controller does not render UI and does not need to live inside the
 * [CascadeEditor] composition subtree. Pass the same [stateHolder],
 * [textStates], and [spanStates] to this factory and to [CascadeEditor] so
 * actions operate on the live editor runtime.
 */
@Stable
public interface CascadeEditorToolbarController {
    public val formattingState: State<FormattingState>
    public val formattingActions: FormattingActions

    public val indentationState: State<IndentationState>
    public val indentationActions: IndentationActions

    public val linkState: State<LinkState>
    public val linkActions: LinkChromeActions
}

private val DefaultTrackedStyles: List<SpanStyle> =
    RichTextToolbarConfig.Default.buttons.map { it.style }

/**
 * Remembers a caller-owned toolbar controller for external editor chrome.
 *
 * Use this with [CascadeEditor]'s `toolbar = ToolbarSlot.None` when the toolbar
 * should be rendered outside the editor viewport.
 *
 * The underlying wiring is shared with [CascadeEditor] via
 * [rememberEditorToolbarRuntime]; the factory therefore inherits that
 * contract and must not introduce composition-scoped side effects.
 */
@Composable
public fun rememberCascadeEditorToolbarController(
    stateHolder: EditorStateHolder,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    trackedStyles: List<SpanStyle> = DefaultTrackedStyles,
    config: CascadeEditorConfig = CascadeEditorConfig.Default,
): CascadeEditorToolbarController {
    val interactionPolicy = remember(config) {
        config.toInteractionPolicy()
    }

    val runtime = rememberEditorToolbarRuntime(
        stateHolder = stateHolder,
        textStates = textStates,
        spanStates = spanStates,
        trackedStyles = trackedStyles,
        interactionPolicy = interactionPolicy,
        needsFormattingState = true,
    )

    return remember(runtime) {
        DefaultCascadeEditorToolbarController(
            formattingState = requireNotNull(runtime.formattingState) {
                "EditorToolbarRuntime must produce formattingState when needsFormattingState=true"
            },
            formattingActions = runtime.formattingActions,
            indentationState = runtime.indentationState,
            indentationActions = runtime.indentationActions,
            linkState = runtime.linkState,
            linkActions = runtime.linkActions,
        )
    }
}

@Stable
private class DefaultCascadeEditorToolbarController(
    override val formattingState: State<FormattingState>,
    override val formattingActions: FormattingActions,
    override val indentationState: State<IndentationState>,
    override val indentationActions: IndentationActions,
    override val linkState: State<LinkState>,
    override val linkActions: LinkChromeActions,
) : CascadeEditorToolbarController
