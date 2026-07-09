package io.github.linreal.cascade.editor

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.PlatformImeOptions

internal actual val isIos: Boolean = true

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun platformKeyboardOptions(useNativeIosTextInput: Boolean): KeyboardOptions {
    if (!useNativeIosTextInput) return KeyboardOptions.Default
    return KeyboardOptions(
        platformImeOptions = PlatformImeOptions {
            // TODO: enable after fix of https://youtrack.jetbrains.com/projects/CMP/issues/CMP-10404/iOS-keyboard-dismisses-and-re-presents-yoyos-when-focus-moves-between-TextFields-with-usingNativeTextInputtrue
            usingNativeTextInput(false)
        },
    )
}
