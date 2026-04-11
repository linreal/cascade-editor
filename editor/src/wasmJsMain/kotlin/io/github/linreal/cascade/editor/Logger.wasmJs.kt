package io.github.linreal.cascade.editor

internal actual fun logd(message: String, tag: String) {
    println("D/$tag: $message")
}

internal actual fun loge(message: String, tag: String) {
    println("E/$tag: $message")
}
