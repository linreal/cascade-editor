package io.github.linreal.cascade.editor

import android.os.Build

internal actual fun getPlatformName(): String = "Android ${Build.VERSION.SDK_INT}"
