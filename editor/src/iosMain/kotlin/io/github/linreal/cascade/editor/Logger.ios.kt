package io.github.linreal.cascade.editor

import platform.Foundation.NSLog

public actual fun logd(tag: String, message: String) {
    NSLog("D/$tag: $message")
}

public actual fun loge(tag: String, message: String) {
    NSLog("E/$tag: $message")
}
