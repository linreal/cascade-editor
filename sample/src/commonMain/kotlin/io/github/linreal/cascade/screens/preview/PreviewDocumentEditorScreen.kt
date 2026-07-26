package io.github.linreal.cascade.screens.preview

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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.linreal.cascade.editor.registry.DefaultBlockCallbacks
import io.github.linreal.cascade.editor.serialization.loadFromJson
import io.github.linreal.cascade.editor.serialization.toJson
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.rememberEditorState
import io.github.linreal.cascade.editor.ui.CascadeEditor
import io.github.linreal.cascade.screens.OpenedLinkPill
import io.github.linreal.cascade.screens.SavedPill
import io.github.linreal.cascade.screens.TitledEditorTopBar
import io.github.linreal.cascade.theme.SampleEditorTheme
import io.github.linreal.cascade.ui.PageScaffold
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Editable destination for one document opened from the preview gallery.
 *
 * Editor state is created here rather than in the grid, so the gallery keeps
 * paying only for static previews. Every export is published back through
 * [PreviewDocumentLibrary], which is what refreshes the originating card.
 */
@OptIn(FlowPreview::class)
@Composable
fun PreviewDocumentEditorScreen(
    documentId: String,
    library: PreviewDocumentLibrary,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
) {
    val document = library.document(documentId)
    if (document == null) {
        MissingDocument(isDark = isDark, onToggleTheme = onToggleTheme, onBack = onBack)
        return
    }

    val editorTheme = if (isDark) SampleEditorTheme.dark() else SampleEditorTheme.light()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

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
    var isLoaded by remember(documentId) { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf("") }
    var lastOpenedLink by remember { mutableStateOf("") }

    // Load the library snapshot once per document identity. The editor owns the
    // document from here on; the library is only written back to.
    LaunchedEffect(documentId) {
        editorState.loadFromJson(
            jsonString = library.document(documentId)?.json ?: "",
            textStates = textStates,
            spanStates = spanStates,
            typeCodec = PreviewSampleTypeCodec,
        )
        isLoaded = true
    }

    fun persist(): Boolean {
        if (!isLoaded) return true
        val json = editorState.toJson(
            textStates = textStates,
            spanStates = spanStates,
            typeCodec = PreviewSampleTypeCodec,
        )
        return library.save(documentId, json)
    }

    if (isLoaded) {
        LaunchedEffect(documentId) {
            snapshotFlow { editorState.state }
                .drop(1)
                .debounce(1_000)
                .collect {
                    saveStatus = if (persist()) "Saved" else "Save failed"
                    delay(2_000)
                    saveStatus = ""
                }
        }
    }

    PageScaffold {
        TitledEditorTopBar(
            title = document.title,
            isDark = isDark,
            onBack = {
                // Flush before leaving so the card behind this screen is already
                // refreshed by the time the grid recomposes.
                persist()
                onBack()
            },
            onToggleTheme = onToggleTheme,
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
                    modifier = Modifier.fillMaxSize().imePadding(),
                )
            }
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
private fun MissingDocument(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
) {
    PageScaffold {
        TitledEditorTopBar(
            title = "Document unavailable",
            isDark = isDark,
            onBack = onBack,
            onToggleTheme = onToggleTheme,
        )
    }
}
