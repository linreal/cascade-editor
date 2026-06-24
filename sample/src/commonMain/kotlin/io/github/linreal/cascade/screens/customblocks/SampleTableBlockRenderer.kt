package io.github.linreal.cascade.screens.customblocks

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cascadeeditor.sample.generated.resources.Res
import cascadeeditor.sample.generated.resources.ic_add
import cascadeeditor.sample.generated.resources.ic_close
import cascadeeditor.sample.generated.resources.ic_delete
import cascadeeditor.sample.generated.resources.ic_table
import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.registry.BlockCallbacks
import io.github.linreal.cascade.editor.registry.BlockDescriptor
import io.github.linreal.cascade.editor.registry.BlockRenderScope
import io.github.linreal.cascade.editor.registry.ScopedBlockRenderer
import io.github.linreal.cascade.editor.slash.BuiltInBlockSlashBehavior
import io.github.linreal.cascade.editor.slash.BuiltInSlashCommandSpec
import io.github.linreal.cascade.theme.CascadeSampleColors
import io.github.linreal.cascade.theme.LocalCascadeSampleColors
import org.jetbrains.compose.resources.painterResource

internal fun createTableDescriptor(): BlockDescriptor {
    return BlockDescriptor(
        typeId = SampleTableModel.TABLE_TYPE_ID,
        displayName = "Table",
        description = "Interactive table with editable cells",
        keywords = listOf("table", "grid", "rows", "columns", "cells"),
        slash = BuiltInSlashCommandSpec(
            behavior = BuiltInBlockSlashBehavior.AlwaysInsert,
        ),
        factory = { id -> sampleTableBlock(id) },
    )
}

private data class SampleTableCell(
    val row: Int,
    val column: Int,
)

private data class SampleTableShape(
    val rowCount: Int,
    val columnCount: Int,
)

private data class SampleTableDraft(
    val cell: SampleTableCell,
    val shape: SampleTableShape,
    val persistedValue: String,
    val value: String,
)

@Immutable
private data class SampleTableColors(
    val card: Color,
    val cardBorder: Color,
    val accentBorder: Color,
    val gridBorder: Color,
    val cellBorder: Color,
    val divider: Color,
    val cellBackground: Color,
    val headerBackground: Color,
    val trailingBackground: Color,
    val iconTileBackground: Color,
    val iconTint: Color,
    val title: Color,
    val headerText: Color,
    val bodyText: Color,
    val placeholder: Color,
    val mutedLabel: Color,
    val accent: Color,
    val pillBackground: Color,
    val switchTrackOff: Color,
    val rowDeleteIdle: Color,
    val danger: Color,
)

private fun CascadeSampleColors.tableColors(): SampleTableColors {
    return if (isDark) {
        SampleTableColors(
            card = Color(0xFF1B1430),
            cardBorder = Color(0x1FFFFFFF),
            accentBorder = Color(0xFFA78BFA),
            gridBorder = Color(0x1FFFFFFF),
            cellBorder = Color(0x14FFFFFF),
            divider = Color(0x14FFFFFF),
            cellBackground = Color(0xFF1B1430),
            headerBackground = Color(0x248B5CF6),
            trailingBackground = Color(0x0AFFFFFF),
            iconTileBackground = Color(0x2E8B5CF6),
            iconTint = Color(0xFFC4B5FD),
            title = Color(0xFFF4F1FB),
            headerText = Color(0xFFF4F1FB),
            bodyText = Color(0xFFD8D2E8),
            placeholder = Color(0xFF6F6690),
            mutedLabel = Color(0xFF9B93B8),
            accent = Color(0xFFA78BFA),
            pillBackground = Color(0x1F8B5CF6),
            switchTrackOff = Color(0x33FFFFFF),
            rowDeleteIdle = Color(0xFF6F6690),
            danger = Color(0xFFFF8A6B),
        )
    } else {
        SampleTableColors(
            card = Color(0xFFFFFFFF),
            cardBorder = Color(0xFFEFE9FB),
            accentBorder = Color(0xFF6C3DE8),
            gridBorder = Color(0xFFE9E1F8),
            cellBorder = Color(0xFFEDE6F8),
            divider = Color(0xFFF1ECFB),
            cellBackground = Color(0xFFFFFFFF),
            headerBackground = Color(0xFFF4EFFE),
            trailingBackground = Color(0xFFFBF9FF),
            iconTileBackground = Color(0xFFF0E9FE),
            iconTint = Color(0xFF6C3DE8),
            title = Color(0xFF1C1238),
            headerText = Color(0xFF1C1238),
            bodyText = Color(0xFF332A4D),
            placeholder = Color(0xFFBCB2D6),
            mutedLabel = Color(0xFF6B6580),
            accent = Color(0xFF6C3DE8),
            pillBackground = Color(0xFFF1ECFB),
            switchTrackOff = Color(0xFFE2D9F5),
            rowDeleteIdle = Color(0xFFCDBFF0),
            danger = Color(0xFFFF6B4A),
        )
    }
}

