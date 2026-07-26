package io.github.linreal.cascade.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.registry.BlockPreviewRenderer
import io.github.linreal.cascade.editor.registry.BlockPreviewScope
import io.github.linreal.cascade.editor.registry.BlockRegistry
import io.github.linreal.cascade.editor.theme.CascadeEditorBlockStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorStrings
import io.github.linreal.cascade.editor.theme.CascadeEditorTheme
import io.github.linreal.cascade.editor.ui.CascadeDocumentPreview
import io.github.linreal.cascade.editor.ui.CascadeDocumentPreviewConfig
import io.github.linreal.cascade.editor.ui.ExperimentalCascadePreviewApi
import io.github.linreal.cascade.editor.ui.createEditorRegistry
import io.github.linreal.cascade.screens.preview.PreviewDocument
import io.github.linreal.cascade.screens.preview.PreviewDocumentLibrary
import io.github.linreal.cascade.screens.preview.PreviewMetricBlockType
import io.github.linreal.cascade.theme.SampleEditorTheme
import io.github.linreal.cascade.ui.PageScaffold

/**
 * Representative note grid demonstrating the recommended preview integration:
 * documents and registry are hoisted, item keys are stable, only the outer grid
 * owns vertical scrolling, and a card opens the same document in a full editor.
 *
 * The grid never creates editor state. Tapping a card navigates by document ID;
 * the destination loads that document's JSON and publishes its export back
 * through [PreviewDocumentLibrary], which refreshes this card in place.
 */
@OptIn(ExperimentalCascadePreviewApi::class)
@Composable
fun PreviewGalleryScreen(
    library: PreviewDocumentLibrary,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit,
) {
    // SampleEditorTheme returns a fresh but structurally equal instance per call,
    // so this is already stable enough for the per-card `remember` keys inside the
    // preview to survive recomposition.
    val editorTheme = if (isDark) SampleEditorTheme.dark() else SampleEditorTheme.light()
    val registry = remember {
        createEditorRegistry().apply {
            registerPreviewRenderer(PreviewMetricBlockType.typeId, PreviewMetricRenderer)
        }
    }
    val strings = remember { CascadeEditorStrings.default() }
    val blockStrings = remember { CascadeEditorBlockStrings.default() }
    val previewConfig = remember {
        // The card owns the tap, so link activation is disabled rather than
        // arbitrated against navigation. Link spans still render styled.
        CascadeDocumentPreviewConfig.GridCard.copy(
            textScale = 0.8f,
            linksEnabled = false,
        )
    }
    val documents = library.documents

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
                text = "${documents.size} documents · tap a card to edit it",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (library.lastErrorMessage.isNotEmpty()) {
                Text(
                    text = library.lastErrorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 260.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = documents,
                key = { document -> document.id },
            ) { document ->
                PreviewDocumentCard(
                    document = document,
                    registry = registry,
                    editorTheme = editorTheme,
                    strings = strings,
                    blockStrings = blockStrings,
                    config = previewConfig,
                    onOpen = { onOpenDocument(document.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalCascadePreviewApi::class)
@Composable
private fun PreviewDocumentCard(
    document: PreviewDocument,
    registry: BlockRegistry,
    editorTheme: CascadeEditorTheme,
    strings: CascadeEditorStrings,
    blockStrings: CascadeEditorBlockStrings,
    config: CascadeDocumentPreviewConfig,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = document.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        CascadeDocumentPreview(
            blocks = document.blocks,
            registry = registry,
            theme = editorTheme,
            strings = strings,
            blockStrings = blockStrings,
            config = config,
            onOpenLink = null,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
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
