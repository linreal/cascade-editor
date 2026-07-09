@file:OptIn(ExperimentalForeignApi::class)

package io.github.linreal.cascade.ios.fixtures

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.CustomBlockType
import io.github.linreal.cascade.editor.serialization.DocumentSchema
import io.github.linreal.cascade.ios.model.CascadeEditorDocumentBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.posix.getenv

/**
 * Golden parity between the committed canonical fixtures, the KMP sample's seed
 * documents, and the iOS-facing [CascadeEditorDocumentBuilder].
 *
 * Fixtures (committed under `src/commonTest/resources/`, directory provided by the
 * Gradle test task through the `CASCADE_FIXTURES_DIR` environment variable):
 * - `editor_demo_document.json` — the editor-demo seed; a canonical copy of the KMP
 *   sample's `default_document.json` with its authored block ids intentionally preserved.
 * - `comments_composer_document.json` — the comments composer seed (one empty paragraph).
 * - `custom_blocks_document.json` — the custom-blocks demo seed, mirroring the KMP
 *   sample's `CustomBlocksScreen.buildDemoBlocks()` data.
 *
 * ## Normalization rules
 *
 * "Semantically equivalent" is not byte equality. Both sides of every comparison are
 * projected through [normalizeDocument] before comparing:
 * - **Block ids** are dropped when `ignoreIds = true` (generated ids are volatile);
 *   comparisons against fixtures with intentionally authored ids keep them.
 * - **Type objects**: default-omitted params are materialized (`todo` without
 *   `checked` → `checked: false`; `numbered_list` without `number` → `number: 1`),
 *   and the serializer's `custom: true` marker flag is dropped — registered-codec
 *   output (`{"typeId": "table"}`) and the codec-less fallback
 *   (`{"typeId": "table", "custom": true}`) decode to the same block.
 * - **Attributes**: omitted entirely when `indentationLevel` is absent or `0`
 *   (the encoder's canonical default omission).
 * - **Text content**: `kind` and `text` are kept; the schema-internal `version`
 *   field is dropped (only v1 exists); `spans` are kept verbatim (order is canonical).
 * - **Custom content**: `kind` and `data` are compared exactly, including nested
 *   lists/maps — custom payload parity is the point of the custom-blocks fixture.
 *
 * The **builder projection** ([Projection.Builder]) additionally drops `spans` and
 * `attributes` from both sides: the document builder deliberately authors neither
 * inline formatting nor indentation this milestone, so builder comparisons cover
 * block sequence, types, type params, text, and custom payloads. Full-fidelity
 * span/indentation parity for the editor-demo document is guaranteed by the
 * fixture-vs-sample-file comparison and the decode/encode round-trip instead.
 */
class GoldenParityTest {

    private enum class Projection {
        /** Full semantic form: everything except volatile ids and marker/default noise. */
        Document,

        /** [Document] minus spans and indentation attributes — the builder-expressible subset. */
        Builder,
    }

    // Fixture access

    private fun environmentValue(name: String): String? = getenv(name)?.toKString()

