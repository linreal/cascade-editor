package io.github.linreal.cascade.editor

import android.util.Log

internal actual fun logd(message: String, tag: String) {
    Log.d(tag, message)
}

internal actual fun loge(message: String, tag: String) {
    Log.e(tag, message)
}
