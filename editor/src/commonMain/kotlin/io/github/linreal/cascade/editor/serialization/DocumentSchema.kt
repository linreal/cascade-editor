package io.github.linreal.cascade.editor.serialization

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.CustomBlockType
import io.github.linreal.cascade.editor.core.UnknownBlockType
import io.github.linreal.cascade.editor.core.normalizeIndentationOutlineWithReport
import io.github.linreal.cascade.editor.core.renumberNumberedLists
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Canonical JSON serialization for a list of [Block]s.
 *
 * JSON shape (version 2):
 * ```json
 * {
 *   "version": 2,
 *   "blocks": [
 *     {
 *       "id": "...",
 *       "type": { "typeId": "paragraph" },
 *       "attributes": { "indentationLevel": 0 },
 *       "content": { "kind": "text", "version": 1, "text": "...", "spans": [...] }
 *     }
 *   ]
 * }
 * ```
 */
public object DocumentSchema {

    public const val CURRENT_VERSION: Int = 2

    // Encode

    /**
     * Encodes a list of [Block]s to a versioned [JsonObject].
     */
    public fun encode(
        blocks: List<Block>,
        options: DocumentEncodeOptions = DocumentEncodeOptions(),
        typeCodec: BlockTypeCodec? = null,
        contentCodec: BlockContentCodec? = null,
    ): JsonObject = buildJsonObject {
        put("version", JsonPrimitive(CURRENT_VERSION))
        put("blocks", buildJsonArray {
            for (block in blocks) {
                add(encodeBlock(block, options, typeCodec, contentCodec))
            }
        })
    }

    /**
     * Encodes a list of [Block]s to a JSON string.
     */
    public fun encodeToString(
        blocks: List<Block>,
        options: DocumentEncodeOptions = DocumentEncodeOptions(),
        typeCodec: BlockTypeCodec? = null,
        contentCodec: BlockContentCodec? = null,
    ): String = encode(blocks, options, typeCodec, contentCodec).toString()

    // Decode

    /**
     * Decodes a [JsonObject] to a list of [Block]s, discarding warnings.
     */
    public fun decode(
        json: JsonObject,
        options: DocumentDecodeOptions = DocumentDecodeOptions(),
        typeCodec: BlockTypeCodec? = null,
        contentCodec: BlockContentCodec? = null,
    ): List<Block> = decodeWithReport(json, options, typeCodec, contentCodec).blocks

    /**
     * Decodes a JSON string to a list of [Block]s, discarding warnings.
     */
    public fun decodeFromString(
        jsonString: String,
        options: DocumentDecodeOptions = DocumentDecodeOptions(),
        typeCodec: BlockTypeCodec? = null,
        contentCodec: BlockContentCodec? = null,
    ): List<Block> = decode(Json.parseToJsonElement(jsonString).jsonObject, options, typeCodec, contentCodec)

    /**
     * Decodes a [JsonObject] to a [DocumentDecodeResult] with blocks and warnings.
     */
    public fun decodeWithReport(
        json: JsonObject,
        options: DocumentDecodeOptions = DocumentDecodeOptions(),
        typeCodec: BlockTypeCodec? = null,
        contentCodec: BlockContentCodec? = null,
    ): DocumentDecodeResult {
        val version = (json["version"] as? JsonPrimitive)?.intOrNull ?: 1
        if (version > CURRENT_VERSION) {
            throw IllegalArgumentException(
                "Unsupported document version $version (max supported: $CURRENT_VERSION)"
            )
        }

        val blocksArray = json["blocks"] as? JsonArray ?: return DocumentDecodeResult(emptyList(), emptyList())
        val warnings = mutableListOf<DocumentDecodeWarning>()
        val seenIds = mutableSetOf<String>()
        val blocks = mutableListOf<Block>()
        val blockSourceIndices = mutableListOf<Int>()

        for ((index, element) in blocksArray.withIndex()) {
            val blockJson = element as? JsonObject
            if (blockJson == null) {
                warnings.add(DocumentDecodeWarning.MalformedBlockSkipped(index, "Block entry is not a JSON object"))
                continue
            }
            val block = decodeBlock(blockJson, index, options, typeCodec, contentCodec, seenIds, warnings)
            if (block != null) {
                blocks.add(block)
                blockSourceIndices.add(index)
            }
        }

        val normalization = normalizeIndentationOutlineWithReport(blocks)
        for (blockIndex in normalization.changedBlockIndices) {
            val sourceBlockIndex = blockSourceIndices[blockIndex]
            warnings.add(
                DocumentDecodeWarning.InvalidBlockAttributeParam(
                    blockIndex = sourceBlockIndex,
                    param = "indentationLevel=${blocks[blockIndex].attributes.indentationLevel}",
                    fallback = "indentationLevel=${normalization.blocks[blockIndex].attributes.indentationLevel}",
                )
            )
        }

        val renumbered = renumberNumberedLists(normalization.blocks)
        return DocumentDecodeResult(renumbered, warnings)
    }

