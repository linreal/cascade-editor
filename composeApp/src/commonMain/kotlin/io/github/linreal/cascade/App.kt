package io.github.linreal.cascade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.state.rememberEditorState
import io.github.linreal.cascade.editor.ui.CascadeEditor
import io.github.linreal.cascade.editor.ui.utils.Spacers

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

        Column(
            modifier = Modifier
                .background(Color.White)
                .safeContentPadding()
                .fillMaxSize(),
        ) {

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "Cascade Editor",
                color = Color.Black,
                fontSize = 40.sp,
                textAlign = TextAlign.Center
            )
            Spacers.Vertical(40.dp)
            CascadeEditor(
                stateHolder = editorState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}