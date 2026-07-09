package io.github.linreal.cascade.ios.block

import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.serialization.BlockTypeCodec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Bridges registered native custom block ids across the document serializer.
 *
 * [isRegistered] is queried live (not captured), so blocks registered after this
 * codec is wired into the load path still decode to a renderable
 * [NativeCustomBlockType]. Unregistered ids return `null` from [decodeType] and
 * fall through to the serializer's `UnknownBlockType` preservation.
 */
internal class NativeCustomBlockCodec(
    private val isRegistered: (String) -> Boolean,
) : BlockTypeCodec {

    override fun encodeType(type: BlockType): JsonObject? {
        if (type !is NativeCustomBlockType) return null
        return buildJsonObject { put("typeId", JsonPrimitive(type.typeId)) }
    }

    override fun decodeType(typeId: String, json: JsonObject): BlockType? {
        if (!isRegistered(typeId)) return null
        return NativeCustomBlockType(typeId)
    }
}
