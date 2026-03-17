package io.github.linreal.cascade.editor.serialization

import io.github.linreal.cascade.editor.core.BlockContent
import kotlinx.serialization.json.JsonObject

/**
 * Consumer-provided codec for encoding/decoding custom [BlockContent] instances.
 *
 * Return `null` to fall through to the default encoding/decoding logic.
 */
public interface BlockContentCodec {
    /** Encode [BlockContent] to JSON. Return `null` to use default encoding. */
    public fun encodeContent(content: BlockContent): JsonObject?

    /** Decode [BlockContent] from JSON by its [kind]. Return `null` to use default decoding. */
    public fun decodeContent(kind: String, json: JsonObject): BlockContent?
}
