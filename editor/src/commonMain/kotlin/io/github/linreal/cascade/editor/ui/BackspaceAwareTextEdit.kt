package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp


// The invisible zero-width space character
private const val ZWSP = "\u200B"

/*
 * It's impossible to 100% correctly detect when user press backspace at the start of the TextField
 * So, we insert invisible space at the beginning
 */
@Composable
public fun BackspaceAwareTextField(
    modifier: Modifier = Modifier,
    initialText: String = "",
    onBackspaceAtStart: () -> Unit
) {
    val state = rememberTextFieldState(initialText = "$ZWSP$initialText")

    val sentinelGuard = remember {
        InputTransformation {
            // If the new text no longer starts with ZWSP, the user tried to delete it
            if (!asCharSequence().startsWith(ZWSP)) {
                onBackspaceAtStart()
                insert(0, ZWSP)
            }
        }
    }

    // If the cursor is at 0, standard typing would insert BEFORE invisible space
    LaunchedEffect(state.selection) {
        if (state.selection.start == 0 && state.selection.collapsed) {
            state.edit {
                selection = TextRange(1)
            }
        }
    }

    BasicTextField(
        state = state,
        inputTransformation = sentinelGuard,
        textStyle = TextStyle(fontSize = 16.sp),
        modifier = modifier
    )
}