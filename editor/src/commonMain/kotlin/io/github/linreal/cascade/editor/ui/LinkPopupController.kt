package io.github.linreal.cascade.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import io.github.linreal.cascade.editor.action.FocusBlock
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.richtext.LinkActions
import io.github.linreal.cascade.editor.richtext.LinkState
import io.github.linreal.cascade.editor.richtext.LinkTarget
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Holds editor-managed link popup session state and lifecycle.
 *
 * Owns the active [LinkPopupSession] and focus restoration on close.
 * Constructed and managed via [rememberLinkPopupController] from [CascadeEditor].
 */
@Stable
internal class LinkPopupController(
    private val linkActions: LinkActions,
    private val stateHolder: EditorStateHolder,
    private val textStates: BlockTextStates,
    private val spanStates: BlockSpanStates,
    private val linkStateProvider: () -> LinkState,
) {
    var session: LinkPopupSession? by mutableStateOf(null)
        private set

    /**
     * Opens a popup session against the latest [LinkState], or no-ops when
     * [LinkState.canLink] is false or no link target is currently resolvable.
     */
    fun open() {
        val newSession = LinkPopupSession.create(
            linkState = linkStateProvider(),
            linkActions = linkActions,
            textGeneration = textStates.generation,
            spanGeneration = spanStates.generation,
            onRequestClose = ::handleClose,
        ) ?: return
        session = newSession
    }

    /**
     * Closes any active session without restoring focus, used when the slot
     * configuration changes to [LinkPopupSlot.None] or when the editor decides
     * the session has become invalid (deleted target block, runtime reset).
     */
    fun forceDismiss() {
        if (session == null) return
        session = null
    }

    private fun handleClose(request: LinkPopupCloseRequest) {
        val current = session ?: return
        session = null
        restoreFocus(
            target = current.target,
            restoreSelection = request == LinkPopupCloseRequest.RestoreCapturedSelection,
        )
    }

    private fun restoreFocus(target: LinkTarget, restoreSelection: Boolean) {
        val targetBlockExists = stateHolder.state.blocks.any { block ->
            block.id == target.blockId &&
                block.type.supportsText &&
                block.content is BlockContent.Text
        }
        if (!targetBlockExists) return
        if (restoreSelection) {
            // Collapse to a cursor at the end of the captured range rather
            // than restoring the full range. BasicTextField keeps a non-empty
            // selection visible as a highlight even when the field loses focus,
            // which makes the highlight linger on the block after the user
            // moves to another block. Collapsing avoids that lingering visual
            // while still landing the cursor where the user was.
            textStates.setSelection(
                blockId = target.blockId,
                selection = TextRange(target.normalizedEnd),
            )
        }
        stateHolder.dispatch(FocusBlock(target.blockId))
    }
}

/**
 * Constructs and remembers a [LinkPopupController] bound to the supplied
 * holders, and wires up two lifecycle effects:
 *
 *  1. Switching to [LinkPopupSlot.None] force-dismisses any active popup so
 *     consumer chrome owns link UI alone.
 *  2. While a session is active, the controller dismisses it whenever the
 *     captured target block disappears or runtime text/span generations advance
 *     (hard reset). This is implemented with a [derivedStateOf] guard so the
 *     observer only fires on actual invalidation transitions, not on every
 *     unrelated state change to the document.
 */
@Composable
internal fun rememberLinkPopupController(
    linkPopup: LinkPopupSlot,
    linkActions: LinkActions,
    stateHolder: EditorStateHolder,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    linkState: State<LinkState>,
): LinkPopupController {
    val controller = remember(linkActions, stateHolder, textStates, spanStates) {
        LinkPopupController(
            linkActions = linkActions,
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
            linkStateProvider = { linkState.value },
        )
    }

    // Force-dismiss when the host switches to LinkPopupSlot.None.
    DisposableEffect(linkPopup, controller) {
        if (linkPopup == LinkPopupSlot.None) controller.forceDismiss()
        onDispose { /* no-op */ }
    }

    // Invalidation guard: only flips when the captured target genuinely
    // becomes stale, so the LaunchedEffect body runs at most twice per
    // session (once true, once false) instead of on every document change.
    val invalidated = remember(controller, textStates, spanStates) {
        derivedStateOf {
            val active = controller.session ?: return@derivedStateOf false
            val genChanged = active.textGeneration != textStates.generation ||
                active.spanGeneration != spanStates.generation
            if (genChanged) return@derivedStateOf true
            stateHolder.state.blocks.none { block ->
                block.id == active.target.blockId &&
                    block.type.supportsText &&
                    block.content is BlockContent.Text
            }
        }
    }

    LaunchedEffect(controller) {
        snapshotFlow { invalidated.value }
            .distinctUntilChanged()
            .collect { stale ->
                if (stale) controller.forceDismiss()
            }
    }

    return controller
}
