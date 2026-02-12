package io.github.linreal.cascade.editor.serialization

import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Canonical JSON serialization for [BlockContent.Text] with rich text spans.
 *
 * JSON shape (version 1):
 * ```json
 * {
 *   "version": 1,
 *   "text": "Hello bold world",
 *   "spans": [
 *     { "start": 6, "end": 10, "style": { "type": "bold" } }
 *   ]
 * }
 * ```
 *
 * Normalization on decode:
 * - Span coordinates are clamped to `[0, text.length]`.
 * - Empty spans (after clamping) are dropped.
 * - Unknown style types are dropped gracefully.
 * - Missing required fields cause the individual span to be dropped, not the whole decode.
 *
 * Custom payload canonicalization:
 * - On encode, `SpanStyle.Custom.payload` (`String?`) is parsed and embedded as structured JSON.
 * - On decode, the structured JSON element is serialized back to a canonical string.
 * - This normalizes formatting differences (e.g. `{"a":1}` vs `{ "a" : 1 }`).
 */
public object RichTextSchema {

    public const val CURRENT_VERSION: Int = 1

    /**
     * Encodes [BlockContent.Text] to a [JsonObject].
     */
    public fun encode(content: BlockContent.Text): JsonObject = buildJsonObject {
        put("version", JsonPrimitive(CURRENT_VERSION))
        put("text", JsonPrimitive(content.text))
        put("spans", buildJsonArray {
            for (span in content.spans) {
                add(encodeSpan(span))
            }
        })
    }

    /**
     * Encodes [BlockContent.Text] to a JSON string.
     */
    public fun encodeToString(content: BlockContent.Text): String =
        encode(content).toString()

    /**
     * Decodes a [JsonObject] to [BlockContent.Text].
     * Normalizes invalid/out-of-bounds spans.
     *
     * @throws IllegalArgumentException if the schema version is unsupported (higher than [CURRENT_VERSION])
     */
    public fun decode(json: JsonObject): BlockContent.Text {
        val version = json["version"]?.jsonPrimitive?.intOrNull ?: 1
        return when {
            version <= CURRENT_VERSION -> decodeV1(json)
            else -> throw IllegalArgumentException(
                "Unsupported schema version $version (max supported: $CURRENT_VERSION)"
            )
        }
    }

    /**
     * Decodes a JSON string to [BlockContent.Text].
     *
     * @throws IllegalArgumentException if the schema version is unsupported
     */
    public fun decodeFromString(jsonString: String): BlockContent.Text =
        decode(Json.parseToJsonElement(jsonString).jsonObject)

    // -- Private encode helpers --

    private fun encodeSpan(span: TextSpan): JsonObject = buildJsonObject {
        put("start", JsonPrimitive(span.start))
        put("end", JsonPrimitive(span.end))
        put("style", encodeStyle(span.style))
    }

    private fun encodeStyle(style: SpanStyle): JsonObject = buildJsonObject {
        when (style) {
            is SpanStyle.Bold -> put("type", JsonPrimitive("bold"))
            is SpanStyle.Italic -> put("type", JsonPrimitive("italic"))
            is SpanStyle.Underline -> put("type", JsonPrimitive("underline"))
            is SpanStyle.StrikeThrough -> put("type", JsonPrimitive("strikethrough"))
            is SpanStyle.InlineCode -> put("type", JsonPrimitive("inline_code"))
            is SpanStyle.Highlight -> {
                put("type", JsonPrimitive("highlight"))
                put("colorArgb", JsonPrimitive(style.colorArgb))
            }
            is SpanStyle.Link -> {
                put("type", JsonPrimitive("link"))
                put("url", JsonPrimitive(style.url))
            }
            is SpanStyle.Custom -> {
                put("type", JsonPrimitive("custom"))
                put("typeId", JsonPrimitive(style.typeId))
                if (style.payload != null) {
                    val element = try {
                        Json.parseToJsonElement(style.payload)
                    } catch (_: Exception) {
                        // Malformed JSON — store as plain string primitive
                        JsonPrimitive(style.payload)
                    }
                    put("payload", element)
                }
            }
        }
    }

    // -- Private decode helpers --

    private fun decodeV1(json: JsonObject): BlockContent.Text {
        val text = json["text"]?.jsonPrimitive?.content ?: ""
        val spansArray = json["spans"]?.jsonArray ?: JsonArray(emptyList())

        val spans = spansArray.mapNotNull { element ->
            decodeSpan(element.jsonObject, text.length)
        }

        return BlockContent.Text(text = text, spans = spans)
    }

    private fun decodeSpan(json: JsonObject, textLength: Int): TextSpan? {
        val rawStart = json["start"]?.jsonPrimitive?.intOrNull ?: return null
        val rawEnd = json["end"]?.jsonPrimitive?.intOrNull ?: return null
        val styleJson = json["style"]?.jsonObject ?: return null
        val style = decodeStyle(styleJson) ?: return null

        // Normalize: clamp to valid range
        val start = rawStart.coerceIn(0, textLength)
        val end = rawEnd.coerceIn(start, textLength)

        // Drop empty spans after clamping
        if (start == end) return null

        return TextSpan(start = start, end = end, style = style)
    }

    private fun decodeStyle(json: JsonObject): SpanStyle? {
        return when (json["type"]?.jsonPrimitive?.content) {
            "bold" -> SpanStyle.Bold
            "italic" -> SpanStyle.Italic
            "underline" -> SpanStyle.Underline
            "strikethrough" -> SpanStyle.StrikeThrough
            "inline_code" -> SpanStyle.InlineCode
            "highlight" -> {
                val colorArgb = json["colorArgb"]?.jsonPrimitive?.longOrNull ?: return null
                SpanStyle.Highlight(colorArgb)
            }
            "link" -> {
                val url = json["url"]?.jsonPrimitive?.content ?: return null
                SpanStyle.Link(url)
            }
            "custom" -> {
                val typeId = json["typeId"]?.jsonPrimitive?.content ?: return null
                val payload = json["payload"]?.let { element ->
                    // Canonicalize: JsonElement → canonical String
                    element.toString()
                }
                SpanStyle.Custom(typeId = typeId, payload = payload)
            }
            else -> null // Unknown style type — drop gracefully
        }
    }
}
