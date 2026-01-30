package io.github.linreal.cascade.editor

import android.util.Log

public actual fun logd(tag: String, message: String) {
    Log.d(tag, message)
}

public actual fun loge(tag: String, message: String) {
    Log.e(tag, message)
}
