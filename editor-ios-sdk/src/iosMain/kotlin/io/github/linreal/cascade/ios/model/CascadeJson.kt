package io.github.linreal.cascade.ios.model

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
import kotlinx.serialization.json.longOrNull

internal fun parseJsonObjectPayload(payloadJson: String): Map<String, Any?> {
    val element = Json.parseToJsonElement(payloadJson)
    require(element is JsonObject) { "Custom block payload must be a JSON object" }
    return element.toCascadeMap()
}

internal data class CascadePayloadParseResult(
    val data: Map<String, Any?>,
    val errorMessage: String?,
)

internal fun parseJsonObjectPayloadSafely(payloadJson: String): CascadePayloadParseResult = try {
    val element = Json.parseToJsonElement(payloadJson)
    if (element is JsonObject) {
        CascadePayloadParseResult(data = element.toCascadeMap(), errorMessage = null)
    } else {
        CascadePayloadParseResult(
            data = emptyMap(),
            errorMessage = "Custom block payload must be a JSON object",
        )
    }
} catch (exception: Exception) {
    val reason = exception.message
        ?.lineSequence()
        ?.firstOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "parse failed"
    CascadePayloadParseResult(
        data = emptyMap(),
        errorMessage = "Invalid custom block payload JSON: $reason",
    )
}

internal fun JsonObject.toCascadeMap(): Map<String, Any?> =
    entries.associate { (key, value) -> key to value.toCascadeValue() }

private fun JsonElement.toCascadeValue(): Any? = when (this) {
    JsonNull -> null
    is JsonObject -> toCascadeMap()
    is JsonArray -> map { it.toCascadeValue() }
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        doubleOrNull != null -> doubleOrNull?.takeIf { it.isFinite() } ?: content
        else -> content
    }
}

/** Serializes a coerced payload map back to a JSON object string. */
internal fun Map<String, Any?>.toCascadeJsonString(): String = toCascadeJsonObject().toString()

private fun Map<String, Any?>.toCascadeJsonObject(): JsonObject = buildJsonObject {
    for ((key, value) in this@toCascadeJsonObject) {
        put(key, value.toCascadeJsonElement())
    }
}

private fun Any?.toCascadeJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Int -> JsonPrimitive(this)
    is Long -> JsonPrimitive(this)
    is Float -> JsonPrimitive(this)
    is Double -> JsonPrimitive(this.takeIf { it.isFinite() })
    is List<*> -> buildJsonArray {
        for (item in this@toCascadeJsonElement) add(item.toCascadeJsonElement())
    }
    is Map<*, *> -> buildJsonObject {
        for ((key, value) in this@toCascadeJsonElement) {
            if (key is String) put(key, value.toCascadeJsonElement())
        }
    }
    else -> JsonPrimitive(toString())
}
