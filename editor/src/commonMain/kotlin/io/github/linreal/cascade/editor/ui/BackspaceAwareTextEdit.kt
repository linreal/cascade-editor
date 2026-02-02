package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.loge
import kotlinx.coroutines.flow.collectLatest


// The invisible zero-width space character
private const val ZWSP = "\u200B"

/**
 * A TextField that can detect backspace at the start of the text.
 *
 * Uses a zero-width space sentinel at position 0 to detect when user
 * tries to delete at the beginning.
 *
 * @param modifier Modifier for the text field
 * @param initialText Initial text content (without sentinel)
 * @param textStyle Style for the text
 * @param focusRequester Optional FocusRequester for programmatic focus control
 * @param onTextChange Called when text changes with the new text (without sentinel) and cursor position
 * @param onBackspaceAtStart Called when backspace is pressed at the start of text
 * @param onEnterPressed Called when Enter is pressed, with cursor position (relative to visible text)
 */
@Composable
public fun BackspaceAwareTextField(
    modifier: Modifier = Modifier,
    initialText: String = "",
    textStyle: TextStyle = TextStyle(fontSize = 16.sp),
    focusRequester: FocusRequester? = null,
    onTextChange: (text: String, cursorPosition: Int) -> Unit = { _, _ -> },
    onBackspaceAtStart: () -> Unit,
    onEnterPressed: (cursorPosition: Int) -> Unit
) {
    val state = rememberTextFieldState(initialText = "$ZWSP$initialText")
    LaunchedEffect(initialText) {
        state.clearText()
        state.edit {
            append("$ZWSP$initialText")
        }
    }

    val sentinelGuard = remember {
        InputTransformation {
            // If the new text no longer starts with ZWSP, the user tried to delete it
            if (!asCharSequence().startsWith(ZWSP)) {
                onBackspaceAtStart()
                insert(0, ZWSP)
            }
        }
    }

    // Track text changes and report them (excluding the sentinel)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .collectLatest { fullText ->
                val visibleText = fullText.removePrefix(ZWSP)
                // Cursor position relative to visible text (subtract 1 for sentinel)
                val cursorPos = (state.selection.start - 1).coerceAtLeast(0)
                onTextChange(visibleText, cursorPos)
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

    val baseModifier = if (focusRequester != null) {
        modifier.focusRequester(focusRequester)
    } else {
        modifier
    }

    BasicTextField(
        state = state,
        inputTransformation = sentinelGuard,
        textStyle = textStyle,
        modifier = baseModifier,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Next
        ),
        onKeyboardAction = {
            // Report cursor position relative to visible text
            val cursorPos = (state.selection.start - 1).coerceAtLeast(0)
            onEnterPressed(cursorPos)
        },
    )
}

/**
 * Returns the visible text content (without the sentinel character).
 */
public fun TextFieldState.visibleText(): String {
    return text.toString().removePrefix(ZWSP)
}

/**
 * Returns the cursor position relative to visible text.
 */
public fun TextFieldState.visibleCursorPosition(): Int {
    return (selection.start - 1).coerceAtLeast(0)
}