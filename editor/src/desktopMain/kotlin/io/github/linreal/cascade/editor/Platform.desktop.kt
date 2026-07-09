package io.github.linreal.cascade.editor

import androidx.compose.foundation.text.KeyboardOptions

internal actual val isIos: Boolean = false

internal actual fun platformKeyboardOptions(useNativeIosTextInput: Boolean): KeyboardOptions {
    return KeyboardOptions.Default
}
