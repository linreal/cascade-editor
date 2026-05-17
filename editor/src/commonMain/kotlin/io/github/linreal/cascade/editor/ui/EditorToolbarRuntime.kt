package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.indentation.DefaultIndentationActions
import io.github.linreal.cascade.editor.indentation.IndentationActions
import io.github.linreal.cascade.editor.indentation.IndentationState
import io.github.linreal.cascade.editor.indentation.rememberIndentationState
import io.github.linreal.cascade.editor.richtext.DefaultFormattingActions
import io.github.linreal.cascade.editor.richtext.DefaultLinkActions
import io.github.linreal.cascade.editor.richtext.FormattingActions
import io.github.linreal.cascade.editor.richtext.FormattingState
import io.github.linreal.cascade.editor.richtext.LinkActionDispatcher
import io.github.linreal.cascade.editor.richtext.LinkChromeActions
import io.github.linreal.cascade.editor.richtext.LinkState
import io.github.linreal.cascade.editor.richtext.SpanActionDispatcher
import io.github.linreal.cascade.editor.richtext.rememberFormattingState
import io.github.linreal.cascade.editor.richtext.rememberLinkState
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder

/**
 * Internal shared wiring for editor toolbar state and actions.
 *
 * Used by both [CascadeEditor] (built-in/custom toolbars rendered inside the
 * editor subtree) and [rememberCascadeEditorToolbarController] (app-owned
 * toolbars rendered outside the editor viewport). Centralizing the assembly
 * keeps remember-key tuples, policy-provider semantics, and dispatcher
 * construction in lock-step across both call sites.
 *
 * **CONTRACT — must not introduce composition-scoped side effects.**
 * Callers may invoke this from sibling compositions whose lifetime is not
 * tied to [CascadeEditor]. Keep [rememberEditorToolbarRuntime] purely
 * synchronous: no `LaunchedEffect`, no `DisposableEffect`, no coroutine
 * launches. Any such effect would break the controller test harness, which
 * composes the factory, awaits idle, then disposes the composition before
 * exercising the returned state/actions.
 */
@Stable
internal class EditorToolbarRuntime(
    val formattingState: State<FormattingState>?,
    val formattingActions: FormattingActions,
    val spanActionDispatcher: SpanActionDispatcher,
    val indentationState: State<IndentationState>,
    val indentationActions: IndentationActions,
    val linkState: State<LinkState>,
    val linkActions: LinkChromeActions,
)

@Composable
internal fun rememberEditorToolbarRuntime(
    stateHolder: EditorStateHolder,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    trackedStyles: List<SpanStyle>,
    interactionPolicy: EditorInteractionPolicy,
    needsFormattingState: Boolean,
): EditorToolbarRuntime {
    val currentInteractionPolicy = rememberUpdatedState(interactionPolicy)

    val formattingState = if (needsFormattingState) {
        rememberFormattingState(
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
            trackedStyles = trackedStyles,
            policy = interactionPolicy,
        )
    } else {
        null
    }

    val spanActionDispatcher = remember(stateHolder, textStates, spanStates) {
        SpanActionDispatcher(
            dispatchFn = { action -> stateHolder.dispatch(action) },
            textStates = textStates,
            spanStates = spanStates,
            stateHolder = stateHolder,
        )
    }

    // interactionPolicy is intentionally NOT a remember key for action objects:
    // they are stateless and read the latest policy via
    // currentInteractionPolicy.value at invocation time, so a policy flip needs
    // no wrapper rebuild.
    val formattingActions = remember(stateHolder, textStates, spanActionDispatcher) {
        DefaultFormattingActions(
            stateHolder = stateHolder,
            textStates = textStates,
            spanActionDispatcher = spanActionDispatcher,
            policyProvider = { currentInteractionPolicy.value },
        )
    }

    val indentationState = rememberIndentationState(stateHolder, interactionPolicy)
    val indentationActions = remember(stateHolder, indentationState) {
        DefaultIndentationActions(
            stateProvider = { indentationState.value },
            dispatchAction = { action -> stateHolder.dispatchStructuralAction(action) },
            policyProvider = { currentInteractionPolicy.value },
        )
    }

    val linkState = rememberLinkState(
        stateHolder = stateHolder,
        textStates = textStates,
        spanStates = spanStates,
        policy = interactionPolicy,
    )
    val linkActionDispatcher = remember(stateHolder, textStates, spanStates) {
        LinkActionDispatcher(
            dispatchFn = { action -> stateHolder.dispatch(action) },
            textStates = textStates,
            spanStates = spanStates,
            stateHolder = stateHolder,
        )
    }
    val linkActions = remember(linkState, linkActionDispatcher) {
        DefaultLinkActions(
            stateProvider = { linkState.value },
            delegate = linkActionDispatcher,
            policyProvider = { currentInteractionPolicy.value },
        )
    }

    return remember(
        formattingState,
        formattingActions,
        spanActionDispatcher,
        indentationState,
        indentationActions,
        linkState,
        linkActions,
    ) {
        EditorToolbarRuntime(
            formattingState = formattingState,
            formattingActions = formattingActions,
            spanActionDispatcher = spanActionDispatcher,
            indentationState = indentationState,
            indentationActions = indentationActions,
            linkState = linkState,
            linkActions = linkActions,
        )
    }
}
