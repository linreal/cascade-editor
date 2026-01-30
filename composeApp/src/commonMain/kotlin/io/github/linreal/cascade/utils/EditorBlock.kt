package io.github.linreal.cascade.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.loge
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// A stable identity for each block
class EditorBlock @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String = Uuid.random().toString(),
    initialText: String = ""
) {
    // TextFieldState is mutable and holds both text and selection
    val textState = TextFieldState(initialText)
    val focusRequester = FocusRequester()
}


class EditorState {
    val blocks = mutableStateListOf(EditorBlock())

    fun onEnter(currentIndex: Int) {
        val currentBlock = blocks[currentIndex]

        // We perform the split logic inside an atomic 'edit' block
        currentBlock.textState.edit {
            // 1. Capture the text after the cursor
            val cursorPosition = selection.start
            val textAfterCursor = asCharSequence().subSequence(cursorPosition, length)

            // 2. Create the new block with that text
            val newBlock = EditorBlock(initialText = textAfterCursor.toString())

            // 3. Delete the text after cursor in the current block
            replace(cursorPosition, length, "")

            // 4. Insert the new block into the list
            blocks.add(currentIndex + 1, newBlock)
        }
    }

    fun onBackspace(currentIndex: Int) {
        if (currentIndex <= 0) return

        val currentBlock = blocks[currentIndex]
        val previousBlock = blocks[currentIndex - 1]

        // Atomic merge: modify previous block, then remove current
        previousBlock.textState.edit {
            val appendIndex = length
            val textToAppend = currentBlock.textState.text

            // 1. Append text from the current block
            append(textToAppend)

            // 2. Move cursor to the merge point
            selection = TextRange(appendIndex)
        }

        // 3. Remove the current block
        blocks.removeAt(currentIndex)
    }
}

private val colorsList = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow)
private const val TAG = "StateBasedBlockEditor"

@Composable
fun StateBasedBlockEditor() {
    val editorState = remember { EditorState() }

    // Helper to track the last added block to trigger focus
    var focusedBlockId by remember { mutableStateOf<String?>(null) }
    loge(TAG, "StateBasedBlockEditor start")
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        itemsIndexed(editorState.blocks, key = { _, block -> block.id }) { index, block ->

            // Focus Management Side-Effect
            LaunchedEffect(block.id, focusedBlockId) {
                if (focusedBlockId == block.id) {
                    block.focusRequester.requestFocus()
                    focusedBlockId = null // Reset trigger
                }
            }
            val bgColor = remember { colorsList.random() }
            var previousText by remember { mutableStateOf(block.textState.text.toString()) }
            var previousSelection by remember { mutableStateOf(block.textState.selection) }

            LaunchedEffect(block.textState) {
                snapshotFlow { block.textState.text.toString() to block.textState.selection }
                    .collect { (currentText, currentSelection) ->

                        val textDeleted = currentText.length < previousText.length
                        val cursorAtStart = currentSelection.start == 0 && currentSelection.collapsed
                        val wasAtStart = previousSelection.start == 0
                        loge(TAG, "Text changed $currentText, $previousText textDeleted: $textDeleted, cursorAtStart: $cursorAtStart wasAtStart: $wasAtStart")

                        // Backspace at start of empty block
                        if (textDeleted && cursorAtStart && wasAtStart && currentText.isEmpty()) {
                            val prevBlockId = editorState.blocks[index - 1].id
                            editorState.onBackspace(index)
                            // Mark the PREVIOUS block to be focused
                            focusedBlockId = prevBlockId
                        }

                        previousText = currentText
                        previousSelection = currentSelection
                    }
            }
            BasicTextField(
                state = block.textState,
                textStyle = TextStyle(fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                onKeyboardAction = {
                    loge(TAG, "Enter pressed")
                    editorState.onEnter(index)
                    focusedBlockId = editorState.blocks[index + 1].id
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(vertical = 4.dp)
                    .focusRequester(block.focusRequester)
            )
        }
    }
}


@Composable
internal fun DetectableBackspace() {
    val state = rememberTextFieldState()
    var previousText by remember { mutableStateOf(state.text.toString()) }
    var previousSelection by remember { mutableStateOf(state.selection) }

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() to state.selection }
            .collect { (currentText, currentSelection) ->
                val textDeleted = currentText.length < previousText.length
                val cursorAtStart = currentSelection.start == 0 && currentSelection.collapsed
                val wasAtStart = previousSelection.start == 0
                loge(
                    TAG,
                    "Text changed $currentText, $previousText textDeleted: $textDeleted, cursorAtStart: $cursorAtStart wasAtStart: $wasAtStart"
                )

                // Backspace at start of empty block
                if (textDeleted && cursorAtStart && wasAtStart && currentText.isEmpty()) {
                    // Success! Handle backspace at start!!!!
                }

                previousText = currentText
                previousSelection = currentSelection
            }
    }

    BasicTextField(
        state = state,
        textStyle = TextStyle(fontSize = 16.sp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}