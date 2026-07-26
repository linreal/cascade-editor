package io.github.linreal.cascade.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockAttributes
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.BlockType
import io.github.linreal.cascade.editor.core.CustomBlockType
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan
import io.github.linreal.cascade.editor.core.UnknownBlockType
import io.github.linreal.cascade.editor.registry.BlockPreviewRenderer
import io.github.linreal.cascade.editor.registry.BlockPreviewScope
import io.github.linreal.cascade.editor.theme.CascadeEditorBlockStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorStrings
import io.github.linreal.cascade.editor.ui.CascadeDocumentPreview
import io.github.linreal.cascade.editor.ui.CascadeDocumentPreviewConfig
import io.github.linreal.cascade.editor.ui.ExperimentalCascadePreviewApi
import io.github.linreal.cascade.editor.ui.createEditorRegistry
import io.github.linreal.cascade.theme.SampleEditorTheme
import io.github.linreal.cascade.ui.PageScaffold

/**
 * Representative 50-note grid demonstrating the recommended preview
 * integration: documents and registry are hoisted, item keys are stable, and
 * only the outer grid owns vertical scrolling.
 */
@OptIn(ExperimentalCascadePreviewApi::class)
@Composable
fun PreviewGalleryScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
) {
    val resolvedEditorTheme =
        if (isDark) SampleEditorTheme.dark() else SampleEditorTheme.light()
    val editorTheme = remember(isDark, resolvedEditorTheme) { resolvedEditorTheme }
    val registry = remember {
        createEditorRegistry().apply {
            registerPreviewRenderer(PreviewMetricBlockType.typeId, PreviewMetricRenderer)
        }
    }
    val strings = remember { CascadeEditorStrings.default() }
    val blockStrings = remember { CascadeEditorBlockStrings.default() }
    val previewConfig = remember {
        CascadeDocumentPreviewConfig.GridCard.copy(textScale = 0.8f)
    }
    val notes = remember { buildPreviewNotes(count = 50) }
    var lastAction by remember { mutableStateOf("Tap a card or its link") }

    PageScaffold(maxContentWidth = 1080.dp) {
        TitledEditorTopBar(
            title = "Preview Gallery",
            isDark = isDark,
            onBack = onBack,
            onToggleTheme = onToggleTheme,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "50 immutable documents · outer grid scroll only",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = lastAction,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 260.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = notes,
                key = { note -> note.id },
            ) { note ->
                PreviewNoteCard(
                    note = note,
                    registry = registry,
                    editorTheme = editorTheme,
                    strings = strings,
                    blockStrings = blockStrings,
                    config = previewConfig,
                    onCardClick = { lastAction = "Card: ${note.id}" },
                    onOpenLink = { target -> lastAction = "Link: $target" },
                )
            }
        }
    }
}

@OptIn(ExperimentalCascadePreviewApi::class)
@Composable
private fun PreviewNoteCard(
    note: PreviewNote,
    registry: io.github.linreal.cascade.editor.registry.BlockRegistry,
    editorTheme: io.github.linreal.cascade.editor.theme.CascadeEditorTheme,
    strings: CascadeEditorStrings,
    blockStrings: CascadeEditorBlockStrings,
    config: CascadeDocumentPreviewConfig,
    onCardClick: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onCardClick)
            .padding(vertical = 10.dp),
    ) {
        CascadeDocumentPreview(
            blocks = note.blocks,
            registry = registry,
            theme = editorTheme,
            strings = strings,
            blockStrings = blockStrings,
            config = config,
            onOpenLink = onOpenLink,
        )
    }
}

private data class PreviewNote(
    val id: String,
    val blocks: List<Block>,
)

private data object PreviewMetricBlockType : CustomBlockType {
    override val typeId: String = "sample.preview_metric"
    override val displayName: String = "Preview metric"
}

@OptIn(ExperimentalCascadePreviewApi::class)
private data object PreviewMetricRenderer : BlockPreviewRenderer<PreviewMetricBlockType> {
    @Composable
    override fun RenderPreview(
        block: Block,
        modifier: Modifier,
        scope: BlockPreviewScope,
    ) {
        val custom = block.content as? BlockContent.Custom
        val value = custom?.data?.get("value") as? String ?: "—"
        val label = custom?.data?.get("label") as? String ?: "Metric"
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(10.dp),
                )
                .padding(12.dp),
        ) {
            BasicText(
                text = value,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            BasicText(
                text = label,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 12.sp,
                ),
            )
        }
    }
}

