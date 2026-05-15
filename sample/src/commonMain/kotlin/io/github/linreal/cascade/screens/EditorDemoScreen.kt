package io.github.linreal.cascade.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cascadeeditor.sample.generated.resources.Res
import cascadeeditor.sample.generated.resources.ic_arrow_back
import cascadeeditor.sample.generated.resources.ic_dark_mode
import cascadeeditor.sample.generated.resources.ic_delete
import cascadeeditor.sample.generated.resources.ic_edit
import cascadeeditor.sample.generated.resources.ic_edit_off
import cascadeeditor.sample.generated.resources.ic_light_mode
import cascadeeditor.sample.generated.resources.ic_redo
import cascadeeditor.sample.generated.resources.ic_undo
import io.github.linreal.cascade.editor.action.ClearSelection
import io.github.linreal.cascade.editor.action.DeleteSelectedOrFocused
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.serialization.loadFromJson
import io.github.linreal.cascade.editor.serialization.toJson
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorStateHolder
import io.github.linreal.cascade.editor.state.rememberEditorState
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
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
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun EditorDemoScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
) {
    val editorTheme = if (isDark) CascadeEditorTheme.dark() else CascadeEditorTheme.light()
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
            saveStatus = saveStatus,
            lastOpenedLink = lastOpenedLink,
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
    saveStatus: String,
    lastOpenedLink: String,
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
            SelectionHeader(
                selectedCount = selectedCount,
                isReadOnly = isReadOnly,
                onCancelSelection = onCancelSelection,
                onDeleteSelected = onDeleteSelected,
            )
        } else {
            EditingHeader(
                isReadOnly = isReadOnly,
                isDark = isDark,
                canUndo = canUndo,
                canRedo = canRedo,
                saveStatus = saveStatus,
                lastOpenedLink = lastOpenedLink,
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

@Composable
private fun SelectionHeader(
    selectedCount: Int,
    isReadOnly: Boolean,
    onCancelSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancelSelection) {
                Text("Cancel")
            }
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        HeaderIconButton(
            icon = Res.drawable.ic_delete,
            contentDescription = "Delete selected blocks",
            tint = MaterialTheme.colorScheme.error.copy(
                alpha = if (isReadOnly) 0.38f else 1f,
            ),
            iconSize = 24.dp,
            enabled = !isReadOnly,
            onClick = onDeleteSelected,
        )
    }
}

@Composable
private fun EditingHeader(
    isReadOnly: Boolean,
    isDark: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    saveStatus: String,
    lastOpenedLink: String,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleReadOnly: () -> Unit,
    onToggleTheme: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderIconButton(
                icon = Res.drawable.ic_arrow_back,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
                iconSize = 24.dp,
                onClick = onBack,
            )
            HeaderIconButton(
                icon = Res.drawable.ic_undo,
                contentDescription = "Undo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (!isReadOnly && canUndo) 1f else 0.38f,
                ),
                enabled = !isReadOnly && canUndo,
                onClick = onUndo,
            )
            HeaderIconButton(
                icon = Res.drawable.ic_redo,
                contentDescription = "Redo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (!isReadOnly && canRedo) 1f else 0.38f,
                ),
                enabled = !isReadOnly && canRedo,
                onClick = onRedo,
            )
            EditorStatusLabels(
                saveStatus = saveStatus,
                lastOpenedLink = lastOpenedLink,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderIconButton(
                icon = if (isReadOnly) Res.drawable.ic_edit_off else Res.drawable.ic_edit,
                contentDescription = if (isReadOnly) "Read-only" else "Editable",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onToggleReadOnly,
            )
            Spacer(modifier = Modifier.width(4.dp))
            HeaderIconButton(
                icon = if (isDark) Res.drawable.ic_light_mode else Res.drawable.ic_dark_mode,
                contentDescription = if (isDark) "Switch to light" else "Switch to dark",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onToggleTheme,
            )
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                enabled = !isReadOnly,
                onClick = onReset,
            ) {
                Text("Reset")
            }
        }
    }
}

@Composable
private fun EditorStatusLabels(
    saveStatus: String,
    lastOpenedLink: String,
) {
    AnimatedVisibility(
        visible = saveStatus.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Text(
            text = saveStatus,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
    AnimatedVisibility(
        visible = lastOpenedLink.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Text(
            text = "Opened: $lastOpenedLink",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun HeaderIconButton(
    icon: DrawableResource,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(35.dp),
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(iconSize),
        )
    }
}

private suspend fun loadDefaultDocument(): String {
    return Res.readBytes("files/default_document.json").decodeToString()
}