internal fun createTableRenderer(): ScopedBlockRenderer<TableBlockType> {
    return SampleTableRenderer()
}

private class SampleTableRenderer : ScopedBlockRenderer<TableBlockType> {
    override val handlesSelectionVisual: Boolean = true

    @Composable
    override fun Render(
        block: Block,
        isSelected: Boolean,
        isFocused: Boolean,
        modifier: Modifier,
        callbacks: BlockCallbacks,
        scope: BlockRenderScope,
    ) {
        val model = SampleTableModel.fromBlock(block)
        val canMutate = !scope.readOnly && scope.canUpdateBlock
        val shape = model.shape()
        val drafts = remember(block.id) { mutableStateMapOf<SampleTableCell, SampleTableDraft>() }
        val sampleColors = LocalCascadeSampleColors.current
        val colors = sampleColors.tableColors()
        val cardShape = RoundedCornerShape(22.dp)
        val highlighted = isFocused || isSelected
        val borderColor = if (highlighted) colors.accentBorder else colors.cardBorder
        val borderWidth = if (highlighted) 2.dp else 1.dp
        val elevation = if (sampleColors.isDark) 0.dp else 10.dp

        LaunchedEffect(shape) {
            drafts.clear()
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .shadow(elevation, cardShape)
                .background(colors.card, cardShape)
                .border(borderWidth, borderColor, cardShape),
        ) {
            SampleTableToolbar(
                model = model,
                colors = colors,
                enabled = canMutate,
                onHeaderRowChange = { enabled ->
                    updateTable(block.id, scope, drafts) { it.withHeaderRow(enabled) }
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.divider),
            )
            SampleTableGrid(
                block = block,
                model = model,
                drafts = drafts,
                colors = colors,
                canMutate = canMutate,
                callbacks = callbacks,
                scope = scope,
            )
            SampleTableFooter(
                colors = colors,
                enabled = canMutate,
                onAddRow = {
                    updateTable(block.id, scope, drafts) { it.addRow() }
                },
                onAddColumn = {
                    updateTable(block.id, scope, drafts) { it.addColumn() }
                },
            )
        }
    }
}