    /**
     * Decodes a JSON string to a [DocumentDecodeResult] with blocks and warnings.
     */
    public fun decodeFromStringWithReport(
        jsonString: String,
        options: DocumentDecodeOptions = DocumentDecodeOptions(),
        typeCodec: BlockTypeCodec? = null,
        contentCodec: BlockContentCodec? = null,
    ): DocumentDecodeResult = decodeWithReport(Json.parseToJsonElement(jsonString).jsonObject, options, typeCodec, contentCodec)

    // Private encode helpers

    private fun encodeBlock(
        block: Block,
        options: DocumentEncodeOptions,
        typeCodec: BlockTypeCodec?,
        contentCodec: BlockContentCodec?,
    ): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(block.id.value))
        put("type", encodeBlockType(block.type, typeCodec))
        encodeBlockAttributes(block)?.let { attributes ->
            put("attributes", attributes)
        }
        put("content", encodeBlockContent(block.content, options, contentCodec))
    }

    private fun encodeBlockAttributes(block: Block): JsonObject? {
        val indentationLevel = if (block.type.supportsIndentation) {
            block.attributes.indentationLevel
        } else {
            BlockAttributes.DEFAULT_INDENTATION_LEVEL
        }
        if (indentationLevel == BlockAttributes.DEFAULT_INDENTATION_LEVEL) return null

        return buildJsonObject {
            put("indentationLevel", JsonPrimitive(indentationLevel))
        }
    }

    private fun encodeBlockType(
        type: BlockType,
        typeCodec: BlockTypeCodec?,
    ): JsonObject {
        // Built-in types
        val builtIn = encodeBuiltInType(type)
        if (builtIn != null) return builtIn

        // Consumer codec
        if (typeCodec != null) {
            val codecResult = typeCodec.encodeType(type)
            if (codecResult != null) return codecResult
        }

        // UnknownBlockType: re-emit rawTypeJson verbatim
        if (type is UnknownBlockType) {
            return Json.parseToJsonElement(type.rawTypeJson).jsonObject
        }

        // Fallback for unhandled CustomBlockType
        return buildJsonObject {
            put("typeId", JsonPrimitive(type.typeId))
            put("custom", JsonPrimitive(true))
        }
    }

    private fun encodeBuiltInType(type: BlockType): JsonObject? = when (type) {
        is BlockType.Paragraph -> buildJsonObject {
            put("typeId", JsonPrimitive("paragraph"))
        }
        is BlockType.Heading -> buildJsonObject {
            put("typeId", JsonPrimitive("heading_${type.level}"))
        }
        is BlockType.Todo -> buildJsonObject {
            put("typeId", JsonPrimitive("todo"))
            put("checked", JsonPrimitive(type.checked))
        }
        is BlockType.BulletList -> buildJsonObject {
            put("typeId", JsonPrimitive("bullet_list"))
        }
        is BlockType.NumberedList -> buildJsonObject {
            put("typeId", JsonPrimitive("numbered_list"))
            put("number", JsonPrimitive(type.number))
        }
        is BlockType.Quote -> buildJsonObject {
            put("typeId", JsonPrimitive("quote"))
        }
        is BlockType.Divider -> buildJsonObject {
            put("typeId", JsonPrimitive("divider"))
        }

        is CustomBlockType -> null // handled by codec / UnknownBlockType / fallback paths
    }

    private fun encodeBlockContent(
        content: BlockContent,
        options: DocumentEncodeOptions,
        contentCodec: BlockContentCodec?,
    ): JsonObject {
        // Built-in content kinds
        val builtIn = encodeBuiltInContent(content, options)
        if (builtIn != null) return builtIn

        // Consumer codec
        if (contentCodec != null) {
            val codecResult = contentCodec.encodeContent(content)
            if (codecResult != null) return codecResult
        }

        // BlockContent.Custom fallback
        if (content is BlockContent.Custom) {
            return buildJsonObject {
                put("kind", JsonPrimitive(content.typeId))
                put("data", encodeDataMap(content.data, options))
            }
        }

        // Should not reach here for built-in types, but safety fallback
        return buildJsonObject { put("kind", JsonPrimitive("empty")) }
    }

    private fun encodeBuiltInContent(
        content: BlockContent,
        options: DocumentEncodeOptions,
    ): JsonObject? = when (content) {
        is BlockContent.Text -> {
            val richTextJson = RichTextSchema.encode(content)
            buildJsonObject {
                put("kind", JsonPrimitive("text"))
                for ((key, value) in richTextJson) {
                    put(key, value)
                }
            }
        }

        is BlockContent.Empty -> buildJsonObject {
            put("kind", JsonPrimitive("empty"))
        }
        is BlockContent.Custom -> null // handled in encodeBlockContent fallback
    }

    private fun encodeDataMap(
        data: Map<String, Any?>,
        options: DocumentEncodeOptions,
    ): JsonObject = buildJsonObject {
        for ((key, value) in data) {
            val encoded = encodeDataValue(value, options, key)
            if (encoded != null) {
                put(key, encoded)
            }
        }
    }

    private fun encodeDataValue(
        value: Any?,
        options: DocumentEncodeOptions,
        key: String,
    ): kotlinx.serialization.json.JsonElement? = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is List<*> -> buildJsonArray {
            for (item in value) {
                val encoded = encodeDataValue(item, options, key)
                if (encoded != null) {
                    add(encoded)
                }
            }
        }
        is Map<*, *> -> {
            if (value.keys.all { it is String }) {
                @Suppress("UNCHECKED_CAST")
                encodeDataMap(value as Map<String, Any?>, options)
            } else {
                when (options.customDataMode) {
                    CustomDataMode.Strict -> throw IllegalArgumentException(
                        "Map with non-String keys for key '$key' in custom data map"
                    )
                    CustomDataMode.LenientSkipUnsupported -> null
                }
            }
        }
        else -> when (options.customDataMode) {
            CustomDataMode.Strict -> throw IllegalArgumentException(
                "Unsupported value type '${value::class.simpleName}' for key '$key' in custom data map"
            )
            CustomDataMode.LenientSkipUnsupported -> null
        }
    }

    // Private decode helpers

    private fun decodeBlock(
        json: JsonObject,
        blockIndex: Int,
        options: DocumentDecodeOptions,
        typeCodec: BlockTypeCodec?,
        contentCodec: BlockContentCodec?,
        seenIds: MutableSet<String>,
        warnings: MutableList<DocumentDecodeWarning>,
    ): Block? {
        // Validate type before resolving ID so skipped blocks don't pollute seenIds
        val typeJson = json["type"] as? JsonObject
        if (typeJson == null) {
            warnings.add(DocumentDecodeWarning.MalformedBlockSkipped(blockIndex, "Missing 'type' object"))
            return null
        }
        val typeId = typeJson.stringOrNull("typeId")
        if (typeId == null) {
            warnings.add(DocumentDecodeWarning.MalformedBlockSkipped(blockIndex, "Missing 'typeId' in type object"))
            return null
        }
        val blockType = decodeBlockType(typeId, typeJson, blockIndex, typeCodec, warnings)
        val attributes = decodeBlockAttributes(json, blockIndex, blockType, warnings)

        // Decode content (before ID, so content failures don't pollute seenIds either)
        val contentJson = json["content"] as? JsonObject
        val content = if (contentJson != null) {
            decodeBlockContent(contentJson, blockIndex, contentCodec, warnings) ?: return null
        } else {
            BlockContent.Empty
        }

        // Resolve ID only after type+content validation succeeds
        val id = resolveBlockId(json, blockIndex, options, seenIds, warnings) ?: return null

        return Block(id = id, type = blockType, content = content, attributes = attributes)
    }

    private fun decodeBlockAttributes(
        json: JsonObject,
        blockIndex: Int,
        blockType: BlockType,
        warnings: MutableList<DocumentDecodeWarning>,
    ): BlockAttributes {
        val indentationLevel = decodeIndentationLevel(
            attributesElement = json["attributes"],
            blockIndex = blockIndex,
            warnings = warnings,
        )
        if (!blockType.supportsIndentation) {
            return BlockAttributes.Default
        }
        return BlockAttributes(indentationLevel = indentationLevel)
    }

    private fun decodeIndentationLevel(
        attributesElement: JsonElement?,
        blockIndex: Int,
        warnings: MutableList<DocumentDecodeWarning>,
    ): Int {
        if (attributesElement == null) return BlockAttributes.DEFAULT_INDENTATION_LEVEL

        val attributesJson = attributesElement as? JsonObject
        if (attributesJson == null) {
            warnings.add(
                DocumentDecodeWarning.InvalidBlockAttributeParam(
                    blockIndex = blockIndex,
                    param = "attributes=${attributesElement.warningContent()}",
                    fallback = "indentationLevel=${BlockAttributes.DEFAULT_INDENTATION_LEVEL}",
                )
            )
            return BlockAttributes.DEFAULT_INDENTATION_LEVEL
        }

        val indentationElement = attributesJson["indentationLevel"]
            ?: return BlockAttributes.DEFAULT_INDENTATION_LEVEL
        val indentationPrimitive = indentationElement as? JsonPrimitive
        val indentationLevel = if (indentationPrimitive != null && !indentationPrimitive.isString) {
            indentationPrimitive.intOrNull
        } else {
            null
        }

        if (indentationLevel == null ||
            indentationLevel !in BlockAttributes.MIN_INDENTATION_LEVEL..BlockAttributes.MAX_INDENTATION_LEVEL
        ) {
            warnings.add(
                DocumentDecodeWarning.InvalidBlockAttributeParam(
                    blockIndex = blockIndex,
                    param = "indentationLevel=${indentationElement.warningContent()}",
                    fallback = "indentationLevel=${BlockAttributes.DEFAULT_INDENTATION_LEVEL}",
                )
            )
            return BlockAttributes.DEFAULT_INDENTATION_LEVEL
        }

        return indentationLevel
    }

    private fun resolveBlockId(
        json: JsonObject,
        blockIndex: Int,
        options: DocumentDecodeOptions,
        seenIds: MutableSet<String>,
        warnings: MutableList<DocumentDecodeWarning>,
    ): BlockId? {
        if (options.idMode == BlockIdMode.Regenerate) {
            return BlockId.generate()
        }

        // Preserve mode
        val rawId = json.stringOrNull("id")
        if (rawId.isNullOrEmpty()) {
            warnings.add(DocumentDecodeWarning.MissingIdRegenerated(blockIndex))
            return BlockId.generate()
        }

        if (rawId in seenIds) {
            when (options.duplicateIdMode) {
                DuplicateIdMode.FailFast -> throw IllegalArgumentException(
                    "Duplicate block ID '$rawId' at index $blockIndex"
                )
                DuplicateIdMode.RegenerateLaterDuplicates -> {
                    val newId = BlockId.generate()
                    warnings.add(DocumentDecodeWarning.DuplicateIdRegenerated(blockIndex, rawId, newId.value))
                    seenIds.add(newId.value)
                    return newId
                }
            }
        }

        seenIds.add(rawId)
        return BlockId(rawId)
    }

    private fun decodeBlockType(
        typeId: String,
        typeJson: JsonObject,
        blockIndex: Int,
        typeCodec: BlockTypeCodec?,
        warnings: MutableList<DocumentDecodeWarning>,
    ): BlockType {
        // Built-in types
        val builtIn = decodeBuiltInType(typeId, typeJson, blockIndex, warnings)
        if (builtIn != null) return builtIn

        // Consumer codec
        if (typeCodec != null) {
            val codecResult = typeCodec.decodeType(typeId, typeJson)
            if (codecResult != null) return codecResult
        }

        // Fallback: unknown type
        warnings.add(DocumentDecodeWarning.UnknownBlockTypePreserved(blockIndex, typeId))
        return UnknownBlockType(typeId = typeId, rawTypeJson = typeJson.toString())
    }

    private fun decodeBuiltInType(
        typeId: String,
        typeJson: JsonObject,
        blockIndex: Int,
        warnings: MutableList<DocumentDecodeWarning>,
    ): BlockType? {
        // Handle heading_N pattern
        if (typeId.startsWith("heading_")) {
            val suffix = typeId.removePrefix("heading_")
            val level = suffix.toIntOrNull()
            if (level == null || level !in 1..6) {
                warnings.add(
                    DocumentDecodeWarning.InvalidBlockTypeParam(
                        blockIndex, typeId, "level=$suffix", "level=1"
                    )
                )
                return BlockType.Heading(1)
            }
            return BlockType.Heading(level)
        }

        return when (typeId) {
            "paragraph" -> BlockType.Paragraph
            "todo" -> {
                val checked = typeJson.boolOrNull("checked") ?: false
                BlockType.Todo(checked)
            }
            "bullet_list" -> BlockType.BulletList
            "numbered_list" -> {
                val number = typeJson.intOrNull("number") ?: 1
                if (number < 1) {
                    warnings.add(
                        DocumentDecodeWarning.InvalidBlockTypeParam(
                            blockIndex, typeId, "number=$number", "number=1"
                        )
                    )
                    BlockType.NumberedList(1)
                } else {
                    BlockType.NumberedList(number)
                }
            }
            "quote" -> BlockType.Quote
            "divider" -> BlockType.Divider
            else -> null
        }
    }

    /**
     * Decodes block content from JSON. Returns `null` when the content failure
     * should cause the entire block to be skipped (e.g., image missing URI).
     */
    private fun decodeBlockContent(
        json: JsonObject,
        blockIndex: Int,
        contentCodec: BlockContentCodec?,
        warnings: MutableList<DocumentDecodeWarning>,
    ): BlockContent? {
        val kind = json.stringOrNull("kind") ?: return BlockContent.Empty

        // Built-in kinds
        when (kind) {
            "text" -> return RichTextSchema.decode(json)
            "empty" -> return BlockContent.Empty
            "custom" -> {
                // Legacy shape: kind=custom with separate typeId
                val customTypeId = json.stringOrNull("typeId") ?: "custom"
                val data = json["data"]?.let { decodeDataMap(it as? JsonObject ?: return@let emptyMap()) } ?: emptyMap()
                return BlockContent.Custom(typeId = customTypeId, data = data)
            }
        }

        // Consumer codec
        if (contentCodec != null) {
            val codecResult = contentCodec.decodeContent(kind, json)
            if (codecResult != null) return codecResult
        }

        // Fallback: unknown kind → Custom
        warnings.add(DocumentDecodeWarning.UnknownContentKind(blockIndex, kind))
        val data = json["data"]?.let { decodeDataMap(it as? JsonObject ?: return@let emptyMap()) } ?: emptyMap()
        return BlockContent.Custom(typeId = kind, data = data)
    }

    private fun decodeDataMap(json: JsonObject): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        for ((key, value) in json) {
            result[key] = decodeDataValue(value)
        }
        return result
    }

    private fun decodeDataValue(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> {
            when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.booleanOrNull
                element.longOrNull != null -> element.longOrNull
                element.doubleOrNull != null -> element.doubleOrNull
                else -> element.content
            }
        }
        is JsonArray -> element.map { decodeDataValue(it) }
        is JsonObject -> decodeDataMap(element)
    }

    // Safe JSON accessors

    private fun JsonElement.warningContent(): String = when (this) {
        is JsonPrimitive -> content
        else -> toString()
    }

    /**
     * Safely extracts a string from a JSON element that may not be a primitive.
     * Returns null if the element is missing, is not a primitive, or has no string content.
     */
    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.content

    private fun JsonObject.boolOrNull(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.intOrNull(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull
}
