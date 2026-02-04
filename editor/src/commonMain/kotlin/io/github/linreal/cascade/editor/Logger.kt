package io.github.linreal.cascade.editor

/**
 * Logs a debug message.
 */
public expect fun logd(message: String, tag: String = "CascadeEditor")

/**
 * Logs an error message.
 */
public expect fun loge(message: String, tag: String = "CascadeEditor")