private fun buildPreviewNotes(count: Int): List<PreviewNote> {
    return List(count) { index ->
        val noteId = "preview-note-$index"
        PreviewNote(
            id = noteId,
            blocks = when (index % 6) {
                0 -> simplePreviewBlocks(noteId)
                1 -> spanHeavyPreviewBlocks(noteId)
                2 -> structuralPreviewBlocks(noteId)
                3 -> todoPreviewBlocks(noteId)
                4 -> quoteCodePreviewBlocks(noteId)
                else -> customPreviewBlocks(noteId)
            },
        )
    }
}

private fun simplePreviewBlocks(noteId: String): List<Block> = listOf(
    previewBlock(noteId, 0, BlockType.Heading(2), BlockContent.Text("Preview mode")),
    previewBlock(
        noteId,
        1,
        BlockType.Paragraph,
        BlockContent.Text(
            "Static document cards avoid editor state, focus, history, and nested scrolling.",
        ),
    ),
    previewBlock(noteId, 2, BlockType.Todo(checked = true), BlockContent.Text("Hoist registry")),
    previewBlock(noteId, 3, BlockType.Divider, BlockContent.Empty),
)

private fun spanHeavyPreviewBlocks(noteId: String): List<Block> {
    val text = "Bold italic underline strike code mark link"
    fun span(word: String, style: SpanStyle): TextSpan {
        val start = text.indexOf(word)
        return TextSpan(start, start + word.length, style)
    }
    return listOf(
        previewBlock(noteId, 0, BlockType.Heading(3), BlockContent.Text("Rich text parity")),
        previewBlock(
            noteId,
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
            noteId,
            2,
            BlockType.Paragraph,
            BlockContent.Text("Only the link activates; the rest of the card remains host-owned."),
        ),
    )
}

private fun structuralPreviewBlocks(noteId: String): List<Block> = listOf(
    previewBlock(noteId, 0, BlockType.Heading(3), BlockContent.Text("Outline")),
    previewBlock(noteId, 1, BlockType.NumberedList(1), BlockContent.Text("Parent item")),
    previewBlock(
        noteId,
        2,
        BlockType.NumberedList(1),
        BlockContent.Text("Nested item"),
        indentationLevel = 1,
    ),
    previewBlock(
        noteId,
        3,
        BlockType.BulletList,
        BlockContent.Text("Nested bullet"),
        indentationLevel = 2,
    ),
)

private fun todoPreviewBlocks(noteId: String): List<Block> = listOf(
    previewBlock(noteId, 0, BlockType.Heading(3), BlockContent.Text("Release checklist")),
    previewBlock(noteId, 1, BlockType.Todo(true), BlockContent.Text("Static checkbox")),
    previewBlock(noteId, 2, BlockType.Todo(false), BlockContent.Text("Cross-platform QA")),
    previewBlock(
        noteId,
        3,
        BlockType.Todo(false),
        BlockContent.Text("Nested follow-up"),
        indentationLevel = 1,
    ),
)

private fun quoteCodePreviewBlocks(noteId: String): List<Block> = listOf(
    previewBlock(
        noteId,
        0,
        BlockType.Quote,
        BlockContent.Text("A preview shares visual primitives, not editor runtime."),
    ),
    previewBlock(
        noteId,
        1,
        BlockType.Code,
        BlockContent.Text("val blocks = loadDocument()\nrenderPreview(blocks)"),
    ),
    previewBlock(noteId, 2, BlockType.Divider, BlockContent.Empty),
    previewBlock(noteId, 3, BlockType.Paragraph, BlockContent.Text("No internal scroll state.")),
)

private fun customPreviewBlocks(noteId: String): List<Block> = listOf(
    previewBlock(
        noteId,
        0,
        PreviewMetricBlockType,
        BlockContent.Custom(
            typeId = PreviewMetricBlockType.typeId,
            data = mapOf("value" to "Shared", "label" to "Custom preview renderer"),
        ),
    ),
    previewBlock(
        noteId,
        1,
        UnknownBlockType(
            typeId = "future.weather",
            rawTypeJson = """{"typeId":"future.weather"}""",
        ),
        BlockContent.Text("Unknown text remains readable through the bounded fallback."),
    ),
    previewBlock(
        noteId,
        2,
        UnknownBlockType(
            typeId = "future.media",
            rawTypeJson = """{"typeId":"future.media"}""",
        ),
        BlockContent.Custom("future.media", mapOf("asset" to "opaque")),
    ),
)

private fun previewBlock(
    noteId: String,
    index: Int,
    type: BlockType,
    content: BlockContent,
    indentationLevel: Int = 0,
): Block = Block(
    id = BlockId("$noteId-block-$index"),
    type = type,
    content = content,
    attributes = BlockAttributes(indentationLevel),
)