@Composable
private fun SampleTableToolbar(
    model: SampleTableModel,
    colors: SampleTableColors,
    enabled: Boolean,
    onHeaderRowChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(IconTileSize)
                .clip(RoundedCornerShape(9.dp))
                .background(colors.iconTileBackground),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_table),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.iconTint),
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Table",
            color = colors.title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics {
                contentDescription = "Toggle header row"
            },
        ) {
            Text(
                text = "Header row",
                color = colors.mutedLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = model.headerRow,
                onCheckedChange = onHeaderRowChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = colors.accent,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = colors.switchTrackOff,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun SampleTableGrid(
    block: Block,
    model: SampleTableModel,
    drafts: MutableMap<SampleTableCell, SampleTableDraft>,
    colors: SampleTableColors,
    canMutate: Boolean,
    callbacks: BlockCallbacks,
    scope: BlockRenderScope,
) {
    val gridShape = RoundedCornerShape(13.dp)
    val scrollState = rememberScrollState()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
    ) {
        val contentWidth = ColumnWidth * model.columnCount + TrailingColumnWidth
        val frameWidth = contentWidth.coerceAtMost(maxWidth)
        Column(
            modifier = Modifier
                .width(frameWidth)
                .clip(gridShape)
                .border(1.dp, colors.gridBorder, gridShape)
                .horizontalScroll(scrollState),
        ) {
            repeat(model.rowCount) { row ->
                val isHeaderRow = row == 0
                val isLastRow = row == model.rowCount - 1
                val isHeaderStyled = isHeaderRow && model.headerRow
                val background = if (isHeaderStyled) colors.headerBackground else colors.cellBackground
                val textColor = if (isHeaderStyled) colors.headerText else colors.bodyText
                val weight = if (isHeaderStyled) FontWeight.SemiBold else FontWeight.Normal
                val placeholder = if (isHeaderRow) "Field" else "—"

                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                ) {
                    repeat(model.columnCount) { column ->
                        val cell = SampleTableCell(row = row, column = column)
                        val persistedValue = model.valueAt(row, column)
                        val draft = drafts[cell]
                        val draftValue = draft?.value ?: persistedValue
                        val showColumnDelete = isHeaderRow && canMutate && model.columnCount > 1

                        Box(
                            modifier = Modifier
                                .width(ColumnWidth)
                                .heightIn(min = CellMinHeight)
                                .background(background)
                                .cellDividers(
                                    drawRight = true,
                                    drawBottom = !isLastRow,
                                    color = colors.cellBorder,
                                ),
                        ) {
                            SampleTableCellContent(
                                value = draftValue,
                                editable = canMutate,
                                textColor = textColor,
                                weight = weight,
                                placeholder = placeholder,
                                colors = colors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = 12.dp,
                                        top = 11.dp,
                                        bottom = 11.dp,
                                        end = if (showColumnDelete) 26.dp else 12.dp,
                                    ),
                                onValueChange = { value ->
                                    val currentDraft = drafts[cell]
                                    drafts[cell] = if (currentDraft != null) {
                                        currentDraft.copy(value = value)
                                    } else {
                                        SampleTableDraft(
                                            cell = cell,
                                            shape = model.shape(),
                                            persistedValue = persistedValue,
                                            value = value,
                                        )
                                    }
                                },
                                onFocus = {
                                    callbacks.onFocus(block.id)
                                    val currentModel = scope.getBlock(block.id)
                                        ?.let(SampleTableModel::fromBlock)
                                        ?: model
                                    val currentValue = currentModel.valueAt(cell.row, cell.column)
                                    drafts[cell] = SampleTableDraft(
                                        cell = cell,
                                        shape = currentModel.shape(),
                                        persistedValue = currentValue,
                                        value = currentValue,
                                    )
                                },
                                onBlur = {
                                    val latestDraft = drafts.remove(cell) ?: return@SampleTableCellContent
                                    commitCellValue(
                                        blockId = block.id,
                                        draft = latestDraft,
                                        scope = scope,
                                    )
                                },
                                onSubmit = {
                                    val latestDraft = drafts.remove(cell) ?: return@SampleTableCellContent
                                    commitCellValue(
                                        blockId = block.id,
                                        draft = latestDraft,
                                        scope = scope,
                                    )
                                },
                            )
                            if (showColumnDelete) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(5.dp)
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            updateTable(block.id, scope, drafts) { it.deleteColumn(column) }
                                        }
                                        .semantics {
                                            contentDescription = "Delete column ${column + 1}"
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        painter = painterResource(Res.drawable.ic_close),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(colors.rowDeleteIdle),
                                        modifier = Modifier.size(11.dp),
                                    )
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(TrailingColumnWidth)
                            .fillMaxHeight()
                            .background(colors.trailingBackground)
                            .cellDividers(
                                drawRight = false,
                                drawBottom = !isLastRow,
                                color = colors.cellBorder,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!isHeaderRow && canMutate) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .clickable {
                                        updateTable(block.id, scope, drafts) { it.deleteRow(row) }
                                    }
                                    .semantics {
                                        contentDescription = "Delete row $row"
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painter = painterResource(Res.drawable.ic_delete),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(colors.rowDeleteIdle),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleTableCellContent(
    value: String,
    editable: Boolean,
    textColor: Color,
    weight: FontWeight,
    placeholder: String,
    colors: SampleTableColors,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onFocus: () -> Unit,
    onBlur: () -> Unit,
    onSubmit: () -> Unit,
) {
    val textStyle = TextStyle(
        color = textColor,
        fontWeight = weight,
        fontSize = 13.5.sp,
    )

    if (!editable) {
        Text(
            text = value.ifEmpty { " " },
            modifier = modifier,
            color = textColor,
            fontWeight = weight,
            fontSize = 13.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    var hasFocus by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.accent),
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                    onSubmit()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged { focusState ->
                val wasFocused = hasFocus
                hasFocus = focusState.isFocused
                if (focusState.isFocused && !wasFocused) {
                    onFocus()
                }
                if (!focusState.isFocused && wasFocused) {
                    onBlur()
                }
            },
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = colors.placeholder,
                    fontWeight = weight,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            innerTextField()
        },
    )
}

private fun Modifier.cellDividers(
    drawRight: Boolean,
    drawBottom: Boolean,
    color: Color,
): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    if (drawRight) {
        drawLine(
            color = color,
            start = Offset(size.width - stroke / 2f, 0f),
            end = Offset(size.width - stroke / 2f, size.height),
            strokeWidth = stroke,
        )
    }
    if (drawBottom) {
        drawLine(
            color = color,
            start = Offset(0f, size.height - stroke / 2f),
            end = Offset(size.width, size.height - stroke / 2f),
            strokeWidth = stroke,
        )
    }
}

@Composable
private fun SampleTableFooter(
    colors: SampleTableColors,
    enabled: Boolean,
    onAddRow: () -> Unit,
    onAddColumn: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SampleTableAddPill(
            label = "Add row",
            colors = colors,
            enabled = enabled,
            onClick = onAddRow,
        )
        SampleTableAddPill(
            label = "Add column",
            colors = colors,
            enabled = enabled,
            onClick = onAddColumn,
        )
    }
}

@Composable
private fun SampleTableAddPill(
    label: String,
    colors: SampleTableColors,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .clip(RoundedCornerShape(50))
            .background(colors.pillBackground)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_add),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.accent),
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = label,
            color = colors.accent,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

private fun commitCellValue(
    blockId: BlockId,
    draft: SampleTableDraft,
    scope: BlockRenderScope,
) {
    scope.updateBlock(blockId) { current ->
        val currentModel = SampleTableModel.fromBlock(current)
        if (!draft.canCommitTo(currentModel)) return@updateBlock current
        current.withContent(
            currentModel
                .editCell(draft.cell.row, draft.cell.column, draft.value)
                .toContent()
        )
    }
}

private fun updateTable(
    blockId: BlockId,
    scope: BlockRenderScope,
    drafts: MutableMap<SampleTableCell, SampleTableDraft>,
    transform: (SampleTableModel) -> SampleTableModel,
) {
    val activeDrafts = drafts.values.toList()
    scope.updateBlock(blockId) { current ->
        val currentModel = SampleTableModel.fromBlock(current)
        val committedModel = activeDrafts.fold(currentModel) { model, draft ->
            if (draft.canCommitTo(model)) {
                model.editCell(draft.cell.row, draft.cell.column, draft.value)
            } else {
                model
            }
        }
        current.withContent(transform(committedModel).toContent())
    }
    drafts.clear()
}

private fun SampleTableModel.shape(): SampleTableShape {
    return SampleTableShape(
        rowCount = rowCount,
        columnCount = columnCount,
    )
}

private fun SampleTableDraft.canCommitTo(model: SampleTableModel): Boolean {
    return model.shape() == shape &&
        cell.row in 0 until model.rowCount &&
        cell.column in 0 until model.columnCount &&
        model.valueAt(cell.row, cell.column) == persistedValue
}

private val CellMinHeight = 44.dp
private val ColumnWidth = 132.dp
private val TrailingColumnWidth = 40.dp
private val IconTileSize = 30.dp
