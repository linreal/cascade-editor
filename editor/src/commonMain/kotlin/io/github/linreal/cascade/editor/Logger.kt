package io.github.linreal.cascade.editor

/**
 * Logs a debug message.
 */
internal expect fun logd(message: String, tag: String = "CascadeEditor")

/**
 * Logs an error message.
 */
internal expect fun loge(message: String, tag: String = "CascadeEditor")
