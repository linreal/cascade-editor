package io.github.linreal.cascade.editor

import platform.Foundation.NSLog

internal actual fun logd(message: String, tag: String) {
    NSLog("D/$tag: $message")
}

internal actual fun loge(message: String, tag: String) {
    NSLog("E/$tag: $message")
}
