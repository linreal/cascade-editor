package io.github.linreal.cascade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
                    Block.paragraph(
                            "1. Welcome to CascadeEditor! This is a powerful block-based editor."
                    ),
                    Block.paragraph("2. Short block."),
                    Block.paragraph(
                            "3. This is a medium sized block that contains a bit more information about how this editor works and what you can do with it."
                    ),
                    Block.paragraph(
                            "4. Multiple blocks make it easier to test scrolling and drag-and-drop features that were recently implemented."
                    ),
                    Block.paragraph("5. Another short one."),
                    Block.paragraph("6. Let's add a very long block here. " + "Word ".repeat(50)),
                    Block.paragraph("7. Block number seven is here to stay."),
                    Block.paragraph("8. Press Enter to split a block."),
                    Block.paragraph(
                            "9. Press Backspace at the start to merge with the previous block."
                    ),
                    Block.paragraph(
                            "10. Halfway there! This editor supports various block types and interactions."
                    ),
                    Block.paragraph(
                            "11. Text alignment and styling are important for a good reading experience."
                    ),
                    Block.paragraph("12. " + "Expanding content ".repeat(10)),
                    Block.paragraph("13. Small."),
                    Block.paragraph(
                            "14. Large blocks help verify that the auto-scrolling during drag-and-drop works correctly even when the item is taller than the viewport."
                    ),
                    Block.paragraph("15. Just some more text to fill the space."),
                    Block.paragraph("16. Number sixteen."),
                    Block.paragraph("17. Almost at the end of our initial list."),
                    Block.paragraph("18. " + "Longer text for 18. ".repeat(5)),
                    Block.paragraph("19. One more to go."),
                    Block.paragraph(
                            "20. The final block in our initial set. Enjoy using CascadeEditor!"
                    )
            )
        }

        val editorState = rememberEditorState(initialBlocks)

        Column(
                modifier = Modifier.background(Color.White).safeContentPadding().fillMaxSize(),
        ) {
            Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Cascade Editor",
                    color = Color.Black,
                    fontSize = 40.sp,
                    textAlign = TextAlign.Center
            )
            Spacers.Vertical(40.dp)
            CascadeEditor(stateHolder = editorState, modifier = Modifier.fillMaxSize())
        }
    }
}
