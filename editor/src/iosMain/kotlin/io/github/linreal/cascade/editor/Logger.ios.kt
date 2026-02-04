package io.github.linreal.cascade.editor

import platform.Foundation.NSLog

public actual fun logd(message: String, tag: String) {
    NSLog("D/$tag: $message")
}

public actual fun loge(message: String, tag: String) {
    NSLog("E/$tag: $message")
}
