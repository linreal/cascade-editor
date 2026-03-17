package io.github.linreal.cascade.editor.serialization

import io.github.linreal.cascade.editor.core.BlockType
import kotlinx.serialization.json.JsonObject

/**
 * Consumer-provided codec for encoding/decoding custom [BlockType] instances.
 *
 * Return `null` to fall through to the default encoding/decoding logic.
 */
public interface BlockTypeCodec {
    /** Encode a [BlockType] to JSON. Return `null` to use default encoding. */
    public fun encodeType(type: BlockType): JsonObject?

    /** Decode a [BlockType] from JSON by its [typeId]. Return `null` to use default decoding. */
    public fun decodeType(typeId: String, json: JsonObject): BlockType?
}
