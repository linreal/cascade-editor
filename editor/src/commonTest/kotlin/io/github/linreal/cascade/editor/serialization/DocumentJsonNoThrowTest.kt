package io.github.linreal.cascade.editor.serialization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentJsonNoThrowTest {

    @Test
    fun malformed_json_returns_parse_failed_warning_and_empty_blocks() {
        val result = DocumentSchema.decodeFromStringWithReport("{ this is not json")
        assertTrue(result.blocks.isEmpty())
        assertTrue(result.warnings.any { it is DocumentDecodeWarning.DocumentParseFailed })
    }

    @Test
    fun unsupported_version_is_contained_as_parse_failed() {
        val json = """{"version": 9999, "blocks": []}"""
        val result = DocumentSchema.decodeFromStringWithReport(json)
        assertTrue(result.blocks.isEmpty())
        assertTrue(result.warnings.any { it is DocumentDecodeWarning.DocumentParseFailed })
    }

    @Test
    fun decodeFromString_does_not_throw_on_malformed_input() {
        assertEquals(emptyList(), DocumentSchema.decodeFromString("nonsense"))
    }

    @Test
    fun valid_document_still_decodes() {
        val json = """{"version": 2, "blocks": [{"id":"b1","type":{"typeId":"paragraph"},"content":{"kind":"text","version":1,"text":"hi","spans":[]}}]}"""
        val result = DocumentSchema.decodeFromStringWithReport(json)
        assertEquals(1, result.blocks.size)
        assertTrue(result.warnings.none { it is DocumentDecodeWarning.DocumentParseFailed })
    }
}
