package io.github.linreal.cascade.ios.controller

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.ui.CascadeEditor
import io.github.linreal.cascade.editor.ui.CascadeEditorConfig
import io.github.linreal.cascade.editor.ui.LinkPopupSlot
import io.github.linreal.cascade.editor.ui.SlashCommandSlot
import io.github.linreal.cascade.editor.ui.ToolbarSlot
import io.github.linreal.cascade.editor.ui.rememberCascadeEditorToolbarController
import io.github.linreal.cascade.ios.toolbar.CascadeToolbarState
import io.github.linreal.cascade.ios.toolbar.toCascadeStyleState
import platform.UIKit.UIViewController

private val IosToolbarHighlightStyle: SpanStyle = SpanStyle.Highlight(0xFFFFEB3BL)

private val IosToolbarTrackedStyles: List<SpanStyle> = listOf(
    SpanStyle.Bold,
    SpanStyle.Italic,
    SpanStyle.Underline,
    SpanStyle.StrikeThrough,
    SpanStyle.InlineCode,
    IosToolbarHighlightStyle,
)

internal fun CascadeToolbarMode.toEditorLinkPopupSlot(): LinkPopupSlot = when (this) {
    CascadeToolbarMode.builtIn -> LinkPopupSlot.Default
    CascadeToolbarMode.none -> LinkPopupSlot.None
}

public fun CascadeEditorController.makeViewController(): UIViewController = onMainThread(
    fallback = { UIViewController() },
) {
    ComposeUIViewController {
        val configurationState by configurationSnapshot
        val editorConfig = remember(configurationState) {
            CascadeEditorConfig(
                readOnly = configurationState.readOnly,
                blockSelectionEnabled = configurationState.blockSelectionEnabled,
                blockDraggingEnabled = configurationState.blockDraggingEnabled,
                crashPolicy = configurationState.crashPolicy.toCoreCrashPolicy(),
                onInternalError = { error ->
                    reportInternalError(
                        "CascadeEditor ${error.context} failed: " +
                            (error.cause.message ?: error.cause.toString())
                    )
                },
            )
        }
        val theme = remember(configurationState.isDark) {
            configurationState.resolveEditorTheme()
        }
        val strings by resolvedStrings
        val blockStrings by resolvedBlockStrings
        val toolbarController = rememberCascadeEditorToolbarController(
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
            trackedStyles = IosToolbarTrackedStyles,
            config = editorConfig,
        )

        // Ownership token for this composition. All bridge-state publishes and the
        // dispose-time teardown are guarded on it, so when two hosts briefly overlap
        // (a second makeViewController() mounting before the first disposes) the
        // stale host can neither clobber live toolbar state nor un-mount the
        // controller out from under the visible editor.
        val mountToken = remember { Any() }

        DisposableEffect(Unit) {
            mountOwner = mountToken
            onDispose {
                if (mountOwner === mountToken) {
                    mountOwner = null
                    mounted = false
                    toolbarActions = null
                    updateToolbarStateFromHost(CascadeToolbarState.Empty)
                }
            }
        }

        SideEffect {
            if (mountOwner !== mountToken) return@SideEffect
            toolbarActions = CascadeToolbarActions(
                toggleBold = { toolbarController.formattingActions.toggleStyle(SpanStyle.Bold) },
                toggleItalic = { toolbarController.formattingActions.toggleStyle(SpanStyle.Italic) },
                toggleUnderline = { toolbarController.formattingActions.toggleStyle(SpanStyle.Underline) },
                toggleStrikeThrough = {
                    toolbarController.formattingActions.toggleStyle(SpanStyle.StrikeThrough)
                },
                toggleInlineCode = {
                    toolbarController.formattingActions.toggleStyle(SpanStyle.InlineCode)
                },
                toggleHighlight = { argb ->
                    toolbarController.formattingActions.toggleStyle(SpanStyle.Highlight(argb))
                },
                indentForward = { toolbarController.indentationActions.indentForward() },
                indentBackward = { toolbarController.indentationActions.indentBackward() },
                applyLink = { url, title ->
                    toolbarController.linkActions.applyLinkAtCurrentTarget(url, title)
                },
                removeLink = { toolbarController.linkActions.removeLinkAtCurrentTarget() },
            )
        }

        LaunchedEffect(toolbarController) {
            snapshotFlow {
                val formatting = toolbarController.formattingState.value
                val indentation = toolbarController.indentationState.value
                val link = toolbarController.linkState.value

                CascadeToolbarState(
                    focused = formatting.focusedBlockId != null,
                    canFormat = formatting.canFormat,
                    bold = formatting.styleStatusOf(SpanStyle.Bold).toCascadeStyleState(),
                    italic = formatting.styleStatusOf(SpanStyle.Italic).toCascadeStyleState(),
                    underline = formatting.styleStatusOf(SpanStyle.Underline).toCascadeStyleState(),
                    strikeThrough = formatting.styleStatusOf(SpanStyle.StrikeThrough).toCascadeStyleState(),
                    inlineCode = formatting.styleStatusOf(SpanStyle.InlineCode).toCascadeStyleState(),
                    highlight = formatting.styleStatusOf(IosToolbarHighlightStyle).toCascadeStyleState(),
                    canIndentForward = indentation.canIndentForward,
                    canIndentBackward = indentation.canIndentBackward,
                    canLink = link.canLink,
                    existingUrl = link.existingUrl,
                )
            }.collect { toolbarState ->
                if (mountOwner === mountToken) {
                    updateToolbarStateFromHost(toolbarState)
                }
            }
        }

        LaunchedEffect(Unit) {
            var previousState = stateHolder.state
            var previousDocumentBlocks = currentDocumentBlocks()
            // The controller only counts as mounted once this observer holds its
            // baseline. A public mutation landing between first composition and
            // this coroutine's start is therefore still delivered through the
            // direct (unmounted) notification path, and the baseline captured
            // here already includes it — no change signal is lost in the window.
            if (mountOwner === mountToken) {
                mounted = true
            }
            snapshotFlow {
                stateHolder.state to currentDocumentBlocks()
            }.collect { (state, documentBlocks) ->
                val documentChanged = documentBlocks != previousDocumentBlocks
                if (state != previousState || documentChanged) {
                    previousState = state
                    if (mountOwner === mountToken) {
                        notifyStateChangedFromHost()
                    }
                }
                if (documentChanged) {
                    previousDocumentBlocks = documentBlocks
                    if (mountOwner === mountToken) {
                        notifyDocumentChangedFromHost()
                    }
                }
            }
        }

        CascadeEditor(
            stateHolder = stateHolder,
            textStates = textStates,
            spanStates = spanStates,
            registry = registry,
            slashRegistry = slashRegistry,
            slashCommand = if (configurationState.slashCommandsEnabled) {
                SlashCommandSlot.Default
            } else {
                SlashCommandSlot.None
            },
            theme = theme,
            strings = strings,
            blockStrings = blockStrings,
            modifier = Modifier.fillMaxSize(),
            toolbar = if (configurationState.toolbarMode == CascadeToolbarMode.builtIn) {
                ToolbarSlot.Default()
            } else {
                ToolbarSlot.None
            },
            linkPopup = configurationState.toolbarMode.toEditorLinkPopupSlot(),
            onOpenLink = { url ->
                invokeCallback("onOpenLink", onOpenLink, url)
            },
            config = editorConfig,
        )
    }
}
