package io.github.linreal.cascade.editor.serialization

/**
 * Options for [DocumentSchema.encode] / [DocumentSchema.encodeToString].
 */
public data class DocumentEncodeOptions(
    val customDataMode: CustomDataMode = CustomDataMode.Strict,
)
