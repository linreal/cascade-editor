package io.github.linreal.cascade.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cascadeeditor.sample.generated.resources.Res
import io.github.linreal.cascade.editor.action.ClearSelection
import io.github.linreal.cascade.editor.action.DeleteSelectedOrFocused
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.serialization.loadFromJson
import io.github.linreal.cascade.editor.serialization.toJson
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.rememberEditorState
import io.github.linreal.cascade.theme.SampleEditorTheme
import io.github.linreal.cascade.editor.ui.CascadeEditor
import io.github.linreal.cascade.editor.ui.CascadeEditorConfig
import io.github.linreal.cascade.storage.DocumentStorage
import io.github.linreal.cascade.storage.rememberDocumentStorage
import io.github.linreal.cascade.ui.PageScaffold
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Composable
fun EditorDemoScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
) {
    val editorTheme = if (isDark) SampleEditorTheme.dark() else SampleEditorTheme.light()
    val storage = rememberDocumentStorage()
    val scope = rememberCoroutineScope()

    val textStates = remember { BlockTextStates() }
    val spanStates = remember { BlockSpanStates() }
    val editorState = rememberEditorState()
    val callbacks = remember(editorState, textStates, spanStates) {
        DefaultBlockCallbacks(
            dispatchFn = { action -> editorState.dispatch(action) },
            stateProvider = { editorState.state },
            textStates = textStates,
            spanStates = spanStates,
            stateHolder = editorState,
        )
    }
    var isLoaded by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf("") }
    var lastOpenedLink by remember { mutableStateOf("") }
    var isReadOnly by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    LoadDocumentEffect(
        storage = storage,
        editorState = editorState,
        textStates = textStates,
        spanStates = spanStates,
        onLoaded = { isLoaded = true },
    )
    AutoSaveEffect(
        isLoaded = isLoaded,
        isReadOnly = isReadOnly,
        storage = storage,
        editorState = editorState,
        textStates = textStates,
        spanStates = spanStates,
        onSaveStatusChange = { saveStatus = it },
    )

    PageScaffold {
        EditorDemoHeader(
            hasSelection = editorState.state.hasSelection,
            selectedCount = editorState.state.selectedBlockIds.size,
            isReadOnly = isReadOnly,
            isDark = isDark,
            canUndo = editorState.canUndo,
            canRedo = editorState.canRedo,
            onCancelSelection = { editorState.dispatch(ClearSelection) },
            onDeleteSelected = { callbacks.dispatch(DeleteSelectedOrFocused) },
            onBack = {
                scope.launch {
                    if (isLoaded && !isReadOnly) {
                        val json = editorState.toJson(textStates, spanStates)
                        storage.write(json)
                    }
                    onBack()
                }
            },
            onUndo = { editorState.undo() },
            onRedo = { editorState.redo() },
            onToggleReadOnly = { isReadOnly = !isReadOnly },
            onToggleTheme = onToggleTheme,
            onReset = {
                scope.launch {
                    storage.delete()
                    val json = loadDefaultDocument()
                    editorState.loadFromJson(json, textStates, spanStates)
                }
            },
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isLoaded) {
                CascadeEditor(
                    stateHolder = editorState,
                    textStates = textStates,
                    spanStates = spanStates,
                    theme = editorTheme,
                    onOpenLink = { url ->
                        lastOpenedLink = url
                        scope.launch {
                            delay(3_000)
                            if (lastOpenedLink == url) lastOpenedLink = ""
                        }
                        runCatching { uriHandler.openUri(url) }
                    },
                    config = CascadeEditorConfig(readOnly = isReadOnly),
                    modifier = Modifier.fillMaxSize().imePadding(),
                )
            }
            // Floating status indicators, centered just below the top bar.
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SavedPill(status = saveStatus)
                OpenedLinkPill(link = lastOpenedLink)
            }
        }
    }
}

@Composable
private fun LoadDocumentEffect(
    storage: DocumentStorage,
    editorState: EditorStateHolder,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    onLoaded: () -> Unit,
) {
    LaunchedEffect(Unit) {
        val json = storage.read() ?: loadDefaultDocument()
        try {
            editorState.loadFromJson(json, textStates, spanStates)
        } catch (_: Exception) {
            storage.delete()
            editorState.loadFromJson(loadDefaultDocument(), textStates, spanStates)
        }
        onLoaded()
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun AutoSaveEffect(
    isLoaded: Boolean,
    isReadOnly: Boolean,
    storage: DocumentStorage,
    editorState: EditorStateHolder,
    textStates: BlockTextStates,
    spanStates: BlockSpanStates,
    onSaveStatusChange: (String) -> Unit,
) {
    val currentReadOnly = rememberUpdatedState(isReadOnly)

    if (isLoaded) {
        LaunchedEffect(Unit) {
            snapshotFlow { editorState.state }
                .drop(1)
                .debounce(2_000)
                .collect {
                    if (currentReadOnly.value) return@collect
                    onSaveStatusChange("Saving...")
                    val json = editorState.toJson(textStates, spanStates)
                    storage.write(json)
                    onSaveStatusChange("Saved")
                    delay(2_000)
                    onSaveStatusChange("")
                }
        }
    }
}

@Composable
private fun EditorDemoHeader(
    hasSelection: Boolean,
    selectedCount: Int,
    isReadOnly: Boolean,
    isDark: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onCancelSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleReadOnly: () -> Unit,
    onToggleTheme: () -> Unit,
    onReset: () -> Unit,
) {
    AnimatedContent(
        targetState = hasSelection,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "header",
    ) { selectionMode ->
        if (selectionMode) {
            SelectionTopBar(
                selectedCount = selectedCount,
                isReadOnly = isReadOnly,
                onCancelSelection = onCancelSelection,
                onDeleteSelected = onDeleteSelected,
            )
        } else {
            EditorTopBar(
                isReadOnly = isReadOnly,
                isDark = isDark,
                canUndo = canUndo,
                canRedo = canRedo,
                onBack = onBack,
                onUndo = onUndo,
                onRedo = onRedo,
                onToggleReadOnly = onToggleReadOnly,
                onToggleTheme = onToggleTheme,
                onReset = onReset,
            )
        }
    }
}

private suspend fun loadDefaultDocument(): String {
    return Res.readBytes("files/default_document.json").decodeToString()
}
