package io.github.linreal.cascade.editor.serialization

/**
 * Warnings emitted during document decode.
 *
 * `blockIndex` values refer to the original JSON `blocks` array index, not the
 * decoded block-list position after malformed entries have been skipped.
 *
 * This is a sealed class — consumers should include an `else` branch in `when` expressions
 * to handle future warning subclasses gracefully.
 */
public sealed class DocumentDecodeWarning {

    /** Unknown block type preserved as [io.github.linreal.cascade.editor.core.UnknownBlockType]. */
    public data class UnknownBlockTypePreserved(
        val blockIndex: Int,
        val typeId: String,
    ) : DocumentDecodeWarning()

    /** Unknown content kind decoded as [io.github.linreal.cascade.editor.core.BlockContent.Custom]. */
    public data class UnknownContentKind(
        val blockIndex: Int,
        val kind: String,
    ) : DocumentDecodeWarning()

    /** Duplicate block ID was replaced with a freshly generated one. */
    public data class DuplicateIdRegenerated(
        val blockIndex: Int,
        val originalId: String,
        val newId: String,
    ) : DocumentDecodeWarning()

    /** Empty or missing block ID was generated. */
    public data class MissingIdRegenerated(
        val blockIndex: Int,
    ) : DocumentDecodeWarning()

    /** Block had invalid required fields and was skipped. */
    public data class MalformedBlockSkipped(
        val blockIndex: Int,
        val reason: String,
    ) : DocumentDecodeWarning()

    /** Lenient mode dropped an unsupported custom data value. */
    public data class UnsupportedCustomDataDropped(
        val blockIndex: Int,
        val key: String,
        val valueType: String,
    ) : DocumentDecodeWarning()

    /** A block type parameter was invalid and a fallback was used. */
    public data class InvalidBlockTypeParam(
        val blockIndex: Int,
        val typeId: String,
        val param: String,
        val fallback: String,
    ) : DocumentDecodeWarning()

    /** A block attribute parameter was invalid and a fallback was used. */
    public data class InvalidBlockAttributeParam(
        val blockIndex: Int,
        val param: String,
        val fallback: String,
    ) : DocumentDecodeWarning()

    /** The JSON document could not be parsed or its version was unsupported; load aborted with an empty result. */
    public data class DocumentParseFailed(
        val reason: String,
    ) : DocumentDecodeWarning()
}
