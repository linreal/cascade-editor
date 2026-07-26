package io.github.linreal.cascade.ios.controller

import io.github.linreal.cascade.editor.serialization.DocumentDecodeWarning

/**
 * Keeps Swift-facing document warning text identical across editable and
 * preview controllers.
 */
internal fun DocumentDecodeWarning.toCascadeWarningMessage(): String = when (this) {
    is DocumentDecodeWarning.DocumentParseFailed -> "Document parse failed: $reason"
    is DocumentDecodeWarning.DuplicateIdRegenerated ->
        "Duplicate block id '$originalId' at block $blockIndex was replaced with '$newId'."

    is DocumentDecodeWarning.MissingIdRegenerated ->
        "Missing block id at block $blockIndex was replaced."

    is DocumentDecodeWarning.InvalidBlockAttributeParam ->
        "Invalid block attribute at block $blockIndex: $param; using $fallback."

    is DocumentDecodeWarning.InvalidBlockTypeParam ->
        "Invalid '$param' for block type '$typeId' at block $blockIndex; using $fallback."

    is DocumentDecodeWarning.MalformedBlockSkipped ->
        "Malformed block at index $blockIndex was skipped: $reason."

    is DocumentDecodeWarning.UnknownBlockTypePreserved ->
        "Unknown block type '$typeId' at block $blockIndex was preserved."

    is DocumentDecodeWarning.UnknownContentKind ->
        "Unknown content kind '$kind' at block $blockIndex was preserved as custom content."

    is DocumentDecodeWarning.UnsupportedCustomDataDropped ->
        "Unsupported custom value '$key' at block $blockIndex was dropped: $valueType."
}
