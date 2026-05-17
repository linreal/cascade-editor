package io.github.linreal.cascade.screens.external_toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import io.github.linreal.cascade.editor.action.ClearFocus
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.rememberEditorState
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
import io.github.linreal.cascade.editor.ui.CascadeEditor
import io.github.linreal.cascade.editor.ui.CascadeEditorConfig
import io.github.linreal.cascade.editor.ui.LinkPopupSlot
import io.github.linreal.cascade.editor.ui.SlashCommandSlot
import io.github.linreal.cascade.editor.ui.ToolbarSlot
import io.github.linreal.cascade.editor.ui.rememberCascadeEditorToolbarController
import io.github.linreal.cascade.ui.PageScaffold

/**
 * Demo screen showing how an app can embed [CascadeEditor] inside an existing form
 * while rendering toolbar chrome outside the editor itself.
 *
 * The important integration point is that [rememberCascadeEditorToolbarController]
 * and [CascadeEditor] receive the same editor state/runtime holders. That lets the
 * host own placement, focus behavior, and surrounding UI without asking the editor
 * to render its built-in toolbar, slash command panel, or link popup.
 */
@Composable
fun ExternalToolbarScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
) {
    val editorTheme = if (isDark) CascadeEditorTheme.dark() else CascadeEditorTheme.light()

    val textStates = remember { BlockTextStates() }
    val spanStates = remember { BlockSpanStates() }
    val initialBlocks = remember { buildExternalToolbarDemoBlocks() }
    val editorState = rememberEditorState(initialBlocks)
    val focusManager = LocalFocusManager.current
    val callbacks = remember(editorState, textStates, spanStates) {
        DefaultBlockCallbacks(
            dispatchFn = { action -> editorState.dispatch(action) },
            stateProvider = { editorState.state },
            textStates = textStates,
            spanStates = spanStates,
            stateHolder = editorState,
        )
    }

    var isReadOnly by remember { mutableStateOf(false) }
    val editorConfig =
        CascadeEditorConfig(readOnly = isReadOnly, blockSelectionEnabled = false, blockDraggingEnabled = false)

    // Integration contract: this controller and CascadeEditor must share the same runtime holders.
    val toolbarController = rememberCascadeEditorToolbarController(
        stateHolder = editorState,
        textStates = textStates,
        spanStates = spanStates,
        trackedStyles = ExternalToolbarTrackedStyles,
        config = editorConfig,
    )

    val editorFocused = editorState.state.focusedBlockId != null
    val showToolbar = editorFocused && !isReadOnly
    var bodyBounds by remember { mutableStateOf<Rect?>(null) }
    var editorBounds by remember { mutableStateOf<Rect?>(null) }
    var toolbarBounds by remember { mutableStateOf<Rect?>(null) }
    val clearEditorFocus = remember(editorState, focusManager) {
        {
            editorState.dispatch(ClearFocus)
            focusManager.clearFocus()
        }
    }

    PageScaffold {
        ExternalToolbarScreenHeader(
            isReadOnly = isReadOnly,
            isDark = isDark,
            onBack = onBack,
            onToggleReadOnly = { isReadOnly = !isReadOnly },
            onToggleTheme = onToggleTheme,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { bodyBounds = it.boundsInRoot() }
                .clearEditorFocusWhenHostTapped(
                    editorFocused = editorFocused,
                    showToolbar = showToolbar,
                    bodyBounds = { bodyBounds },
                    editorBounds = { editorBounds },
                    toolbarBounds = { toolbarBounds },
                    clearEditorFocus = clearEditorFocus,
                ),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = ExternalToolbarTokens.FormTopPadding,
                    bottom = ExternalToolbarTokens.FormBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(ExternalToolbarTokens.FormFieldSpacing),
            ) {
                items(
                    items = ExternalToolbarScreenLeadingFields,
                    key = { field -> field.label },
                ) { field ->
                    ExternalToolbarReadOnlyField(field)
                }

                item {
                    ExternalToolbarEditorField(
                        focused = editorFocused,
                        onBoundsChanged = { editorBounds = it },
                    ) {
                        CascadeEditor(
                            stateHolder = editorState,
                            textStates = textStates,
                            spanStates = spanStates,
                            theme = editorTheme,
                            // The host screen owns all editing chrome in this demo.
                            toolbar = ToolbarSlot.None,
                            slashCommand = SlashCommandSlot.None,
                            linkPopup = LinkPopupSlot.None,
                            config = editorConfig,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                items(
                    items = ExternalToolbarScreenTrailingFields,
                    key = { field -> field.label },
                ) { field ->
                    ExternalToolbarReadOnlyField(field)
                }
            }

            this@PageScaffold.AnimatedVisibility(
                visible = showToolbar,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .onGloballyPositioned { toolbarBounds = it.boundsInRoot() }
                    .padding(
                        horizontal = ExternalToolbarTokens.ToolbarOuterHorizontalPadding,
                        vertical = ExternalToolbarTokens.ToolbarOuterVerticalPadding,
                    ),
            ) {
                ExternalToolbar(
                    controller = toolbarController,
                    editorState = editorState,
                    isReadOnly = isReadOnly,
                    onHideKeyboard = clearEditorFocus,
                    onToggleBasicList = {
                        toggleFocusedBlockType(
                            editorState = editorState,
                            callbacks = callbacks,
                            requestedType = BlockType.BulletList,
                            isReadOnly = isReadOnly,
                        )
                    },
                    onToggleNumberedList = {
                        toggleFocusedBlockType(
                            editorState = editorState,
                            callbacks = callbacks,
                            requestedType = BlockType.NumberedList(),
                            isReadOnly = isReadOnly,
                        )
                    },
                )
            }
        }
    }
}

/**
 * Clears editor focus when the user taps the surrounding host form.
 *
 * This mirrors a production screen where the editor is one field among many:
 * taps inside the editor or toolbar keep editing active, while taps elsewhere
 * dismiss the keyboard and hide the external toolbar.
 */
private fun Modifier.clearEditorFocusWhenHostTapped(
    editorFocused: Boolean,
    showToolbar: Boolean,
    bodyBounds: () -> Rect?,
    editorBounds: () -> Rect?,
    toolbarBounds: () -> Rect?,
    clearEditorFocus: () -> Unit,
): Modifier = pointerInput(editorFocused, showToolbar) {
    detectTapGestures { offset ->
        if (!editorFocused) return@detectTapGestures
        val bodyOffset = bodyBounds()?.topLeft ?: return@detectTapGestures
        val tapInRoot = bodyOffset + offset
        val isEditorTap = editorBounds()?.contains(tapInRoot) == true
        val isToolbarTap = showToolbar && toolbarBounds()?.contains(tapInRoot) == true

        if (!isEditorTap && !isToolbarTap) {
            clearEditorFocus()
        }
    }
}
