package io.github.linreal.cascade.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.CustomBlockType
import io.github.linreal.cascade.editor.registry.BlockDescriptor
import io.github.linreal.cascade.editor.registry.BlockRenderer
import io.github.linreal.cascade.editor.registry.blockRenderer
import io.github.linreal.cascade.editor.slash.BuiltInBlockSlashBehavior
import io.github.linreal.cascade.editor.slash.BuiltInSlashCommandSpec
import io.github.linreal.cascade.editor.slash.SlashCommandAction
import io.github.linreal.cascade.editor.slash.SlashCommandId
import io.github.linreal.cascade.editor.slash.SlashCommandRegistry
import io.github.linreal.cascade.editor.slash.SlashCommandResult
import io.github.linreal.cascade.editor.state.BlockSpanStates
import io.github.linreal.cascade.editor.state.BlockTextStates
import io.github.linreal.cascade.editor.state.EditorState
import io.github.linreal.cascade.editor.state.rememberEditorState
import io.github.linreal.cascade.theme.SampleEditorTheme
import io.github.linreal.cascade.editor.ui.CascadeEditor
import io.github.linreal.cascade.editor.ui.CascadeEditorConfig
import io.github.linreal.cascade.editor.ui.createEditorRegistry
import io.github.linreal.cascade.screens.customblocks.createTableDescriptor
import io.github.linreal.cascade.screens.customblocks.createTableRenderer
import io.github.linreal.cascade.screens.customblocks.sampleTableBlock
import io.github.linreal.cascade.ui.PageScaffold
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

// Metric Card Block Type
data class MetricBlockType(val category: String = "default") : CustomBlockType {
    override val typeId: String = "metric"
    override val displayName: String = "Metric Card"
    override val supportsText: Boolean = false
    override val isConvertible: Boolean = false
}

private fun metricBlock(
    value: String,
    label: String,
    trend: String = "up",
    trendValue: String = "",
): Block = Block(
    id = BlockId.generate(),
    type = MetricBlockType(),
    content = BlockContent.Custom(
        typeId = "metric",
        data = mapOf(
            "value" to value,
            "label" to label,
            "trend" to trend,
            "trendValue" to trendValue,
        ),
    ),
)