    private fun readFile(directoryVariable: String, fileName: String): String {
        val directory = assertNotNull(
            environmentValue(directoryVariable),
            "Missing $directoryVariable environment variable; " +
                "expected the Gradle test task to pass it via SIMCTL_CHILD_$directoryVariable",
        )
        val content = NSString.stringWithContentsOfFile(
            "$directory/$fileName",
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        return assertNotNull(content, "Could not read $directory/$fileName")
    }

    private fun readFixture(fileName: String): String =
        readFile("CASCADE_FIXTURES_DIR", fileName)

    // Editor demo document

    @Test
    fun editorDemoFixtureMatchesKmpSampleDefaultDocument() {
        val fixture = readFixture("editor_demo_document.json")
        val sampleDocument = readFile("CASCADE_SAMPLE_FILES_DIR", "default_document.json")

        assertEquals(
            normalizeDocument(sampleDocument, ignoreIds = false),
            normalizeDocument(fixture, ignoreIds = false),
            "editor_demo_document.json drifted from the KMP sample's default_document.json",
        )
    }

    @Test
    fun editorDemoFixtureRoundTripsThroughDocumentSchema() {
        val fixture = readFixture("editor_demo_document.json")
        val reEncoded = DocumentSchema.encodeToString(DocumentSchema.decodeFromString(fixture))

        assertEquals(
            normalizeDocument(fixture, ignoreIds = false),
            normalizeDocument(reEncoded, ignoreIds = false),
            "editor_demo_document.json is not canonical DocumentSchema JSON",
        )
    }

    @Test
    fun editorDemoFixtureReconstructedByIosBuilder() {
        val builderJson = CascadeEditorDocumentBuilder()
            .heading(1, "Welcome to Cascade Editor")
            .paragraph(
                "A block-based document editor for Compose Multiplatform. " +
                    "Everything you see here is editable — try it out!"
            )
            .divider()
            .heading(2, "Try These")
            .todo("Mix bold, italic, underline, strikethrough, code, and highlights in any block.", checked = false)
            .todo("Tap here and start typing", checked = false)
            .todo("Select text, then use the toolbar to make it bold or italic", checked = false)
            .todo("Type / to open slash commands (or just use / icon from the toolbar)", checked = false)
            .todo("Use indentation to group related tasks", checked = false)
            .todo("Subtasks can sit under a parent todo", checked = false)
            .todo("Deeper todo levels stay attached to the outline", checked = false)
            .todo("Outdent again when the group is done", checked = false)
            .todo("Hold and drag any block to reorder it", checked = false)
            .todo("Long-press a block to select it, then tap others to multi-select and delete", checked = false)
            .todo("Press Enter at the end of this line to create a new block", checked = false)
            .heading(2, "Block Types")
            .bulletList("Bullet lists for unordered content")
            .bulletList("Nest ideas without numbering")
            .numberedList("Numbered lists auto-increment", number = 1)
            .numberedList("Indented numbered items keep their own sequence", number = 1)
            .numberedList("Deeper levels can explain a step in detail", number = 1)
            .numberedList("Sibling nested items continue at the same depth", number = 2)
            .numberedList("Delete or reorder and every level renumbers", number = 2)
            .quote("The best way to predict the future is to invent it. — Alan Kay")
            .code("fun greet(name: String) {\n    println(\"Hello, \$name!\")\n}")
            .divider()
            .paragraph("Your changes are saved automatically. Hit Reset in the toolbar to start fresh.")
            .buildJson()

        assertEquals(
            normalizeDocument(readFixture("editor_demo_document.json"), ignoreIds = true, Projection.Builder),
            normalizeDocument(builderJson, ignoreIds = true, Projection.Builder),
        )
    }

    // Comments composer document

    @Test
    fun commentsComposerFixtureMatchesKmpComposerSeed() {
        // CommentsScreenModel seeds/resets its composer with exactly this encoding.
        val kmpComposerSeed = DocumentSchema.encodeToString(listOf(Block.paragraph()))

        assertEquals(
            normalizeDocument(readFixture("comments_composer_document.json"), ignoreIds = true),
            normalizeDocument(kmpComposerSeed, ignoreIds = true),
        )
    }

    @Test
    fun commentsComposerFixtureReconstructedByIosBuilder() {
        val builderJson = CascadeEditorDocumentBuilder()
            .paragraph("")
            .buildJson()

        assertEquals(
            normalizeDocument(readFixture("comments_composer_document.json"), ignoreIds = true),
            normalizeDocument(builderJson, ignoreIds = true),
        )
    }

    // Custom blocks document

    @Test
    fun customBlocksFixtureReconstructedByIosBuilder() {
        val builderJson = CascadeEditorDocumentBuilder()
            .heading(1, "Custom Blocks & Commands")
            .paragraph("This demo shows how to extend CascadeEditor with custom block types and slash commands.")
            .heading(2, "Interactive Tables")
            .paragraph("Table blocks are implemented by the sample app with public custom block APIs.")
            .customBlock(
                "table",
                """
                {
                    "rows": [
                        ["Name", "Role", "Status"],
                        ["Ada", "Engineer", "Active"],
                        ["Linus", "Maintainer", "Review"]
                    ],
                    "headerRow": true
                }
                """.trimIndent(),
            )
            .paragraph("Type /table to insert another table.")
            .divider()
            .heading(2, "Metric Cards")
            .paragraph(
                "Metric cards are non-editable custom blocks — perfect for dashboards and status displays:"
            )
            .customBlock("metric", """{"value":"2,847","label":"Downloads","trend":"up","trendValue":"12.5%"}""")
            .customBlock("metric", """{"value":"99.9%","label":"Uptime","trend":"up","trendValue":"0.1%"}""")
            .customBlock("metric", """{"value":"142ms","label":"Avg Latency","trend":"down","trendValue":"23%"}""")
            .paragraph("Type /metric to insert a new metric card.")
            .divider()
            .heading(2, "Color Palettes")
            .paragraph(
                "Color palette blocks showcase full Compose rendering — circles, hex labels, and layout:"
            )
            .customBlock("palette", """{"name":"Ocean Breeze","colors":"0077B6,00B4D8,90E0EF,CAF0F8"}""")
            .customBlock("palette", """{"name":"Sunset Warmth","colors":"FF6B6B,FFA06B,FFD93D,6BCB77"}""")
            .paragraph("Type /palette to insert a new color palette.")
            .divider()
            .heading(2, "Custom Slash Commands")
            .paragraph("Type /timestamp to insert the current date and time.")
            .paragraph("Type /lorem to insert placeholder text.")
            .paragraph("These custom commands coexist with all built-in commands in the slash popup.")
            .divider()
            .heading(2, "Try It Out")
            .paragraph("Click on any empty paragraph and type / to see all available commands:")
            .paragraph("")
            .paragraph("")
            .buildJson()

        assertEquals(
            normalizeDocument(readFixture("custom_blocks_document.json"), ignoreIds = true),
            normalizeDocument(builderJson, ignoreIds = true),
        )
    }

    @Test
    fun customBlocksFixtureMatchesKmpSampleDemoBlocks() {
        val kmpDemoJson = DocumentSchema.encodeToString(buildKmpDemoBlocks())

        assertEquals(
            normalizeDocument(readFixture("custom_blocks_document.json"), ignoreIds = true),
            normalizeDocument(kmpDemoJson, ignoreIds = true),
        )
    }

    /**
     * Mirrors the KMP sample's demo document (`CustomBlocksScreen.buildDemoBlocks()`
     * plus `SampleTableModel.default()`) using the same `:editor` primitives. The
     * sample's builders are private Compose-importing screen code, so the data is
     * reproduced here verbatim; drift in the sample surfaces as a fixture mismatch.
     */
    private fun buildKmpDemoBlocks(): List<Block> = listOf(
        Block.heading(1, "Custom Blocks & Commands"),
        Block.paragraph(
            "This demo shows how to extend CascadeEditor with custom block types and slash commands."
        ),
        Block.heading(2, "Interactive Tables"),
        Block.paragraph(
            "Table blocks are implemented by the sample app with public custom block APIs."
        ),
        customDataBlock(
            typeId = "table",
            data = mapOf(
                "rows" to listOf(
                    listOf("Name", "Role", "Status"),
                    listOf("Ada", "Engineer", "Active"),
                    listOf("Linus", "Maintainer", "Review"),
                ),
                "headerRow" to true,
            ),
        ),
        Block.paragraph("Type /table to insert another table."),
        Block.divider(),
        Block.heading(2, "Metric Cards"),
        Block.paragraph(
            "Metric cards are non-editable custom blocks — perfect for dashboards and status displays:"
        ),
        metricBlock("2,847", "Downloads", "up", "12.5%"),
        metricBlock("99.9%", "Uptime", "up", "0.1%"),
        metricBlock("142ms", "Avg Latency", "down", "23%"),
        Block.paragraph("Type /metric to insert a new metric card."),
        Block.divider(),
        Block.heading(2, "Color Palettes"),
        Block.paragraph(
            "Color palette blocks showcase full Compose rendering — circles, hex labels, and layout:"
        ),
        paletteBlock("Ocean Breeze", listOf("0077B6", "00B4D8", "90E0EF", "CAF0F8")),
        paletteBlock("Sunset Warmth", listOf("FF6B6B", "FFA06B", "FFD93D", "6BCB77")),
        Block.paragraph("Type /palette to insert a new color palette."),
        Block.divider(),
        Block.heading(2, "Custom Slash Commands"),
        Block.paragraph("Type /timestamp to insert the current date and time."),
        Block.paragraph("Type /lorem to insert placeholder text."),
        Block.paragraph(
            "These custom commands coexist with all built-in commands in the slash popup."
        ),
        Block.divider(),
        Block.heading(2, "Try It Out"),
        Block.paragraph("Click on any empty paragraph and type / to see all available commands:"),
        Block.paragraph(""),
        Block.paragraph(""),
    )

    private fun metricBlock(value: String, label: String, trend: String, trendValue: String): Block =
        customDataBlock(
            typeId = "metric",
            data = mapOf(
                "value" to value,
                "label" to label,
                "trend" to trend,
                "trendValue" to trendValue,
            ),
        )

    private fun paletteBlock(name: String, colors: List<String>): Block =
        customDataBlock(
            typeId = "palette",
            data = mapOf(
                "name" to name,
                "colors" to colors.joinToString(","),
            ),
        )

    private fun customDataBlock(typeId: String, data: Map<String, Any?>): Block = Block(
        id = BlockId.generate(),
        type = KmpSampleCustomType(typeId),
        content = BlockContent.Custom(typeId = typeId, data = data),
    )

    private data class KmpSampleCustomType(override val typeId: String) : CustomBlockType {
        override val displayName: String = typeId
        override val supportsText: Boolean = false
        override val isConvertible: Boolean = false
    }

    // Normalization

    private fun normalizeDocument(
        jsonString: String,
        ignoreIds: Boolean,
        projection: Projection = Projection.Document,
    ): JsonObject {
        val root = Json.parseToJsonElement(jsonString).jsonObject
        return buildJsonObject {
            root["version"]?.let { put("version", it) }
            put(
                "blocks",
                buildJsonArray {
                    (root["blocks"] as? JsonArray)?.forEach { block ->
                        add(normalizeBlock(block.jsonObject, ignoreIds, projection))
                    }
                },
            )
        }
    }

    private fun normalizeBlock(
        block: JsonObject,
        ignoreIds: Boolean,
        projection: Projection,
    ): JsonObject = buildJsonObject {
        if (!ignoreIds) {
            block["id"]?.let { put("id", it) }
        }
        put("type", normalizeType(assertNotNull(block["type"], "Block without type: $block").jsonObject))
        if (projection == Projection.Document) {
            normalizeAttributes(block["attributes"])?.let { put("attributes", it) }
        }
        put("content", normalizeContent(block["content"] as? JsonObject, projection))
    }

    private fun normalizeType(type: JsonObject): JsonObject = buildJsonObject {
        for ((key, value) in type) {
            if (key == "custom") continue
            put(key, value)
        }
        when ((type["typeId"] as? JsonPrimitive)?.content) {
            "todo" -> if ("checked" !in type) put("checked", JsonPrimitive(false))
            "numbered_list" -> if ("number" !in type) put("number", JsonPrimitive(1))
        }
    }

    private fun normalizeAttributes(attributes: kotlinx.serialization.json.JsonElement?): JsonObject? {
        val json = attributes as? JsonObject ?: return null
        val indentationLevel = (json["indentationLevel"] as? JsonPrimitive)?.intOrNull ?: 0
        if (indentationLevel == 0) return null
        return buildJsonObject { put("indentationLevel", JsonPrimitive(indentationLevel)) }
    }

    private fun normalizeContent(content: JsonObject?, projection: Projection): JsonObject {
        val kind = (content?.get("kind") as? JsonPrimitive)?.content ?: "empty"
        return when (kind) {
            "empty" -> buildJsonObject { put("kind", JsonPrimitive("empty")) }
            "text" -> buildJsonObject {
                put("kind", JsonPrimitive("text"))
                put("text", content?.get("text") ?: JsonPrimitive(""))
                if (projection == Projection.Document) {
                    put("spans", content?.get("spans") ?: JsonArray(emptyList()))
                }
            }

            else -> buildJsonObject {
                put("kind", JsonPrimitive(kind))
                put("data", content?.get("data") ?: JsonObject(emptyMap()))
            }
        }
    }
}
