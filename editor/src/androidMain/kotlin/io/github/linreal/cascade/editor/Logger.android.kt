package io.github.linreal.cascade.editor

import android.util.Log

public actual fun logd(message: String, tag: String) {
    Log.d(tag, message)
}

public actual fun loge(message: String, tag: String) {
    Log.e(tag, message)
}