private fun createMetricRenderer(): BlockRenderer<MetricBlockType> =
    blockRenderer { block, _, _, modifier, _ ->
        val custom = block.content as? BlockContent.Custom
        val value = (custom?.data?.get("value") as? String) ?: "0"
        val label = (custom?.data?.get("label") as? String) ?: ""
        val trend = (custom?.data?.get("trend") as? String) ?: "up"
        val trendValue = (custom?.data?.get("trendValue") as? String) ?: ""

        val trendColor = if (trend == "up") Color(0xFF34A853.toInt()) else Color(0xFFEA4335.toInt())
        val trendArrow = if (trend == "up") "\u2191" else "\u2193"

        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (trendValue.isNotEmpty()) {
                Text(
                    text = "$trendArrow $trendValue",
                    color = trendColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(trendColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }

private fun createMetricDescriptor(): BlockDescriptor = BlockDescriptor(
    typeId = "metric",
    displayName = "Metric Card",
    description = "Stat card with value, label, and trend indicator",
    keywords = listOf("metric", "stat", "number", "kpi", "card", "dashboard"),
    slash = BuiltInSlashCommandSpec(
        behavior = BuiltInBlockSlashBehavior.AlwaysInsert,
    ),
    factory = { id ->
        Block(
            id = id,
            type = MetricBlockType(),
            content = BlockContent.Custom(
                typeId = "metric",
                data = mapOf(
                    "value" to "1,234",
                    "label" to "Total Items",
                    "trend" to "up",
                    "trendValue" to "8.2%",
                ),
            ),
        )
    },
)

// Color Palette Block Type

data class PaletteBlockType(val paletteName: String = "default") : CustomBlockType {
    override val typeId: String = "palette"
    override val displayName: String = "Color Palette"
    override val supportsText: Boolean = false
    override val isConvertible: Boolean = false
}

private fun paletteBlock(
    name: String,
    colors: List<String>,
): Block = Block(
    id = BlockId.generate(),
    type = PaletteBlockType(name),
    content = BlockContent.Custom(
        typeId = "palette",
        data = mapOf(
            "name" to name,
            "colors" to colors.joinToString(","),
        ),
    ),
)

private fun createPaletteRenderer(): BlockRenderer<PaletteBlockType> =
    blockRenderer { block, _, _, modifier, _ ->
        val custom = block.content as? BlockContent.Custom
        val name = (custom?.data?.get("name") as? String) ?: "Palette"
        val colorsStr = (custom?.data?.get("colors") as? String) ?: ""
        val hexList = colorsStr.split(",").map { it.trim().removePrefix("#") }
        val colors = hexList.mapNotNull { hex ->
            runCatching { Color(("FF$hex").toLong(16).toInt()) }.getOrNull()
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text(
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                colors.forEachIndexed { index, color ->
                    val hex = hexList.getOrElse(index) { "" }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "#$hex",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }

private fun createPaletteDescriptor(): BlockDescriptor = BlockDescriptor(
    typeId = "palette",
    displayName = "Color Palette",
    description = "Color swatch palette with hex labels",
    keywords = listOf("palette", "color", "swatch", "colors", "theme", "design"),
    slash = BuiltInSlashCommandSpec(
        behavior = BuiltInBlockSlashBehavior.AlwaysInsert,
    ),
    factory = { id ->
        Block(
            id = id,
            type = PaletteBlockType("Custom Palette"),
            content = BlockContent.Custom(
                typeId = "palette",
                data = mapOf(
                    "name" to "Custom Palette",
                    "colors" to "1A73E8,34A853,FBBC04,EA4335",
                ),
            ),
        )
    },
)

// Custom Slash Commands

private fun createSlashRegistry(): SlashCommandRegistry = SlashCommandRegistry().apply {
    register(
        SlashCommandAction(
            id = SlashCommandId("custom.timestamp"),
            title = "Timestamp",
            description = "Insert current date and time",
            keywords = listOf("date", "time", "now", "timestamp"),
            onExecute = {
                val local = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                val formatted = local.toString().substringBefore('.').replace('T', ' ')
                editor.replaceQueryText(formatted)
                SlashCommandResult.Done
            },
        )
    )
    register(
        SlashCommandAction(
            id = SlashCommandId("custom.lorem"),
            title = "Lorem Ipsum",
            description = "Insert placeholder text",
            keywords = listOf("lorem", "ipsum", "placeholder", "dummy", "text"),
            onExecute = {
                editor.replaceQueryText(
                    "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
                            "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. " +
                            "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris."
                )
                SlashCommandResult.Done
            },
        )
    )
}

// Demo Document

private fun buildDemoBlocks(): List<Block> = listOf(
    Block.heading(1, "Custom Blocks & Commands"),
    Block.paragraph(
        "This demo shows how to extend CascadeEditor with custom block types and slash commands."
    ),
    Block.heading(2, "Interactive Tables"),
    Block.paragraph(
        "Table blocks are implemented by the sample app with public custom block APIs."
    ),
    sampleTableBlock(),
    Block.paragraph("Type /table to insert another table."),
    Block.divider(),
    Block.heading(2, "Metric Cards"),
    Block.paragraph(
        "Metric cards are non-editable custom blocks \u2014 perfect for dashboards and status displays:"
    ),
    metricBlock("2,847", "Downloads", "up", "12.5%"),
    metricBlock("99.9%", "Uptime", "up", "0.1%"),
    metricBlock("142ms", "Avg Latency", "down", "23%"),
    Block.paragraph("Type /metric to insert a new metric card."),
    Block.divider(),
    Block.heading(2, "Color Palettes"),
    Block.paragraph(
        "Color palette blocks showcase full Compose rendering \u2014 circles, hex labels, and layout:"
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

// Screen Composable

@Composable
fun CustomBlocksScreen(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
) {
    val editorTheme = if (isDark) SampleEditorTheme.dark() else SampleEditorTheme.light()

    val textStates = remember { BlockTextStates() }
    val spanStates = remember { BlockSpanStates() }
    val editorState = rememberEditorState(buildDemoBlocks())

    val registry = remember {
        createEditorRegistry().apply {
            register(createMetricDescriptor(), createMetricRenderer())
            register(createPaletteDescriptor(), createPaletteRenderer())
            register(createTableDescriptor(), createTableRenderer())
        }
    }

    val slashRegistry = remember { createSlashRegistry() }
    var isReadOnly by remember { mutableStateOf(false) }

    PageScaffold {
        EditorTopBar(
            isReadOnly = isReadOnly,
            isDark = isDark,
            canUndo = editorState.canUndo,
            canRedo = editorState.canRedo,
            onBack = onBack,
            onUndo = { editorState.undo() },
            onRedo = { editorState.redo() },
            onToggleReadOnly = { isReadOnly = !isReadOnly },
            onToggleTheme = onToggleTheme,
            onReset = { editorState.setState(EditorState.withBlocks(buildDemoBlocks())) },
        )

        CascadeEditor(
            stateHolder = editorState,
            textStates = textStates,
            spanStates = spanStates,
            registry = registry,
            slashRegistry = slashRegistry,
            theme = editorTheme,
            config = CascadeEditorConfig(readOnly = isReadOnly),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
