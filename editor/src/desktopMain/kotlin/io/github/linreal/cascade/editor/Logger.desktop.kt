package io.github.linreal.cascade.editor

import java.util.logging.Level
import java.util.logging.Logger

internal actual fun logd(message: String, tag: String) {
    Logger.getLogger(tag).log(Level.INFO, message)
}

internal actual fun loge(message: String, tag: String) {
    Logger.getLogger(tag).log(Level.SEVERE, message)
}
