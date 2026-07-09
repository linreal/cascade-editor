package io.github.linreal.cascade.editor

import androidx.compose.foundation.text.KeyboardOptions

/**
 * `true` when running on iOS, `false` otherwise.
 */
internal expect val isIos: Boolean

/** Creates keyboard options with platform-specific text-input behavior. */
internal expect fun platformKeyboardOptions(useNativeIosTextInput: Boolean): KeyboardOptions
