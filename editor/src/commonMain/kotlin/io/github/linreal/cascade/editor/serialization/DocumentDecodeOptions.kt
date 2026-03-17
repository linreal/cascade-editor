package io.github.linreal.cascade.editor.serialization

/**
 * Options for [DocumentSchema.decode] / [DocumentSchema.decodeFromString].
 */
public data class DocumentDecodeOptions(
    val idMode: BlockIdMode = BlockIdMode.Preserve,
    val duplicateIdMode: DuplicateIdMode = DuplicateIdMode.RegenerateLaterDuplicates,
)
