package io.github.linreal.cascade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.state.rememberEditorState
import io.github.linreal.cascade.editor.ui.CascadeEditor

@Composable
@Preview
fun App() {
    MaterialTheme {
        // Create initial blocks for the editor
        val initialBlocks = remember {
            listOf(
                Block.paragraph("Welcome to CascadeEditor!"),
                Block.paragraph("Press Enter to split a block."),
                Block.paragraph("Press Backspace at the start to merge with the previous block.")
            )
        }

        val editorState = rememberEditorState(initialBlocks)

        Box(
            modifier = Modifier
                .background(Color.White)
                .safeContentPadding()
                .fillMaxSize(),
        ) {
            CascadeEditor(
                stateHolder = editorState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}