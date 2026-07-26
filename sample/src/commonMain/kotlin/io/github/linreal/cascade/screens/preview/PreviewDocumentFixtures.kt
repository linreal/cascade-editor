package io.github.linreal.cascade.screens.preview

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.core.UnknownBlockType

/**
 * Seed fixtures for the preview gallery, cycling through the cases a preview grid
 * has to survive: plain text, span-heavy rich text, nested outlines, todos,
 * quote/code chrome, and custom plus unknown blocks.
 */
internal fun seedBlocks(documentId: String, index: Int): List<Block> = when (index % 6) {
    0 -> simplePreviewBlocks(documentId)
    1 -> spanHeavyPreviewBlocks(documentId)
    2 -> structuralPreviewBlocks(documentId)
    3 -> todoPreviewBlocks(documentId)
    4 -> quoteCodePreviewBlocks(documentId)
    else -> customPreviewBlocks(documentId)
}

internal fun seedTitle(index: Int): String = when (index % 6) {
    0 -> "Preview mode"
    1 -> "Rich text parity"
    2 -> "Outline"
    3 -> "Release checklist"
    4 -> "Shared primitives"
    else -> "Custom + unknown"
} + " #${index + 1}"

private fun simplePreviewBlocks(documentId: String): List<Block> = listOf(
    previewBlock(documentId, 0, BlockType.Heading(2), BlockContent.Text("Preview mode")),
    previewBlock(
        documentId,
        1,
        BlockType.Paragraph,
        BlockContent.Text(
            "Static document cards avoid editor state, focus, history, and nested scrolling.",
        ),
    ),
    previewBlock(
        documentId,
        2,
        BlockType.Todo(checked = true),
        BlockContent.Text("Hoist registry"),
    ),
    previewBlock(documentId, 3, BlockType.Divider, BlockContent.Empty),
)

private fun spanHeavyPreviewBlocks(documentId: String): List<Block> {
    val text = "Bold italic underline strike code mark link"
    fun span(word: String, style: SpanStyle): TextSpan {
        val start = text.indexOf(word)
        return TextSpan(start, start + word.length, style)
    }
    return listOf(
        previewBlock(documentId, 0, BlockType.Heading(3), BlockContent.Text("Rich text parity")),
        previewBlock(
            documentId,
            1,
            BlockType.Paragraph,
            BlockContent.Text(
                text = text,
                spans = listOf(
                    span("Bold", SpanStyle.Bold),
                    span("italic", SpanStyle.Italic),
                    span("underline", SpanStyle.Underline),
                    span("strike", SpanStyle.StrikeThrough),
                    span("code", SpanStyle.InlineCode),
                    span("mark", SpanStyle.Highlight(0xFFFFFF00L)),
                    span("link", SpanStyle.Link("https://github.com/linreal/cascade-editor")),
                ),
            ),
        ),
        previewBlock(
            documentId,
            2,
            BlockType.Paragraph,
            BlockContent.Text("Only the link activates; the rest of the card remains host-owned."),
        ),
    )
}

private fun structuralPreviewBlocks(documentId: String): List<Block> = listOf(
    previewBlock(documentId, 0, BlockType.Heading(3), BlockContent.Text("Outline")),
    previewBlock(documentId, 1, BlockType.NumberedList(1), BlockContent.Text("Parent item")),
    previewBlock(
        documentId,
        2,
        BlockType.NumberedList(1),
        BlockContent.Text("Nested item"),
        indentationLevel = 1,
    ),
    previewBlock(
        documentId,
        3,
        BlockType.BulletList,
        BlockContent.Text("Nested bullet"),
        indentationLevel = 2,
    ),
)

private fun todoPreviewBlocks(documentId: String): List<Block> = listOf(
    previewBlock(documentId, 0, BlockType.Heading(3), BlockContent.Text("Release checklist")),
    previewBlock(documentId, 1, BlockType.Todo(true), BlockContent.Text("Static checkbox")),
    previewBlock(documentId, 2, BlockType.Todo(false), BlockContent.Text("Cross-platform QA")),
    previewBlock(
        documentId,
        3,
        BlockType.Todo(false),
        BlockContent.Text("Nested follow-up"),
        indentationLevel = 1,
    ),
)

private fun quoteCodePreviewBlocks(documentId: String): List<Block> = listOf(
    previewBlock(
        documentId,
        0,
        BlockType.Quote,
        BlockContent.Text("A preview shares visual primitives, not editor runtime."),
    ),
    previewBlock(
        documentId,
        1,
        BlockType.Code,
        BlockContent.Text("val blocks = loadDocument()\nrenderPreview(blocks)"),
    ),
    previewBlock(documentId, 2, BlockType.Divider, BlockContent.Empty),
    previewBlock(documentId, 3, BlockType.Paragraph, BlockContent.Text("No internal scroll state.")),
)

private fun customPreviewBlocks(documentId: String): List<Block> = listOf(
    previewBlock(
        documentId,
        0,
        PreviewMetricBlockType,
        BlockContent.Custom(
            typeId = PreviewMetricBlockType.typeId,
            data = mapOf("value" to "Shared", "label" to "Custom preview renderer"),
        ),
    ),
    previewBlock(
        documentId,
        1,
        UnknownBlockType(
            typeId = "future.weather",
            rawTypeJson = """{"typeId":"future.weather"}""",
        ),
        BlockContent.Text("Unknown text remains readable through the bounded fallback."),
    ),
    previewBlock(
        documentId,
        2,
        UnknownBlockType(
            typeId = "future.media",
            rawTypeJson = """{"typeId":"future.media"}""",
        ),
        BlockContent.Custom("future.media", mapOf("asset" to "opaque")),
    ),
)

private fun previewBlock(
    documentId: String,
    index: Int,
    type: BlockType,
    content: BlockContent,
    indentationLevel: Int = 0,
): Block = Block(
    id = BlockId("$documentId-block-$index"),
    type = type,
    content = content,
    attributes = BlockAttributes(indentationLevel),
)
