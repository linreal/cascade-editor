package io.github.linreal.cascade.editor

import platform.UIKit.UIDevice

internal actual fun getPlatformName(): String =
    UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
