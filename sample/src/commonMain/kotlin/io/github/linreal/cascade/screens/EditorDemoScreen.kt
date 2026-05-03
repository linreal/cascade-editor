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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cascadeeditor.sample.generated.resources.Res
import cascadeeditor.sample.generated.resources.ic_arrow_back
import cascadeeditor.sample.generated.resources.ic_dark_mode
import cascadeeditor.sample.generated.resources.ic_delete
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
import io.github.linreal.cascade.editor.state.rememberEditorState
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
import io.github.linreal.cascade.editor.ui.CascadeEditor
import io.github.linreal.cascade.storage.rememberDocumentStorage
import io.github.linreal.cascade.ui.PageScaffold
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@OptIn(FlowPreview::class)
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
    val uriHandler = LocalUriHandler.current

    // Load saved document or fall back to bundled default
    LaunchedEffect(Unit) {
        val json = storage.read() ?: loadDefaultDocument()
        try {
            editorState.loadFromJson(json, textStates, spanStates)
        } catch (_: Exception) {
            storage.delete()
            editorState.loadFromJson(loadDefaultDocument(), textStates, spanStates)
        }
        isLoaded = true
    }

    // Auto-save 2s after last state change
    if (isLoaded) {
        LaunchedEffect(Unit) {
            snapshotFlow { editorState.state }
                .drop(1)
                .debounce(2_000)
                .collect {
                    saveStatus = "Saving..."
                    val json = editorState.toJson(textStates, spanStates)
                    storage.write(json)
                    saveStatus = "Saved"
                    delay(2_000)
                    saveStatus = ""
                }
        }
    }

    PageScaffold {
        // Header — swaps between normal and selection mode
        val hasSelection = editorState.state.hasSelection
        AnimatedContent(
            targetState = hasSelection,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "header",
        ) { selectionMode ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { editorState.dispatch(ClearSelection) }) {
                            Text("Cancel")
                        }
                        Text(
                            text = "${editorState.state.selectedBlockIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    IconButton(
                        onClick = { callbacks.dispatch(DeleteSelectedOrFocused) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.ic_delete),
                            contentDescription = "Delete selected blocks",
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (isLoaded) {
                                        val json = editorState.toJson(textStates, spanStates)
                                        storage.write(json)
                                    }
                                    onBack()
                                }
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.ic_arrow_back),
                                contentDescription = "Back",
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        val undoTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (editorState.canUndo) 1f else 0.38f
                        )
                        val redoTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (editorState.canRedo) 1f else 0.38f
                        )
                        IconButton(
                            onClick = { editorState.undo() },
                            enabled = editorState.canUndo,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.ic_undo),
                                contentDescription = "Undo",
                                colorFilter = ColorFilter.tint(undoTint),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = { editorState.redo() },
                            enabled = editorState.canRedo,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.ic_redo),
                                contentDescription = "Redo",
                                colorFilter = ColorFilter.tint(redoTint),
                                modifier = Modifier.size(20.dp),
                            )
                        }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Image(
                                painter = painterResource(
                                    if (isDark) Res.drawable.ic_light_mode else Res.drawable.ic_dark_mode
                                ),
                                contentDescription = if (isDark) "Switch to light" else "Switch to dark",
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = {
                                scope.launch {
                                    storage.delete()
                                    val json = loadDefaultDocument()
                                    editorState.loadFromJson(json, textStates, spanStates)
                                }
                            }
                        ) {
                            Text("Reset")
                        }
                    }
                }
            }
        }
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
                modifier = Modifier.fillMaxSize().imePadding(),
            )
        }
    }
}

private suspend fun loadDefaultDocument(): String {
    return Res.readBytes("files/default_document.json").decodeToString()
}
