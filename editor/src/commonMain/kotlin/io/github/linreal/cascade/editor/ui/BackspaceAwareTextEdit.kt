package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp


// The invisible zero-width space character used as a sentinel
private const val ZWSP = "\u200B"

/**
 * A TextField that can detect backspace at the start of the text.
 *
 * Uses a zero-width space sentinel at position 0 to detect when user
 * tries to delete at the beginning.
 *
 * This component receives an externally managed [TextFieldState], making
 * it compatible with the hoisted state architecture where [BlockTextStates]
 * manages all text states centrally.
 *
 * @param state The TextFieldState to use (managed externally via BlockTextStates)
 * @param modifier Modifier for the text field
 * @param textStyle Style for the text
 * @param focusRequester Optional FocusRequester for programmatic focus control
 * @param onBackspaceAtStart Called when backspace is pressed at the start of text
 * @param onEnterPressed Called when Enter is pressed, with cursor position (relative to visible text)
 */
@Composable
public fun BackspaceAwareTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle(fontSize = 16.sp),
    focusRequester: FocusRequester? = null,
    onBackspaceAtStart: () -> Unit,
    onEnterPressed: (cursorPosition: Int) -> Unit,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
) {
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
        onTextLayout = onTextLayout ,
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
