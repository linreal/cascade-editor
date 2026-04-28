package io.github.linreal.cascade.editor.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp


// The invisible zero-width space character used as a sentinel
private const val ZWSP = "\u200B"
private const val ZWSP_CHAR: Char = '\u200B'

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
 * @param cursorBrush Brush for the text cursor. Defaults to the text style color.
 * @param focusRequester Optional FocusRequester for programmatic focus control
 * @param onBackspaceAtStart Called when backspace is pressed at the start of text
 * @param onEnterPressed Called when Enter is pressed, with cursor position (relative to visible text)
 */
@Composable
public fun BackspaceAwareTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle(fontSize = 16.sp),
    cursorBrush: Brush = SolidColor(textStyle.color),
    outputTransformation: OutputTransformation? = null,
    focusRequester: FocusRequester? = null,
    onBackspaceAtStart: () -> Unit,
    onEnterPressed: (cursorPosition: Int) -> Unit,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
) {
    // Capture the latest callback so the input transformation, which is
    // remembered without keys, never invokes a stale lambda after recomposition.
    val currentOnBackspaceAtStart by rememberUpdatedState(onBackspaceAtStart)

    val sentinelGuard = remember {
        InputTransformation {
            when (val action = classifySentinelChange(originalText, asCharSequence())) {
                SentinelGuardAction.NoOp -> Unit
                SentinelGuardAction.DeletionAtStart -> {
                    // ZWSP at position 0 was deleted (Backspace at start, or a
                    // selection that included it was replaced).
                    currentOnBackspaceAtStart()
                    insert(0, ZWSP)
                }
                is SentinelGuardAction.RestoreSentinel -> {
                    // Cursor was at raw position 0 (before the sentinel) and the
                    // user typed/pasted before it. The intent is to insert at the
                    // start of the visible text, NOT to delete the block —
                    // restore ZWSP to position 0 without firing the callback.
                    if (action.zwspIndex > 0) {
                        replace(action.zwspIndex, action.zwspIndex + 1, "")
                    }
                    if (!asCharSequence().startsWith(ZWSP)) {
                        insert(0, ZWSP)
                    }
                }
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

    // Hardware Backspace at raw position 0 produces no text mutation (nothing
    // sits to the left of the cursor), so the InputTransformation never fires.
    // The cursor-pushback LaunchedEffect runs a frame later and can lose its
    // race with rapid drag-then-Backspace, leaving the cursor stranded at 0.
    // Catch this case explicitly so the callback fires regardless of timing.
    val keyGuardModifier = baseModifier.onPreviewKeyEvent { keyEvent ->
        if (
            keyEvent.type == KeyEventType.KeyDown &&
            keyEvent.key == Key.Backspace &&
            state.selection.start == 0 &&
            state.selection.collapsed
        ) {
            currentOnBackspaceAtStart()
            true
        } else {
            false
        }
    }

    BasicTextField(
        state = state,
        inputTransformation = sentinelGuard,
        outputTransformation = outputTransformation,
        textStyle = textStyle,
        cursorBrush = cursorBrush,
        modifier = keyGuardModifier,
        onTextLayout = onTextLayout,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Next,
        ),
        onKeyboardAction = {
            // Report cursor position relative to visible text
            val cursorPos = (state.selection.start - 1).coerceAtLeast(0)
            onEnterPressed(cursorPos)
        },
    )
}

/**
 * Classification of how the buffer changed relative to the ZWSP sentinel
 * invariant. Pure function so the decision logic can be unit-tested without
 * standing up a full Compose runtime.
 */
internal sealed class SentinelGuardAction {
    /** Buffer still starts with ZWSP — invariant holds, nothing to do. */
    internal data object NoOp : SentinelGuardAction()
    /** ZWSP was deleted; the user pressed Backspace at the start. */
    internal data object DeletionAtStart : SentinelGuardAction()
    /** ZWSP shifted off position 0 because text was inserted before it. */
    internal data class RestoreSentinel(val zwspIndex: Int) : SentinelGuardAction()
}

/**
 * Decides whether a buffer change represents a real Backspace-at-start
 * (sentinel was deleted) versus an accidental insertion before the sentinel
 * (cursor was at raw position 0 when the user typed). Distinguishing the two
 * prevents typing-after-failed-Backspace from spuriously firing the callback.
 *
 * @param originalText Buffer contents before the change; expected to start with ZWSP.
 * @param newText Buffer contents after the user's edit.
 */
internal fun classifySentinelChange(
    originalText: CharSequence,
    newText: CharSequence,
): SentinelGuardAction {
    if (newText.isNotEmpty() && newText[0] == ZWSP_CHAR) return SentinelGuardAction.NoOp
    if (newText.length < originalText.length) return SentinelGuardAction.DeletionAtStart
    val zwspIndex = indexOfZwsp(newText)
    return SentinelGuardAction.RestoreSentinel(zwspIndex)
}

private fun indexOfZwsp(text: CharSequence): Int {
    for (i in 0 until text.length) {
        if (text[i] == ZWSP_CHAR) return i
    }
    return -1
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

/**
 * Returns the selection range in visible text coordinates (sentinel offset removed).
 * Both start and end are adjusted by -1 for the ZWSP sentinel, clamped to 0.
 */
public fun TextFieldState.visibleSelection(): TextRange {
    val start = (selection.start - 1).coerceAtLeast(0)
    val end = (selection.end - 1).coerceAtLeast(0)
    return TextRange(start, end)
}
