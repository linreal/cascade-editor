package io.github.linreal.cascade.editor.core

/**
 * Represents a block type that was not recognized during document decode.
 *
 * The original type JSON is preserved as a raw string so it can be re-emitted
 * verbatim during encode, enabling lossless round-tripping of unknown types.
 *
 * This class intentionally does NOT depend on `kotlinx.serialization.json` types —
 * the serialization layer owns the parse/stringify conversion at the boundary.
 *
 * @property rawTypeJson The original type JSON object as a raw string.
 */
public data class UnknownBlockType(
    override val typeId: String,
    val rawTypeJson: String,
) : CustomBlockType {
    override val displayName: String = "Unknown ($typeId)"
    override val supportsText: Boolean = false
    override val isConvertible: Boolean = false
}
