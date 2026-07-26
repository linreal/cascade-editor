package io.github.linreal.cascade.screens.preview

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.CustomBlockType
import io.github.linreal.cascade.editor.serialization.BlockTypeCodec
import io.github.linreal.cascade.editor.serialization.DocumentDecodeWarning
import io.github.linreal.cascade.editor.serialization.DocumentSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * One document held by [PreviewDocumentLibrary].
 *
 * [json] is the single persistence format shared by the preview cards and the
 * editor destination; [blocks] is the decoded snapshot the cards render, so item
 * composition never parses JSON.
 */
@Immutable
data class PreviewDocument(
    val id: String,
    val title: String,
    val json: String,
    val blocks: List<Block>,
)

/**
 * App-owned source of truth for the preview-grid integration example.
 *
 * Seed fixtures are encoded to JSON once at construction so that the grid and the
 * editor exchange exactly one format. [save] persists an editor export and
 * publishes the decoded snapshot only after the document decodes cleanly, which
 * keeps the last valid snapshot on screen when a write is malformed.
 *
 * Replacing a single list entry keeps every other [PreviewDocument] instance
 * identical, so a keyed grid recomposes one card instead of rebuilding the grid.
 */
@Stable
class PreviewDocumentLibrary(documentCount: Int = SeedDocumentCount) {

    var documents: List<PreviewDocument> by mutableStateOf(seedDocuments(documentCount))
        private set

    var lastErrorMessage: String by mutableStateOf("")
        private set

    fun document(id: String): PreviewDocument? = documents.firstOrNull { it.id == id }

    /**
     * Persists an editor export for [id] and republishes its decoded snapshot.
     *
     * Returns `false` when the export does not decode, leaving the previously
     * published document untouched.
     */
    fun save(id: String, json: String): Boolean {
        val index = documents.indexOfFirst { it.id == id }
        if (index < 0) {
            lastErrorMessage = "Document '$id' is no longer available."
            return false
        }

        val result = DocumentSchema.decodeFromStringWithReport(
            jsonString = json,
            typeCodec = PreviewSampleTypeCodec,
        )
        val parseFailed = result.warnings.any { warning ->
            warning is DocumentDecodeWarning.DocumentParseFailed
        }
        if (parseFailed) {
            lastErrorMessage = "Could not save ${documents[index].title}."
            return false
        }

        documents = documents.toMutableList().also { updated ->
            updated[index] = updated[index].copy(json = json, blocks = result.blocks)
        }
        lastErrorMessage = ""
        return true
    }

    fun clearError() {
        lastErrorMessage = ""
    }

    private fun seedDocuments(count: Int): List<PreviewDocument> {
        return List(count) { index ->
            val id = "preview-note-$index"
            val json = DocumentSchema.encodeToString(
                blocks = seedBlocks(id, index),
                typeCodec = PreviewSampleTypeCodec,
            )
            PreviewDocument(
                id = id,
                title = seedTitle(index),
                json = json,
                // Decode the seed rather than reusing the in-memory fixture, so the
                // first render already shows the same normalized outline and list
                // numbering a later save publishes. Otherwise a card can shift
                // subtly the first time its document is edited.
                blocks = DocumentSchema.decodeFromString(
                    jsonString = json,
                    typeCodec = PreviewSampleTypeCodec,
                ),
            )
        }
    }
}

/**
 * Sample block type behind the gallery's custom preview renderer.
 *
 * It lives beside the library because persistence, not rendering, is what makes a
 * custom type survive the grid → editor → grid round trip.
 */
data object PreviewMetricBlockType : CustomBlockType {
    override val typeId: String = "sample.preview_metric"
    override val displayName: String = "Preview metric"
}

/**
 * Keeps [PreviewMetricBlockType] a first-class type across encode/decode.
 *
 * Without a codec the sample's custom blocks would decode as `UnknownBlockType`
 * after the first save and silently fall back to the generic preview.
 */
object PreviewSampleTypeCodec : BlockTypeCodec {
    override fun encodeType(type: BlockType): JsonObject? {
        if (type != PreviewMetricBlockType) return null
        return buildJsonObject {
            put("typeId", JsonPrimitive(PreviewMetricBlockType.typeId))
        }
    }

    override fun decodeType(typeId: String, json: JsonObject): BlockType? {
        return PreviewMetricBlockType.takeIf { typeId == it.typeId }
    }
}

private const val SeedDocumentCount = 50
