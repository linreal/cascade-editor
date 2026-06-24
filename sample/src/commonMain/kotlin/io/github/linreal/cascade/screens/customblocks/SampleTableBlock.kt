package io.github.linreal.cascade.screens.customblocks

import io.github.linreal.cascade.editor.core.Block
import io.github.linreal.cascade.editor.core.BlockContent
import io.github.linreal.cascade.editor.core.BlockId
import io.github.linreal.cascade.editor.core.CustomBlockType

internal data object TableBlockType : CustomBlockType {
    override val typeId: String = "table"
    override val displayName: String = "Table"
    override val supportsText: Boolean = false
    override val isConvertible: Boolean = false
}

internal data class SampleTableModel(
    val rows: List<List<String>>,
    val headerRow: Boolean,
) {
    val rowCount: Int get() = rows.size
    val columnCount: Int get() = rows.firstOrNull()?.size ?: 1

    fun valueAt(row: Int, column: Int): String {
        return rows.getOrNull(row)?.getOrNull(column).orEmpty()
    }

    fun editCell(row: Int, column: Int, value: String): SampleTableModel {
        if (row !in rows.indices) return this
        if (column !in 0 until columnCount) return this
        return copy(
            rows = rows.mapIndexed { rowIndex, cells ->
                if (rowIndex == row) {
                    cells.mapIndexed { columnIndex, cell ->
                        if (columnIndex == column) value else cell
                    }
                } else {
                    cells
                }
            }
        )
    }

    fun addRow(afterRow: Int = rows.lastIndex): SampleTableModel {
        val insertIndex = (afterRow + 1).coerceIn(0, rows.size)
        val nextRows = rows.toMutableList().apply {
            add(insertIndex, List(columnCount) { "" })
        }
        return copy(rows = nextRows)
    }

    fun deleteRow(row: Int): SampleTableModel {
        if (rows.size <= 1 || row !in rows.indices) return this
        return copy(rows = rows.filterIndexed { index, _ -> index != row })
    }

    fun addColumn(afterColumn: Int = columnCount - 1): SampleTableModel {
        val insertIndex = (afterColumn + 1).coerceIn(0, columnCount)
        return copy(
            rows = rows.map { cells ->
                cells.toMutableList().apply {
                    add(insertIndex, "")
                }
            }
        )
    }

    fun deleteColumn(column: Int): SampleTableModel {
        if (columnCount <= 1 || column !in 0 until columnCount) return this
        return copy(
            rows = rows.map { cells ->
                cells.filterIndexed { index, _ -> index != column }
            }
        )
    }

    fun withHeaderRow(enabled: Boolean): SampleTableModel {
        return copy(headerRow = enabled)
    }

    fun toContent(): BlockContent.Custom {
        return BlockContent.Custom(
            typeId = TABLE_TYPE_ID,
            data = mapOf(
                "rows" to rows,
                "headerRow" to headerRow,
            ),
        )
    }

    fun toBlock(id: BlockId = BlockId.generate()): Block {
        return Block(
            id = id,
            type = TableBlockType,
            content = toContent(),
        )
    }

    companion object {
        const val TABLE_TYPE_ID: String = "table"

        fun default(): SampleTableModel {
            return SampleTableModel(
                rows = listOf(
                    listOf("Name", "Role", "Status"),
                    listOf("Ada", "Engineer", "Active"),
                    listOf("Linus", "Maintainer", "Review"),
                ),
                headerRow = true,
            )
        }

        fun fromBlock(block: Block): SampleTableModel {
            return fromContent(block.content)
        }

        fun fromContent(content: BlockContent): SampleTableModel {
            val custom = content as? BlockContent.Custom
            if (custom?.typeId != TABLE_TYPE_ID) return default()
            val rawRows = custom.data["rows"] as? List<*>
            val parsedRows = rawRows.orEmpty().mapNotNull { row ->
                val rawCells = row as? List<*> ?: return@mapNotNull null
                rawCells.map { cell -> cell as? String ?: cell?.toString().orEmpty() }
            }
            val headerRow = custom.data["headerRow"] as? Boolean ?: false
            return normalize(parsedRows, headerRow)
        }

        private fun normalize(
            candidateRows: List<List<String>>,
            headerRow: Boolean,
        ): SampleTableModel {
            val nonEmptyRows = candidateRows.filter { it.isNotEmpty() }
            if (nonEmptyRows.isEmpty()) return default()
            val width = nonEmptyRows.maxOf { it.size }.coerceAtLeast(1)
            return SampleTableModel(
                rows = nonEmptyRows.map { cells ->
                    cells + List(width - cells.size) { "" }
                },
                headerRow = headerRow,
            )
        }
    }
}

internal fun sampleTableBlock(id: BlockId = BlockId.generate()): Block {
    return SampleTableModel.default().toBlock(id)
}
