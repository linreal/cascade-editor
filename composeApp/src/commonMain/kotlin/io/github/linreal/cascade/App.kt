package io.github.linreal.cascade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cascadeeditor.composeapp.generated.resources.Res
import io.github.linreal.cascade.editor.serialization.loadFromJson
import io.github.linreal.cascade.editor.serialization.toJson
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.rememberEditorState
import io.github.linreal.cascade.editor.ui.CascadeEditor
import io.github.linreal.cascade.editor.ui.utils.Spacers
import io.github.linreal.cascade.storage.rememberDocumentStorage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@Composable
@Preview
fun App() {
    MaterialTheme {
        val storage = rememberDocumentStorage()
        val scope = rememberCoroutineScope()

        val textStates = remember { BlockTextStates() }
        val spanStates = remember { BlockSpanStates() }
        val editorState = rememberEditorState()
        var isLoaded by remember { mutableStateOf(false) }

        // Load saved document or fall back to bundled default
        LaunchedEffect(Unit) {
            val json = storage.read() ?: loadDefaultDocument()
            try {
                editorState.loadFromJson(json, textStates, spanStates)
            } catch (_: Exception) {
                // Corrupted saved document — reset to default
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
                        val json = editorState.toJson(textStates, spanStates)
                        storage.write(json)
                    }
            }
        }

        Column(
            modifier = Modifier.background(Color.White).safeContentPadding().fillMaxSize(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Cascade Editor",
                    color = Color.Black,
                    fontSize = 40.sp,
                    textAlign = TextAlign.Center,
                )
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
            Spacers.Vertical(40.dp)
            if (isLoaded) {
                CascadeEditor(
                    stateHolder = editorState,
                    textStates = textStates,
                    spanStates = spanStates,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private suspend fun loadDefaultDocument(): String {
    return Res.readBytes("files/default_document.json").decodeToString()
}
